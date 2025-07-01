package org.ohdsi.sql;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Marc A Suchard
 * @author Benjamin Viernes
 * @author Bhavani Tirupati
 */

public class SqlCteRefactor {
	private static final Logger logger = LoggerFactory.getLogger(SqlCteRefactor.class);

	private static final String NEW_LINE = System.lineSeparator();
	private static final String POST_APPEND = NEW_LINE + "-- Refactored by SqlCteRefactor" + NEW_LINE;
//	private static final boolean ADD_INDICES_DOMAIN = true;
//	private static final boolean ADD_INDICES_CRITERIA = false;
	private static final boolean RENAME_TAG = true;
	private static final boolean USE_DEPTH = false;
	
	public static String runNewCode(String sql, SqlRefactorConfig config) {

		logger.info("SqlCteRefactor::runNewCode entering method.");
		
		List<TempTable> uniqueTables = new ArrayList<>();
		
		Result result = new Result(sql, Status.INCOMPLETE);
		while (result.status == Status.INCOMPLETE) {
			result = iterate(result.sql, uniqueTables, Portion.TOP, config);
		}

		result = new Result(result.sql, Status.INCOMPLETE);
		while (result.status == Status.INCOMPLETE) {
			result = iterate(result.sql, uniqueTables, Portion.BOTTOM, config);
		}		
		
		return result.sql;
	}
	
	enum Status {
		DONE,
		INCOMPLETE
	}
	
	enum Portion {
		TOP,
		BOTTOM,
		ALL
	}
	
	private static class Result {
		final String sql;
		final Status status;
		
		Result(String sql, Status status) {
			this.sql = sql;
			this.status = status;
		}
	}
	
	private static Result iterate(String sql, List<TempTable> uniqueTables, 
																Portion portion, SqlRefactorConfig config) {
		
		List<MatchCriteria> criteriaList = createCorrelatedCriteria();
		List<LocationType> allMatches = new ArrayList<>();

		int lowerLimit = -1;
		int upperLimit = Integer.MAX_VALUE;
		String inclusionString = "-- Inclusion Rule Inserts";
		List<Integer> inclusionPoint = findMatches(sql, inclusionString);
		
		if (portion == Portion.TOP) {
			if (inclusionPoint.size() > 0) {
				upperLimit = inclusionPoint.get(0);
			}
		} else if (portion == Portion.BOTTOM) {
			if (inclusionPoint.size() > 0) {
				lowerLimit = inclusionPoint.get(0); // + inclusionString.length() + 1;
			}
		}
		
		for (MatchCriteria criteria : criteriaList) {
			
			allMatches.addAll(findMatches(sql, criteria, LocationType.Type.BEGIN, lowerLimit, upperLimit));
			allMatches.addAll(findMatches(sql, criteria, LocationType.Type.END, lowerLimit, upperLimit));

		}
		
		allMatches.sort(Comparator.comparingInt(LocationType::getLocation));
		
		List<SqlLocation> locations = new ArrayList<>();
		
		int depth = 0;
		
		for (int i = 0; i < allMatches.size() - 1; ++i) {
			LocationType current = allMatches.get(i);
		
			if (current.getType() == LocationType.Type.BEGIN) {
				++depth;
				
				// Pick only tips
				LocationType next = allMatches.get(i + 1);
				if (next.getType() == LocationType.Type.END) {
					int start = current.getLocation();
					int end = next.getLocation() + next.getCriterion().getEndingPattern().length();

					locations.add(new SqlLocation(current.getCriterion().getCriteriaName(),
						start, end,
						sql.substring(start, end), depth
					));
				}				
			} else { // is END
				--depth;
			}
		}
		
		if (locations.size() == 0) {
			return new Result(sql, Status.DONE);
		}

		locations.sort(Comparator.comparingInt(SqlLocation::getStart));

		addUniqueTables(uniqueTables, locations, config);
		
		// Add in new temporary tables
		int startRefactorPoint;
		if (portion == Portion.BOTTOM) {
			startRefactorPoint = inclusionPoint.get(0) - 1;
		} else {
			startRefactorPoint = sql.indexOf("UPDATE STATISTICS #Codesets;") - 2; // Find end of added tables
			if (startRefactorPoint == -3) {
				startRefactorPoint = sql.indexOf("with primary_events") - 2;
			}
		}

		String firstSqlSegment = sql.substring(0, startRefactorPoint);

		StringBuilder newSql = new StringBuilder(firstSqlSegment).append(NEW_LINE);
		
		for (TempTable table : uniqueTables) {
			if (!table.isComplete()) {
				newSql.append(NEW_LINE).append(table.getNewQuery());
			}
		}
		
		replaceOriginalWithTempTables(sql, locations, startRefactorPoint, newSql);

		// Delete tables at end
		for (TempTable table : uniqueTables) {
			if (!table.isComplete()) {
				newSql.append(NEW_LINE);
				newSql.append("TRUNCATE TABLE ").append(table.getName()).append(";").append(NEW_LINE)
					.append("DROP TABLE ").append(table.getName()).append(";").append(NEW_LINE);
			}
			table.markComplete();
		}
		
		return new Result(newSql + POST_APPEND, 
//			allMatches.size() <= 2 ? Status.DONE : Status.INCOMPLETE
			Status.INCOMPLETE
		);
	}

	private static void replaceOriginalWithTempTables(String sql, 
																										List<SqlLocation> locations, 
																										int firstConceptSetEnd, 
																										StringBuilder newSql) {
		newSql.append(NEW_LINE);
		int currentStart = firstConceptSetEnd;
		for (int i = 0; i < locations.size(); ++i) {
			SqlLocation loc = locations.get(i);
			String replaceQuery = "SELECT * FROM " + loc.getName();

			newSql.append(sql, currentStart, loc.getStart()).append(replaceQuery);

			if (i < locations.size() - 1) {
				newSql.append(NEW_LINE); // TODO VaTools output is inconsistent
			}

			currentStart = loc.getEnd();
		}

		newSql.append(sql.substring(currentStart));
	}

	public static String translateToCustomVaSql2(String sql) {
		return translateToCustomVaSql2(sql, new RSqlRefactorConfig());
	}

	public static String translateToCustomVaSql2(String sql, SqlRefactorConfig config) {
		sql = translateToCustomVaSql(sql, config);
		return runNewCode(sql, config);
	}

	public static String translateToCustomVaSql(String sql) {
		return translateToCustomVaSql(sql, new RSqlRefactorConfig());
	}
	
	public static String translateToCustomVaSql(String sql, SqlRefactorConfig config) {

		logger.info("SqlCteRefactor::translateToCustomVaSql entering method.");
		
		List<MatchCriteria> domainCriteriaList = createDomainCriteria();
		List<SqlLocation> locations = new ArrayList<>();

		// Identify the locations of domain criteria sub-queries in the SQL
		for (MatchCriteria criteria : domainCriteriaList) {

			List<Integer> startLocs = findMatches(sql, criteria.getBeginningPattern());
			List<Integer> endLocs = findMatches(sql, criteria.getEndingPattern());

			for (int i = 0; i < startLocs.size(); i++) {
				int start = startLocs.get(i);
				int end = endLocs.get(i) + criteria.getEndingPattern().length();
				locations.add(new SqlLocation(criteria.getCriteriaName(),
					start, end,
					sql.substring(start, end)
				));
			}
		}

		if (locations.isEmpty()) {
			return sql + POST_APPEND;
		}

		locations.sort(Comparator.comparingInt(SqlLocation::getStart));
		
		List<TempTable> uniqueTables = buildUniqueTables(locations, config);
		
		// Add in new temporary tables
		int firstConceptSetEnd = sql.indexOf(";", sql.indexOf(";") + 1) + 1; // Find 2nd ";"

		String firstSqlSegment = sql.substring(0, firstConceptSetEnd);

		StringBuilder newSql = new StringBuilder(firstSqlSegment).append(NEW_LINE);
		
		if (config.getAddIndicesToDomainCriteria()) {
			newSql.append("CREATE CLUSTERED COLUMNSTORE INDEX idx ON #Codesets;");
		}

		for (TempTable table : uniqueTables) {
			newSql.append(NEW_LINE).append(table.getNewQuery());
		}
		replaceOriginalWithTempTables(sql, locations, firstConceptSetEnd, newSql);

		// Delete tables at end
		newSql.append(" "); // TODO VaTools output is inconsistent

		newSql.append(NEW_LINE).append("-- DELETE TEMP TABLES");

		boolean first = true;
		for (TempTable table : uniqueTables) {
			newSql.append(NEW_LINE);

			if (first) {
				newSql.append(" "); // TODO VaTools output is inconsistent
				first = false;
			}

			newSql.append("TRUNCATE TABLE ").append(table.getName()).append(";").append(NEW_LINE)
				.append("DROP TABLE ").append(table.getName()).append(";").append(NEW_LINE);
		}
		
		return newSql + POST_APPEND;
	}
	
	private static void addUniqueTables(List<TempTable> uniqueTables, List<SqlLocation> locations,
																			SqlRefactorConfig config) {
		
		for (SqlLocation loc : locations) {
			TempTable table = find(uniqueTables, loc.getQuery());
			final String name;
			if (table == null) {
				String tableName = "#" + loc.getDomain() + "Crit" + uniqueTables.size();
				uniqueTables.add(new TempTable(tableName, loc.getQuery(), config.getAddIndicesToNestedCriteria()));
				name = tableName;
			} else {
				name = table.getName();
			}
			loc.setName(name);
		}
	}
	
	private static TempTable find(List<TempTable> tables, String sql) {
		for (TempTable table : tables) {
			if (sql.equalsIgnoreCase(table.getOriginalQuery())) {
				return table;
			}
		}
		return null;
	}

	private static List<TempTable> buildUniqueTables(List<SqlLocation> locations, SqlRefactorConfig config) {

		Map<String, String> mapQueryToPartialName = new HashMap<>();
		Map<String, Integer> mapPartialNameToCount = new HashMap<>();

		List<TempTable> uniqueTables = new ArrayList<>();

		// Generate temporary table names based on extracted queries

		for (SqlLocation loc : locations) {
			Matcher matcher = Pattern.compile("codeset_id =\\s*(\\d+)").matcher(loc.getQuery());
			String codeSetId = matcher.find() ? matcher.group(1) : "X";
			String tableSubName = "#" + loc.getDomain() + "Crit" + codeSetId;
			String name;
			if (!mapQueryToPartialName.containsKey(loc.getQuery())) {
				if (mapPartialNameToCount.containsKey(tableSubName)) {
					Integer count = mapPartialNameToCount.get(tableSubName);
					count++;
					mapPartialNameToCount.put(tableSubName, count); // replace value in hash-map (fix)
					name = tableSubName + "_" + count;
				} else {
					mapPartialNameToCount.put(tableSubName, 1);
					name = tableSubName + "_" + 1;
				}
				mapQueryToPartialName.put(loc.getQuery(), name); // remember to cache all unique queries (fix)
				uniqueTables.add(new TempTable(name, loc.getQuery(), config.getAddIndicesToNestedCriteria()));
			} else {
				name = mapQueryToPartialName.get(loc.getQuery());
			}
			loc.setName(name);
		}
		return uniqueTables;
	}
	
	private static class LocationType {
		
		enum Type {
			BEGIN {
				@Override
				String getPattern(MatchCriteria criterion) {
					return criterion.getBeginningPattern();
				}
			},
			END {
				@Override
				String getPattern(MatchCriteria criterion) {
					return criterion.getEndingPattern();
				}
			};
			
			abstract String getPattern(MatchCriteria criterion);
		}
		
		private final int location;
		private final MatchCriteria criterion;
		private final int depth;
		private final Type type;
		
		LocationType(int location, MatchCriteria criterion, Type type) {
			this(location, criterion, type, -1);
		}
		
		LocationType(int location, MatchCriteria criterion, Type type, int depth) {
			this.location = location;
			this.criterion = criterion;
			this.depth = depth;
			this.type = type;
		}
		
		public int getLocation() { return location; }
		
		public MatchCriteria getCriterion() { return criterion; }
		
		@SuppressWarnings("unused")
		public int getDepth() { return depth; }

		public Type getType() { return type; }
	}
	
	private static List<Integer> findMatches(String sql, String pattern) {
		List<Integer> positions = new ArrayList<>();
		Matcher matcher = Pattern.compile(pattern).matcher(sql);
		while (matcher.find()) {
			positions.add(matcher.start());
		}
		return positions;
	}
	
	private static List<LocationType> findMatches(String sql, MatchCriteria criterion, LocationType.Type type,
																								int lowerLimit,
																								int upperLimit) {
		String pattern = type.getPattern(criterion);
		List<LocationType> positions = new ArrayList<>();
		Matcher matcher = Pattern.compile(pattern).matcher(sql);
		while (matcher.find()) {
			int start = matcher.start();
			if (start > lowerLimit && start < upperLimit) {
				positions.add(new LocationType(matcher.start(), criterion, type));
			}
		}
		return positions;
	}
	
	private static List<MatchCriteria> createCorrelatedCriteria() {
		return Arrays.asList(
			new MatchCriteria("PrimaryEvents", "-- Begin Primary Events", "-- End Primary Events"),
			new MatchCriteria("CorrelatedCriteria", "-- Begin Correlated Criteria", "-- End Correlated Criteria"),
//			Begin Criteria Group
			new MatchCriteria("CriteriaGroup", "-- Begin Criteria Group", "-- End Criteria Group")
		);
	}
	
	private static List<MatchCriteria> createDomainCriteria() {
		return Arrays.asList(
			new MatchCriteria("Measurement", "-- Begin Measurement Criteria", "-- End Measurement Criteria"),
			new MatchCriteria("Condition", "-- Begin Condition Occurrence Criteria", "-- End Condition Occurrence Criteria"),
			new MatchCriteria("ConditionEra", "-- Begin Condition Era Criteria", "-- End Condition Era Criteria"),
			new MatchCriteria("Drug", "-- Begin Drug Exposure Criteria", "-- End Drug Exposure Criteria"),
			new MatchCriteria("DrugEra", "-- Begin Drug Era Criteria", "-- End Drug Era Criteria"),
			new MatchCriteria("Visit", "-- Begin Visit Occurrence Criteria", "-- End Visit Occurrence Criteria"),
			new MatchCriteria("Device", "-- Begin Device Exposure Criteria", "-- End Device Exposure Criteria"),
			new MatchCriteria("Observation", "-- Begin Observation Criteria", "-- End Observation Criteria"),
			new MatchCriteria("Procedure", "-- Begin Procedure Occurrence Criteria",  "-- End Procedure Occurrence Criteria"),
			new MatchCriteria("Specimen", "-- Begin Specimen Criteria", "-- End Specimen Criteria"),
			new MatchCriteria("Location", "-- Begin Location region Criteria", "-- End Location region Criteria"),
			new MatchCriteria("Demographics", "-- Begin Demographic Criteria", "-- End Demographic Criteria")
		);
	}
	
	private static class MatchCriteria {
		private final String name;
		private final String beginningPattern;
		private final String endingPattern;

		public MatchCriteria(String name, String beginningPattern, String endingPattern) {
			this.name = name;
			this.beginningPattern = beginningPattern;
			this.endingPattern = endingPattern;
		}

		public String getCriteriaName() { return name; }

		public String getBeginningPattern() {
			return beginningPattern;
		}

		public String getEndingPattern() {
			return endingPattern;
		}
	}

	private static class TempTable {
		private final String name;
		private final String originalQuery;
		private final boolean addIndices;
		private boolean complete;

		public TempTable(String name, String originalQuery, boolean addIndices) {
			this.name = name;
			this.originalQuery = originalQuery;
			this.complete = false;
			this.addIndices = addIndices;
		}
		
		public String getName() { return name; }
		
		public String getOriginalQuery() { return originalQuery; }

		public String getNewQuery() {
			int firstFrom = originalQuery.toLowerCase().indexOf("from");
			int endCrit = RENAME_TAG ? 
				originalQuery.indexOf("-- XEnd") :	// TODO update for depth
				originalQuery.indexOf("-- End"); // TODO why SQL with additional -- Begin / -- End tags breaks! fix.
			StringBuilder newSql = new StringBuilder(originalQuery.substring(0, firstFrom)).append("INTO ").append(name)
				.append(NEW_LINE)
				.append(originalQuery, firstFrom, endCrit)
				.append(";").append(NEW_LINE);
			if (addIndices) {
				newSql.append("CREATE CLUSTERED COLUMNSTORE INDEX idx ON ").append(name).append(";").append(NEW_LINE);
			}
			newSql.append(originalQuery.substring(endCrit));
			return newSql.toString();
		}
		
		public boolean isComplete() { return complete; }
		
		public void markComplete() { complete = true; }
	}

	private static class SqlLocation {
		private final String domain;
		private final int start;
		private final int end;
		private final String query;
		private final int depth;

		private String name;
		
		public SqlLocation(String domain, int start, int end, String query) {
			this(domain, start, end, query, -1);
		}
		
		public SqlLocation(String domain, int start, int end, String query, int depth) {
			this.domain = domain;
			this.start = start;
			this.end = end;
			this.depth = depth;
			
			if (RENAME_TAG) {
				String tag = "X";
				if (USE_DEPTH) {
					if (depth > 0) {
						StringBuilder sb = new StringBuilder(depth);
						for (int i = 0; i < depth; ++i) {
							sb.append("Y");
						}
						tag = sb.toString();
					}
				}
				query = query.replaceAll("Begin",   tag + "Begin");
				query = query.replaceAll("End",   tag + "End");
			}
			
			this.query = query;
		}

		@SuppressWarnings("unused")
		public String getDomain() {
			return domain;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}
		
		public void setName(String str) {
			name = str;
		}

		public String getName() {
			return name;
		}

		public String getQuery() {
			return query;
		}
		
		public int getDepth() { return depth; }
	}

	public static void main(String[] args) {

		if (args.length != 2 && args.length != 3) {
			System.out.println("Please provide an input filename and one or two output filenames.");
			return;
		}

		String inputFileName = args[0];
		String outputFileName = args[1];
		StringBuilder contentBuilder = new StringBuilder();

		try (BufferedReader reader = new BufferedReader(new FileReader(inputFileName))) {

			String line;
			while ((line = reader.readLine()) != null) {
				contentBuilder.append(line);
				contentBuilder.append(System.lineSeparator());
			}

		} catch (IOException e) {
			System.err.println("An error occurred while reading the file: " + e.getMessage());
			return;
		}
		
		SqlRefactorConfig config = new RSqlRefactorConfig();

		String oldSql = contentBuilder.toString();
		String newSql = translateToCustomVaSql(oldSql, config);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
			writer.write(newSql);
		} catch (IOException e) {
			System.err.println("An error occurred while writing to the file: " + e.getMessage());
		}
		
		if (args.length == 3) {
			String outputFileName2 = args[2];
			String newestSql = runNewCode(newSql, config);
			
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName2))) {
				writer.write(newestSql);
			} catch (IOException e) {
				System.err.println("An error occurred while writing to the file: " + e.getMessage());
			}
		}
	}
}
