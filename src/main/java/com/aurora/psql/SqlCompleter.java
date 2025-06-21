package com.aurora.psql;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provides SQL command completion for the PostgreSQL client
 */
public class SqlCompleter implements Completer {
    private static final Logger logger = LoggerFactory.getLogger(SqlCompleter.class);

    private static final String[] SQL_KEYWORDS = {
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
        "TABLE", "DATABASE", "INDEX", "VIEW", "TRIGGER", "FUNCTION", "PROCEDURE", "SCHEMA",
        "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "BEGIN", "END", "TRANSACTION",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER",
        "HAVING", "UNION", "INTERSECT", "EXCEPT", "DISTINCT", "ALL", "AS", "IN",
        "EXISTS", "BETWEEN", "LIKE", "ILIKE", "IS", "NULL", "NOT", "AND", "OR",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "LIMIT", "OFFSET", "CASE", "WHEN",
        "THEN", "ELSE", "CAST", "COALESCE", "NULLIF", "CURRENT_DATE", "CURRENT_TIME",
        "CURRENT_TIMESTAMP", "NOW", "EXTRACT", "DATE_PART", "TO_CHAR", "TO_DATE"
    };

    private static final String[] META_COMMANDS = {
        "\\connect", "\\c", "\\list", "\\l", "\\dt", "\\d", "\\timing", "\\help", "\\h", "\\quit", "\\q"
    };

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        int cursor = line.cursor();
        
        // Don't complete if cursor is at the beginning
        if (cursor == 0) {
            return;
        }
        
        String word = line.word();
        int wordIndex = line.wordIndex();
        
        // Complete meta-commands (starting with \)
        if (word.startsWith("\\")) {
            completeMetaCommands(word, candidates);
            return;
        }
        
        // Complete SQL keywords (only at word boundaries)
        if (wordIndex == 0 || buffer.charAt(cursor - 1) == ' ') {
            completeSqlKeywords(word, candidates);
        }
    }

    private void completeMetaCommands(String word, List<Candidate> candidates) {
        for (String command : META_COMMANDS) {
            if (command.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(command));
            }
        }
    }

    private void completeSqlKeywords(String word, List<Candidate> candidates) {
        for (String keyword : SQL_KEYWORDS) {
            if (keyword.toLowerCase().startsWith(word.toLowerCase())) {
                candidates.add(new Candidate(keyword));
            }
        }
    }
} 