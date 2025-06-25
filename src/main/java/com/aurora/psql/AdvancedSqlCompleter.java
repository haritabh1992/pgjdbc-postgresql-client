package com.aurora.psql;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides advanced context-aware SQL command completion for the PostgreSQL client
 */
public class AdvancedSqlCompleter implements Completer {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedSqlCompleter.class);

    // SQL Keywords
    private static final String[] SQL_KEYWORDS = {
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
        "TABLE", "DATABASE", "INDEX", "VIEW", "TRIGGER", "FUNCTION", "PROCEDURE", "SCHEMA",
        "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "BEGIN", "END", "TRANSACTION",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER",
        "HAVING", "UNION", "INTERSECT", "EXCEPT", "DISTINCT", "ALL", "AS", "IN",
        "EXISTS", "BETWEEN", "LIKE", "ILIKE", "IS", "NULL", "NOT", "AND", "OR",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "LIMIT", "OFFSET", "CASE", "WHEN",
        "THEN", "ELSE", "CAST", "COALESCE", "NULLIF", "CURRENT_DATE", "CURRENT_TIME",
        "CURRENT_TIMESTAMP", "NOW", "EXTRACT", "DATE_PART", "TO_CHAR", "TO_DATE",
        "INTO", "VALUES", "SET", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
        "CONSTRAINT", "UNIQUE", "CHECK", "DEFAULT", "CASCADE", "RESTRICT"
    };

    // Meta commands
    private static final String[] META_COMMANDS = {
        "\\connect", "\\c", "\\list", "\\l", "\\dt", "\\d", "\\timing", 
        "\\begin", "\\commit", "\\rollback", "\\savepoint", "\\release",
        "\\help", "\\h", "\\quit", "\\q", "\\mode"
    };

    // PostgreSQL system functions
    private static final String[] SYSTEM_FUNCTIONS = {
        "pg_database_size", "pg_relation_size", "pg_total_relation_size",
        "pg_size_pretty", "current_database", "current_schema", "current_schemas",
        "current_user", "session_user", "version", "pg_backend_pid",
        "pg_is_in_recovery", "pg_last_wal_receive_lsn", "pg_last_wal_replay_lsn",
        "pg_last_xact_replay_timestamp", "age", "clock_timestamp", "timeofday",
        "array_agg", "string_agg", "json_agg", "jsonb_agg", "row_number",
        "rank", "dense_rank", "percent_rank", "cume_dist", "ntile",
        "lag", "lead", "first_value", "last_value", "nth_value"
    };

    private Connection connection;
    
    // Cache for database metadata
    private final Map<String, List<String>> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> tableCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> columnCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> functionCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_TIMEOUT = 60000; // 1 minute

    // Patterns for context detection
    private static final Pattern FROM_PATTERN = Pattern.compile(
        "\\b(FROM|JOIN|INTO|UPDATE|DELETE\\s+FROM)\\s+([\\w.]*)?$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "\\bSELECT\\s+([\\w\\s,.*]*?)\\s*$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern WHERE_PATTERN = Pattern.compile(
        "\\b(WHERE|AND|OR|ON)\\s+([\\w.]*)?$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "\\bINSERT\\s+INTO\\s+(\\w+)\\s*\\(([\\w\\s,]*)?$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern UPDATE_SET_PATTERN = Pattern.compile(
        "\\bUPDATE\\s+(\\w+)\\s+SET\\s+([\\w\\s,=]*)?$", 
        Pattern.CASE_INSENSITIVE
    );

    public AdvancedSqlCompleter(Connection connection) {
        this.connection = connection;
        // Initial cache load
        refreshCache();
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
        clearCache();
        refreshCache();
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        int cursor = line.cursor();
        
        // Don't complete if cursor is at the beginning
        if (cursor == 0) {
            return;
        }
        
        String word = line.word();
        String textBeforeCursor = buffer.substring(0, cursor);
        
        // Complete meta-commands (starting with \)
        if (word.startsWith("\\")) {
            completeMetaCommands(word, candidates);
            return;
        }
        
        // Refresh cache if needed
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TIMEOUT) {
            refreshCache();
        }
        
        // Context-aware completion
        if (matchesFromContext(textBeforeCursor)) {
            completeTables(word, candidates);
        } else if (matchesSelectContext(textBeforeCursor) || matchesWhereContext(textBeforeCursor)) {
            completeColumns(word, textBeforeCursor, candidates);
        } else if (matchesInsertContext(textBeforeCursor)) {
            completeInsertColumns(word, textBeforeCursor, candidates);
        } else if (matchesUpdateContext(textBeforeCursor)) {
            completeUpdateColumns(word, textBeforeCursor, candidates);
        } else if (word.contains("(")) {
            // Function completion
            completeFunctions(word, candidates);
        } else {
            // Default to keyword completion
            completeKeywords(word, candidates);
        }
    }

    private boolean matchesFromContext(String text) {
        Matcher matcher = FROM_PATTERN.matcher(text);
        return matcher.find();
    }

    private boolean matchesSelectContext(String text) {
        Matcher matcher = SELECT_PATTERN.matcher(text);
        return matcher.find();
    }

    private boolean matchesWhereContext(String text) {
        Matcher matcher = WHERE_PATTERN.matcher(text);
        return matcher.find();
    }

    private boolean matchesInsertContext(String text) {
        Matcher matcher = INSERT_PATTERN.matcher(text);
        return matcher.find();
    }

    private boolean matchesUpdateContext(String text) {
        Matcher matcher = UPDATE_SET_PATTERN.matcher(text);
        return matcher.find();
    }

    private void completeMetaCommands(String word, List<Candidate> candidates) {
        for (String command : META_COMMANDS) {
            if (command.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(command));
            }
        }
    }

    private void completeKeywords(String word, List<Candidate> candidates) {
        // Add SQL keywords
        for (String keyword : SQL_KEYWORDS) {
            if (keyword.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(keyword));
            }
        }
        
        // Add system functions
        for (String function : SYSTEM_FUNCTIONS) {
            if (function.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(function + "("));
            }
        }
    }

    private void completeTables(String word, List<Candidate> candidates) {
        String lowerWord = word.toLowerCase();
        
        // Check if word contains schema prefix
        if (word.contains(".")) {
            String[] parts = word.split("\\.", 2);
            String schema = parts[0];
            String tablePrefix = parts.length > 1 ? parts[1] : "";
            
            List<String> tables = tableCache.get(schema);
            if (tables != null) {
                for (String table : tables) {
                    if (table.toLowerCase().startsWith(tablePrefix.toLowerCase())) {
                        candidates.add(new Candidate(schema + "." + table));
                    }
                }
            }
        } else {
            // Complete with all tables from all schemas
            for (Map.Entry<String, List<String>> entry : tableCache.entrySet()) {
                String schema = entry.getKey();
                for (String table : entry.getValue()) {
                    if (table.toLowerCase().startsWith(lowerWord)) {
                        // For public schema, offer both with and without schema prefix
                        if ("public".equals(schema)) {
                            candidates.add(new Candidate(table));
                        }
                        candidates.add(new Candidate(schema + "." + table));
                    }
                }
            }
        }
    }

    private void completeColumns(String word, String textBeforeCursor, List<Candidate> candidates) {
        // Extract table names from the query
        Set<String> tableNames = extractTableNames(textBeforeCursor);
        
        String lowerWord = word.toLowerCase();
        
        // Check if word contains table prefix
        if (word.contains(".")) {
            String[] parts = word.split("\\.", 2);
            String tableAlias = parts[0];
            String columnPrefix = parts.length > 1 ? parts[1] : "";
            
            // Find the actual table name for the alias
            String tableName = findTableForAlias(textBeforeCursor, tableAlias);
            if (tableName == null) {
                tableName = tableAlias; // Assume it's the actual table name
            }
            
            List<String> columns = columnCache.get(tableName.toLowerCase());
            if (columns != null) {
                for (String column : columns) {
                    if (column.toLowerCase().startsWith(columnPrefix.toLowerCase())) {
                        candidates.add(new Candidate(tableAlias + "." + column));
                    }
                }
            }
        } else {
            // Complete with columns from all referenced tables
            for (String tableName : tableNames) {
                List<String> columns = columnCache.get(tableName.toLowerCase());
                if (columns != null) {
                    for (String column : columns) {
                        if (column.toLowerCase().startsWith(lowerWord)) {
                            candidates.add(new Candidate(column));
                        }
                    }
                }
            }
        }
    }

    private void completeInsertColumns(String word, String textBeforeCursor, List<Candidate> candidates) {
        Matcher matcher = INSERT_PATTERN.matcher(textBeforeCursor);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            List<String> columns = columnCache.get(tableName.toLowerCase());
            if (columns != null) {
                for (String column : columns) {
                    if (column.toLowerCase().startsWith(word.toLowerCase())) {
                        candidates.add(new Candidate(column));
                    }
                }
            }
        }
    }

    private void completeUpdateColumns(String word, String textBeforeCursor, List<Candidate> candidates) {
        Matcher matcher = UPDATE_SET_PATTERN.matcher(textBeforeCursor);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            List<String> columns = columnCache.get(tableName.toLowerCase());
            if (columns != null) {
                for (String column : columns) {
                    if (column.toLowerCase().startsWith(word.toLowerCase())) {
                        candidates.add(new Candidate(column));
                    }
                }
            }
        }
    }

    private void completeFunctions(String word, List<Candidate> candidates) {
        String funcPrefix = word.substring(0, word.indexOf('(')).toLowerCase();
        
        // System functions
        for (String function : SYSTEM_FUNCTIONS) {
            if (function.toLowerCase().startsWith(funcPrefix)) {
                candidates.add(new Candidate(function + "("));
            }
        }
        
        // User-defined functions
        for (List<String> functions : functionCache.values()) {
            for (String function : functions) {
                if (function.toLowerCase().startsWith(funcPrefix)) {
                    candidates.add(new Candidate(function + "("));
                }
            }
        }
    }

    private Set<String> extractTableNames(String query) {
        Set<String> tables = new HashSet<>();
        String upperQuery = query.toUpperCase();
        
        // Simple extraction - can be improved with proper SQL parsing
        Pattern tablePattern = Pattern.compile(
            "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([\\w.]+)(?:\\s+(?:AS\\s+)?(\\w+))?",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = tablePattern.matcher(query);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            // Remove schema prefix if present
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.lastIndexOf('.') + 1);
            }
            tables.add(tableName);
        }
        
        return tables;
    }

    private String findTableForAlias(String query, String alias) {
        Pattern aliasPattern = Pattern.compile(
            "\\b([\\w.]+)\\s+(?:AS\\s+)?" + Pattern.quote(alias) + "\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = aliasPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    private void refreshCache() {
        if (connection == null) {
            return;
        }
        
        try {
            logger.debug("Refreshing database metadata cache");
            
            // Cache schemas
            cacheSchemas();
            
            // Cache tables
            cacheTables();
            
            // Cache columns
            cacheColumns();
            
            // Cache functions
            cacheFunctions();
            
            lastCacheUpdate = System.currentTimeMillis();
            
        } catch (SQLException e) {
            logger.error("Error refreshing metadata cache", e);
        }
    }

    private void cacheSchemas() throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                schemas.add(rs.getString("TABLE_SCHEM"));
            }
        }
        schemaCache.put("all", schemas);
    }

    private void cacheTables() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        
        // Get tables for each schema
        for (String schema : schemaCache.getOrDefault("all", Collections.emptyList())) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = metadata.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            if (!tables.isEmpty()) {
                tableCache.put(schema, tables);
            }
        }
    }

    private void cacheColumns() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        
        // Get columns for each table
        for (Map.Entry<String, List<String>> entry : tableCache.entrySet()) {
            String schema = entry.getKey();
            for (String table : entry.getValue()) {
                List<String> columns = new ArrayList<>();
                try (ResultSet rs = metadata.getColumns(null, schema, table, "%")) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME"));
                    }
                }
                if (!columns.isEmpty()) {
                    columnCache.put(table.toLowerCase(), columns);
                    // Also cache with schema prefix
                    columnCache.put((schema + "." + table).toLowerCase(), columns);
                }
            }
        }
    }

    private void cacheFunctions() throws SQLException {
        // Query PostgreSQL system catalog for functions
        String sql = "SELECT n.nspname as schema, p.proname as function " +
                    "FROM pg_proc p " +
                    "JOIN pg_namespace n ON p.pronamespace = n.oid " +
                    "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') " +
                    "ORDER BY schema, function";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            Map<String, List<String>> functions = new HashMap<>();
            while (rs.next()) {
                String schema = rs.getString("schema");
                String function = rs.getString("function");
                
                functions.computeIfAbsent(schema, k -> new ArrayList<>()).add(function);
            }
            
            functionCache.clear();
            functionCache.putAll(functions);
        }
    }

    private void clearCache() {
        schemaCache.clear();
        tableCache.clear();
        columnCache.clear();
        functionCache.clear();
        lastCacheUpdate = 0;
    }
}