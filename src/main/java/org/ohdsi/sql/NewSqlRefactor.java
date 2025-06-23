package org.ohdsi.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marc A Suchard
 * @author Benjamin Viernes
 * @author Bhavani Tirupati
 */

@SuppressWarnings("Duplicates")
public class NewSqlRefactor {
	
	private static final Logger logger = LoggerFactory.getLogger(NewSqlRefactor.class);

	private static final String NEW_LINE = System.lineSeparator();
	private static final boolean RENAME_TAG = true;
	private static final boolean USE_DEPTH = false;
	private static final boolean ADD_LINE_NUMBERS = true;
	
	private final String originalSql;
	private final SqlRefactorConfig config;
	private String refactoredSql;
	
	private final List<TempTable> uniqueTables = new ArrayList<>();
	
	public NewSqlRefactor(String sql, SqlRefactorConfig config) {
		this.originalSql = sql;
		this.config = config;
	}
	
	public String getRefactoredSql() {
		if (refactoredSql == null) {
			try {
				refactoredSql = processSql();
			} catch (Exception e) {
				logger.error("NewSqlRefactor::processSql error: " + e.getMessage());
				return originalSql;
			}
		}
		return refactoredSql;
	}

	@SuppressWarnings("unused")
	public static String translateToCustomVaSql(String sql) {
		return translateToCustomVaSql(sql, new RSqlRefactorConfig());
	}

	public static String translateToCustomVaSql(String sql, SqlRefactorConfig config) {
		NewSqlRefactor refactor = new NewSqlRefactor(sql, config);
		return refactor.getRefactoredSql();
	}
	
	private String processSql() {

		logger.info("NewSqlRefactor::processSql entering method.");
		
		StringBuilder sb = new StringBuilder();
		
		// Split into execution lines and process
		String[] lines = originalSql.split(";");
		for (int i = 0; i < lines.length; ++i) {
			String result = processLine(lines[i], i);
			sb.append(result);
		}

		// Delete temp tables at end
		for (TempTable table : uniqueTables) {
			sb.append(NEW_LINE);
			sb.append("TRUNCATE TABLE ").append(table.getName()).append(";").append(NEW_LINE)
				.append("DROP TABLE ").append(table.getName()).append(";").append(NEW_LINE);
		}

		return sb.toString();
	}
	
	private String processLine(String line, int number) {
		
		String lineNumber = "(" + (number + 1) + ")";
		
		line = stripWhiteSpaces(line) + ";";
		
		Result result = new Result(line, Status.INCOMPLETE);
		while (result.status == Status.INCOMPLETE) {
			result = iterate(result);
		}
		
		StringBuilder sb = new StringBuilder();
		
		if (result.header.length() > 0) {
			if (ADD_LINE_NUMBERS) {
				sb.append("-- temp-table start ").append(lineNumber).append(NEW_LINE);
			}
			sb.append(result.header).append(NEW_LINE);
			if (ADD_LINE_NUMBERS) {
				sb.append("-- temp-table end ").append(lineNumber).append(NEW_LINE);
			}
		}
		
		if (ADD_LINE_NUMBERS) {
			sb.append("-- execution start ").append(lineNumber).append(NEW_LINE);
		}
		sb.append(result.sql).append(NEW_LINE);
		if (ADD_LINE_NUMBERS) {
			sb.append("-- execution end ").append(lineNumber).append(NEW_LINE);
		}
		
		return sb.toString();
	}
	
	private String stripWhiteSpaces(String line) {
		line = line.replaceAll("^\\s+", "");
		line = line.replaceAll("\\s+$", "");
		return line;
	}
	
	enum Status {
		DONE,
		INCOMPLETE
	}
	
	private static class Result {
		final String sql;
		final Status status;
		final String header;
		
		Result(String sql, Status status) {
			this(sql, "", status);
		}
		
		Result(String sql, String header, Status status) {
			this.sql = sql;
			this.header = header;
			this.status = status;
		}
	}
	
	private Result iterate(Result result) {
		
		String sql = result.sql;
		StringBuilder header = new StringBuilder(result.header);
		
		List<LocationType> allMatches = new ArrayList<>();
		
		for (MatchCriteria criteria : createRefactorCriteria()) {
			
			allMatches.addAll(findMatches(sql, criteria, LocationType.Type.BEGIN));
			allMatches.addAll(findMatches(sql, criteria, LocationType.Type.END));

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
			return new Result(sql, header.toString(), Status.DONE);
		}

		locations.sort(Comparator.comparingInt(SqlLocation::getStart));
		addUniqueTables(locations);
		
		for (TempTable table : uniqueTables) {
			if (!table.isComplete()) {
				header.append(table.getNewQuery()).append(NEW_LINE);
				table.markComplete();
			}
		}

		StringBuilder newSql = new StringBuilder();
		replaceOriginalWithTempTables(sql, locations, newSql);
		
		return new Result(newSql.toString(), header.toString(), Status.INCOMPLETE);
	}

	private void replaceOriginalWithTempTables(String sql,
																						 List<SqlLocation> locations,	
																						 StringBuilder newSql) {
		int currentStart = 0;
		for (SqlLocation loc : locations) {
			String replaceQuery = "SELECT * FROM " + loc.getName();
			newSql.append(sql, currentStart, loc.getStart()).append(replaceQuery);
			currentStart = loc.getEnd();
		}

		newSql.append(sql.substring(currentStart));
	}
	
	private void addUniqueTables(List<SqlLocation> locations) {
		
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
	
	private static List<LocationType> findMatches(String sql, MatchCriteria criterion, LocationType.Type type) {
		String pattern = type.getPattern(criterion);
		List<LocationType> positions = new ArrayList<>();
		Matcher matcher = Pattern.compile(pattern).matcher(sql);
		while (matcher.find()) {
			positions.add(new LocationType(matcher.start(), criterion, type));
		}
		return positions;
	}
	
	private static List<MatchCriteria> createRefactorCriteria() {
		return Arrays.asList(
			new MatchCriteria("PrimaryEvents", "-- Begin Primary Events", "-- End Primary Events"),
			new MatchCriteria("CorrelatedCriteria", "-- Begin Correlated Criteria", "-- End Correlated Criteria"),
			new MatchCriteria("CriteriaGroup", "-- Begin Criteria Group", "-- End Criteria Group"),
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
		
		@SuppressWarnings("unused")
		public int getDepth() { return depth; }
	}

	public static void main(String[] args) {

		if (args.length != 2) {
			System.out.println("Please provide an input filename and output filename.");
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
		
		NewSqlRefactor refactor = new NewSqlRefactor(oldSql, config);
		String refactoredSql = refactor.getRefactoredSql();

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
			writer.write(refactoredSql);
		} catch (IOException e) {
			System.err.println("An error occurred while writing to the file: " + e.getMessage());
		}
	}
}
