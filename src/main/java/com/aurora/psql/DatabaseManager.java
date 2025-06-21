package com.aurora.psql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages database connections for the PostgreSQL client
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    public DatabaseManager() {
        // Load PostgreSQL JDBC driver
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found", e);
        }
    }

    /**
     * Establish a connection to PostgreSQL
     */
    public Connection connect(ConnectionConfig config) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s", 
                                 config.host, config.port, config.database);
        
        logger.info("Connecting to PostgreSQL: {}", url);
        
        Connection connection = DriverManager.getConnection(url, config.username, config.password);
        connection.setAutoCommit(true);
        
        logger.info("Successfully connected to PostgreSQL");
        return connection;
    }

    /**
     * Test if a connection is valid
     */
    public boolean isConnectionValid(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            logger.error("Error checking connection validity", e);
            return false;
        }
    }

    /**
     * Close a connection
     */
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }

    /**
     * Connection configuration
     */
    public static class ConnectionConfig {
        public String host = "localhost";
        public int port = 5432;
        public String database;
        public String username;
        public String password;
    }
} 