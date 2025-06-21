package com.aurora.psql;

import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats SQL result sets for display
 */
public class ResultFormatter {
    private static final Logger logger = LoggerFactory.getLogger(ResultFormatter.class);

    public void formatResultSet(ResultSet rs, Terminal terminal) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // Get column names and calculate widths
        String[] columnNames = new String[columnCount];
        int[] columnWidths = new int[columnCount];
        
        for (int i = 1; i <= columnCount; i++) {
            columnNames[i - 1] = metaData.getColumnName(i);
            columnWidths[i - 1] = columnNames[i - 1].length();
        }
        
        // Collect all rows and calculate maximum widths
        List<String[]> rows = new ArrayList<>();
        while (rs.next()) {
            String[] row = new String[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                if (value == null) {
                    value = "NULL";
                }
                row[i - 1] = value;
                columnWidths[i - 1] = Math.max(columnWidths[i - 1], value.length());
            }
            rows.add(row);
        }
        
        // Print header
        printSeparator(columnWidths);
        printRow(columnNames, columnWidths);
        printSeparator(columnWidths);
        
        // Print data rows
        for (String[] row : rows) {
            printRow(row, columnWidths);
        }
        
        // Print footer
        printSeparator(columnWidths);
        
        // Print row count
        System.out.println("(" + rows.size() + " row" + (rows.size() != 1 ? "s" : "") + ")");
    }

    private void printRow(String[] values, int[] widths) {
        System.out.print("|");
        for (int i = 0; i < values.length; i++) {
            System.out.print(" " + padRight(values[i], widths[i]) + " |");
        }
        System.out.println();
    }

    private void printSeparator(int[] widths) {
        System.out.print("+");
        for (int width : widths) {
            System.out.print("-".repeat(width + 2) + "+");
        }
        System.out.println();
    }

    private String padRight(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        return str + " ".repeat(length - str.length());
    }
} 