package com.aurora.psql;

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Provides SQL syntax highlighting for the PostgreSQL client
 */
public class SqlHighlighter implements Highlighter {
    private static final Logger logger = LoggerFactory.getLogger(SqlHighlighter.class);

    private static final Pattern SQL_KEYWORDS = Pattern.compile(
        "\\b(SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|" +
        "TABLE|DATABASE|INDEX|VIEW|TRIGGER|FUNCTION|PROCEDURE|SCHEMA|" +
        "GRANT|REVOKE|COMMIT|ROLLBACK|BEGIN|END|TRANSACTION|" +
        "JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|BY|ORDER|" +
        "HAVING|UNION|INTERSECT|EXCEPT|DISTINCT|ALL|AS|IN|" +
        "EXISTS|BETWEEN|LIKE|ILIKE|IS|NULL|NOT|AND|OR|" +
        "COUNT|SUM|AVG|MIN|MAX|LIMIT|OFFSET|CASE|WHEN|" +
        "THEN|ELSE|CAST|COALESCE|NULLIF|CURRENT_DATE|CURRENT_TIME|" +
        "CURRENT_TIMESTAMP|NOW|EXTRACT|DATE_PART|TO_CHAR|TO_DATE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STRING_LITERAL = Pattern.compile(
        "'([^']|'')*'"
    );

    private static final Pattern NUMBER_LITERAL = Pattern.compile(
        "\\b\\d+(\\.\\d+)?\\b"
    );

    private static final Pattern COMMENT = Pattern.compile(
        "--.*$"
    );

    private static final AttributedStyle KEYWORD_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.BLUE);
    private static final AttributedStyle STRING_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    private static final AttributedStyle NUMBER_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle COMMENT_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        
        if (buffer == null || buffer.isEmpty()) {
            return builder.toAttributedString();
        }

        // Handle meta-commands (starting with \)
        if (buffer.startsWith("\\")) {
            builder.append(buffer, AttributedStyle.BOLD.foreground(AttributedStyle.MAGENTA));
            return builder.toAttributedString();
        }

        // Simple highlighting for SQL
        String[] lines = buffer.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append("\n");
            }
            highlightLine(lines[i], builder);
        }

        return builder.toAttributedString();
    }

    private void highlightLine(String line, AttributedStringBuilder builder) {
        // Remove comments first
        String[] parts = COMMENT.split(line, 2);
        String sqlPart = parts[0];
        String commentPart = parts.length > 1 ? parts[1] : null;

        // Highlight SQL part
        highlightSqlPart(sqlPart, builder);

        // Highlight comment part
        if (commentPart != null) {
            builder.append(commentPart, COMMENT_STYLE);
        }
    }

    private void highlightSqlPart(String sql, AttributedStringBuilder builder) {
        // Simple approach: highlight keywords, strings, and numbers
        // This is a basic implementation - a more sophisticated approach would use proper parsing
        
        String[] words = sql.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                builder.append(" ");
            }
            
            String word = words[i];
            
            // Check if it's a keyword
            if (SQL_KEYWORDS.matcher(word).matches()) {
                builder.append(word, KEYWORD_STYLE);
            } else if (word.matches("'.*'")) {
                // String literal
                builder.append(word, STRING_STYLE);
            } else if (word.matches("\\d+(\\.\\d+)?")) {
                // Number literal
                builder.append(word, NUMBER_STYLE);
            } else {
                // Default
                builder.append(word);
            }
        }
    }

    @Override
    public void setErrorIndex(int errorIndex) {
        // Not used in this implementation
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
        // Not used in this implementation
    }
} 