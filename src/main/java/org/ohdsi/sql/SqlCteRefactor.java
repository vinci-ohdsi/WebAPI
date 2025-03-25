package org.ohdsi.sql;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marc A Suchard
 * @author Benjamin Viernes
 * @author Bhavani Tirupati
 */

public class SqlCteRefactor {

	private static final String NEW_LINE = "\n";
	private static final String POST_APPEND = NEW_LINE + "-- Refactored by SqlCteRefactor" + NEW_LINE;

	public static String translateToCustomVaSql(String sql) {

		List<DomainCriteria> domainCriteriaList = createDomainCriteria();
		List<SqlLocation> locations = new ArrayList<>();

		// Identify the locations of domain criteria sub-queries in the SQL
		for (DomainCriteria criteria : domainCriteriaList) {

			List<Integer> startLocs = findMatches(sql, criteria.getBeginningPattern());
			List<Integer> endLocs = findMatches(sql, criteria.getEndingPattern());

			for (int i = 0; i < startLocs.size(); i++) {
				int start = startLocs.get(i);
				int end = endLocs.get(i) + criteria.getEndingPattern().length();
				locations.add(new SqlLocation(criteria.getDomain(),
					start, end,
					sql.substring(start, end)
				));
			}
		}

		if (locations.isEmpty()) {
			return sql + POST_APPEND;
		}

		locations.sort(Comparator.comparingInt(SqlLocation::getStart));
		
		List<TempTable> uniqueTables = buildUniqueTables(locations);
		
		// Add in new temporary tables
		int firstConceptSetEnd = sql.indexOf(";", sql.indexOf(";") + 1) + 1; // Find 2nd ";"

		String firstSqlSegment = sql.substring(0, firstConceptSetEnd);

		StringBuilder newSql = new StringBuilder(firstSqlSegment)
			.append(NEW_LINE + " CREATE CLUSTERED COLUMNSTORE INDEX idx ON #Codesets;");

		for (TempTable table : uniqueTables) {
			newSql.append(NEW_LINE).append(table.getNewQuery());
		}
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

		// Delete tables at end
		newSql.append(" "); // TODO VaTools output is inconsistent

		newSql.append(NEW_LINE + "-- DELETE TEMP TABLES");

		boolean first = true;
		for (TempTable table : uniqueTables) {
			newSql.append(NEW_LINE);

			if (first) {
				newSql.append(" "); // TODO VaTools output is inconsistent
				first = false;
			}

			newSql.append("TRUNCATE TABLE ").append(table.getName()).append(";" + NEW_LINE)
				.append("DROP TABLE ").append(table.getName()).append(";" + NEW_LINE);
		}
		
		return newSql + POST_APPEND;
	}

	private static List<TempTable> buildUniqueTables(List<SqlLocation> locations) {

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
				uniqueTables.add(new TempTable(name, loc.getQuery()));
			} else {
				name = mapQueryToPartialName.get(loc.getQuery());
			}
			loc.setName(name);
		}
		return uniqueTables;
	}
	
	private static List<Integer> findMatches(String sql, String pattern) {
		List<Integer> positions = new ArrayList<>();
		Matcher matcher = Pattern.compile(pattern).matcher(sql);
		while (matcher.find()) {
			positions.add(matcher.start());
		}
		return positions;
	}

	private static List<DomainCriteria> createDomainCriteria() {
		return Arrays.asList(
			new DomainCriteria("Measurement", "-- Begin Measurement Criteria", "-- End Measurement Criteria"),
			new DomainCriteria("Condition", "-- Begin Condition Occurrence Criteria", "-- End Condition Occurrence Criteria"),
			new DomainCriteria("Drug", "-- Begin Drug Exposure Criteria", "-- End Drug Exposure Criteria"),
			new DomainCriteria("Visit", "-- Begin Visit Occurrence Criteria", "-- End Visit Occurrence Criteria"),
			new DomainCriteria("Device", "-- Begin Device Exposure Criteria", "-- End Device Exposure Criteria"),
			new DomainCriteria("Observation", "-- Begin Observation Criteria", "-- End Observation Criteria"),
			new DomainCriteria("Procedure", "-- Begin Procedure Occurrence Criteria", "-- End Procedure Occurrence Criteria")
		);
	}

	private static class DomainCriteria {
		private final String domain;
		private final String beginningPattern;
		private final String endingPattern;

		public DomainCriteria(String domain, String beginningPattern, String endingPattern) {
			this.domain = domain;
			this.beginningPattern = beginningPattern;
			this.endingPattern = endingPattern;
		}

		public String getDomain() {
			return domain;
		}

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

		public TempTable(String name, String originalQuery) {
			this.name = name;
			this.originalQuery = originalQuery;
		}
		
		public String getName() { return name; }

		public String getNewQuery() {
			int firstFrom = originalQuery.toLowerCase().indexOf("from");
			int endCrit = originalQuery.indexOf("-- End");
			return originalQuery.substring(0, firstFrom) +
				"INTO " + name + NEW_LINE +
				originalQuery.substring(firstFrom, endCrit) +
				";" + NEW_LINE + " CREATE CLUSTERED COLUMNSTORE INDEX idx ON " +
				name + ";" + NEW_LINE +
				originalQuery.substring(endCrit);
		}
	}

	private static class SqlLocation {
		private final String domain;
		private final int start;
		private final int end;
		private final String query;

		private String name;

		public SqlLocation(String domain, int start, int end, String query) {
			this.domain = domain;
			this.start = start;
			this.end = end;
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
	}

	public static void main(String[] args) {

		if (args.length != 2) {
			System.out.println("Please provide an input filename and an output filename.");
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

		String oldSql = contentBuilder.toString();
		String newSql = translateToCustomVaSql(oldSql);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
			writer.write(newSql);
		} catch (IOException e) {
			System.err.println("An error occurred while writing to the file: " + e.getMessage());
		}
	}
}
