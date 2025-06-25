package com.aurora.psql;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Properties;

/**
 * PostgreSQL Client - A Java-based psql-like application
 */
public class PsqlClient {
    private static final Logger logger = LoggerFactory.getLogger(PsqlClient.class);
    
    private final DatabaseManager databaseManager;
    private final ResultFormatter resultFormatter;
    private final CommandHistory commandHistory;
    private final Terminal terminal;
    private final LineReader reader;
    private final SessionManager sessionManager;
    
    private Connection connection;
    private String currentDatabase;
    private String currentUser;
    private String currentHost;
    private int currentPort;
    private boolean timingEnabled = false;

    public PsqlClient() throws IOException {
        this.sessionManager = null;
        this.databaseManager = new DatabaseManager();
        this.resultFormatter = new ResultFormatter();
        this.commandHistory = new CommandHistory();
        
        // Initialize terminal and line reader
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(new SqlCompleter())
                .highlighter(new SqlHighlighter())
                .history(commandHistory.getHistory())
                .build();
    }

    public PsqlClient(SessionManager sessionManager) throws IOException {
        this.sessionManager = sessionManager;
        this.databaseManager = new DatabaseManager();
        this.resultFormatter = new ResultFormatter();
        this.commandHistory = new CommandHistory();
        
        // Initialize terminal and line reader
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(new SqlCompleter())
                .highlighter(new SqlHighlighter())
                .history(commandHistory.getHistory())
                .build();
    }

    public static void main(String[] args) {
        SessionManager sessionManager = null;
        
        try {
            // Initialize session manager first
            sessionManager = new SessionManager();
            System.out.println("Session started with ID: " + sessionManager.getSessionId());
            
            PsqlClient client = new PsqlClient(sessionManager);
            client.run(args);
        } catch (Exception e) {
            System.err.println("Error starting PostgreSQL client: " + e.getMessage());
            logger.error("Error starting PostgreSQL client", e);
            System.exit(1);
        } finally {
            // Cleanup session manager
            if (sessionManager != null) {
                sessionManager.cleanup();
            }
        }
    }

    public void run(String[] args) {
        try {
            // Parse command line arguments
            DatabaseManager.ConnectionConfig config = parseArguments(args);
            
            // Connect to database - always use the config, even if some params are missing
            connect(config);
            
            // Show welcome message
            showWelcomeMessage();
            
            // Start interactive mode
            startInteractiveMode();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error in PostgreSQL client", e);
        } finally {
            cleanup();
        }
    }

    private DatabaseManager.ConnectionConfig parseArguments(String[] args) {
        DatabaseManager.ConnectionConfig config = new DatabaseManager.ConnectionConfig();
        
        if (args.length == 0) {
            return config; // Return empty config, will prompt for all details
        }
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    if (i + 1 < args.length) config.host = args[++i];
                    break;
                case "-p":
                case "--port":
                    if (i + 1 < args.length) config.port = Integer.parseInt(args[++i]);
                    break;
                case "-U":
                case "--username":
                    if (i + 1 < args.length) config.username = args[++i];
                    break;
                case "-d":
                case "--database":
                    if (i + 1 < args.length) config.database = args[++i];
                    break;
                case "-W":
                case "--password":
                    if (i + 1 < args.length) config.password = args[++i];
                    break;
                case "--help":
                    showHelp();
                    System.exit(0);
                    break;
            }
        }
        
        // Return the config even if some parameters are missing
        // The connect method will prompt only for missing ones
        return config;
    }

    private void connect(DatabaseManager.ConnectionConfig config) throws SQLException {
        System.out.println("Connecting to PostgreSQL...");
        
        // Prompt only for missing connection details
        if (config.host == null) {
            config.host = reader.readLine("Host [localhost]: ");
            if (config.host.isEmpty()) config.host = "localhost";
        }
        
        if (config.port == 0) {
            String portStr = reader.readLine("Port [5432]: ");
            config.port = portStr.isEmpty() ? 5432 : Integer.parseInt(portStr);
        }
        
        if (config.database == null) {
            config.database = reader.readLine("Database: ");
        }
        
        if (config.username == null) {
            config.username = reader.readLine("Username: ");
        }
        
        if (config.password == null) {
            config.password = reader.readLine("Password: ", '*');
        }
        
        // Establish connection
        connection = databaseManager.connect(config);
        currentHost = config.host;
        currentPort = config.port;
        currentDatabase = config.database;
        currentUser = config.username;
        
        System.out.println("Connected to PostgreSQL successfully!");
    }

    private void showWelcomeMessage() {
        System.out.println("=".repeat(60));
        System.out.println("PostgreSQL Client v1.0.0");
        System.out.println("=".repeat(60));
        
        if (connection != null) {
            System.out.println("Connected to: " + currentHost + ":" + currentPort + "/" + currentDatabase);
            System.out.println("User: " + currentUser);
        }
        
        System.out.println("Type '\\help' for available commands");
        System.out.println("Type '\\quit' to exit");
        System.out.println("=".repeat(60));
        System.out.println();
    }

    private void startInteractiveMode() {
        String prompt = connection != null ? 
            currentDatabase + "=> " : "psql=> ";
        
        while (true) {
            try {
                String line = reader.readLine(prompt);
                
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                
                // Handle meta-commands (starting with \)
                if (line.startsWith("\\")) {
                    handleMetaCommand(line);
                } else {
                    // Handle SQL commands
                    handleSqlCommand(line);
                }
                
            } catch (UserInterruptException e) {
                System.out.println("^C");
            } catch (EndOfFileException e) {
                System.out.println("\\q");
                break;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                logger.error("Error in interactive mode", e);
            }
        }
    }

    private void handleMetaCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        try {
            switch (cmd) {
                case "\\connect":
                case "\\c":
                    handleConnect(args);
                    break;
                case "\\list":
                case "\\l":
                    handleListDatabases();
                    break;
                case "\\dt":
                    handleListTables();
                    break;
                case "\\d":
                    handleDescribeTable(args);
                    break;
                case "\\timing":
                    handleTiming(args);
                    break;
                case "\\mode":
                    showQueryMode();
                    break;
                case "\\help":
                case "\\h":
                    showHelp();
                    break;
                case "\\quit":
                case "\\q":
                    System.out.println("Goodbye!");
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown command: " + cmd);
                    System.err.println("Type \\help for available commands");
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
        }
    }

    private void handleSqlCommand(String sql) {
        if (connection == null) {
            System.err.println("Not connected to any database. Use \\connect to establish a connection.");
            return;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Use PreparedStatement mode for all SQL commands
            logger.info("Executing SQL using PreparedStatement mode");
            
            // Execute the SQL using our unified method
            executeSql(sql);
            
            // Only show timing if enabled
            if (timingEnabled) {
                long endTime = System.currentTimeMillis();
                System.out.println("Time: " + (endTime - startTime) + " ms");
            }
            
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            logger.error("SQL execution error", e);
        }
    }

    /**
     * Unified SQL execution method using PreparedStatement mode
     */
    private void executeSql(String sql) throws SQLException {
        // For simple queries without parameters, we can use a PreparedStatement with the full SQL
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            boolean hasResultSet = pstmt.execute();
            
            if (hasResultSet) {
                try (ResultSet rs = pstmt.getResultSet()) {
                    resultFormatter.formatResultSet(rs, terminal);
                }
            } else {
                int updateCount = pstmt.getUpdateCount();
                System.out.println("Query executed successfully. " + updateCount + " row(s) affected.");
            }
        }
    }

    private void handleConnect(String args) throws SQLException {
        DatabaseManager.ConnectionConfig config = new DatabaseManager.ConnectionConfig();
        boolean needsPrompting = true;
        
        if (!args.isEmpty()) {
            // Parse connection string
            if (args.contains("/")) {
                // Format: host:port/database or just host/database
                String[] parts = args.split("/", 2);
                String hostPart = parts[0];
                config.database = parts[1];
                
                if (hostPart.contains(":")) {
                    // host:port format
                    String[] hostParts = hostPart.split(":", 2);
                    config.host = hostParts[0];
                    try {
                        config.port = Integer.parseInt(hostParts[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + hostParts[1]);
                        return;
                    }
                } else {
                    // just host
                    config.host = hostPart;
                }
            } else {
                // Just database name
                config.database = args;
                // Keep existing host and port if already connected
                if (connection != null) {
                    config.host = currentHost;
                    config.port = currentPort;
                }
            }
        } else {
            // No arguments provided, prompt for everything
            needsPrompting = true;
        }
        
        // Close existing connection first
        if (connection != null) {
            System.out.println("Closing current connection...");
            try {
                connection.close();
                logger.info("Closed previous connection to {}:{}/{}", currentHost, currentPort, currentDatabase);
            } catch (SQLException e) {
                logger.error("Error closing previous connection", e);
            }
            connection = null;
        }
        
        try {
            // Connect with the new configuration
            connect(config);
            
            // Update the prompt with new database name
            String prompt = connection != null ? 
                currentDatabase + "=> " : "psql=> ";
                
            System.out.println("You are now connected to database \"" + currentDatabase + "\" as user \"" + currentUser + "\".");
            
        } catch (SQLException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            logger.error("Failed to connect to database", e);
            // Try to restore previous connection if possible
            // For now, just leave disconnected
            connection = null;
        }
    }

    private void handleListDatabases() throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        String sql = "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname";
        
        System.out.println("List of databases");
        System.out.println("Name");
        System.out.println("-".repeat(20));
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                System.out.println(rs.getString("datname"));
            }
        }
    }

    private void handleListTables() throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        String sql = "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' ORDER BY table_name";
        
        System.out.println("List of relations");
        System.out.println("Schema | Name | Type | Owner");
        System.out.println("-".repeat(50));
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                System.out.println("public | " + rs.getString("table_name") + " | table | " + currentUser);
            }
        }
    }

    private void handleDescribeTable(String tableName) throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (tableName.isEmpty()) {
            System.err.println("\\d requires a table name");
            return;
        }
        
        String sql = "SELECT column_name, data_type, is_nullable, column_default " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = ? ORDER BY ordinal_position";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                resultFormatter.formatResultSet(rs, terminal);
            }
        }
    }

    private void handleTiming(String args) {
        args = args.trim().toLowerCase();
        
        if (args.isEmpty()) {
            // Toggle timing
            timingEnabled = !timingEnabled;
        } else if (args.equals("on")) {
            timingEnabled = true;
        } else if (args.equals("off")) {
            timingEnabled = false;
        } else {
            System.err.println("Invalid argument for \\timing. Use 'on', 'off', or no argument to toggle.");
            return;
        }
        
        System.out.println("Timing is " + (timingEnabled ? "on" : "off") + ".");
    }

    private void showQueryMode() {
        System.out.println("Current Query Mode: PreparedStatement");
        System.out.println("All SQL commands are executed using pgJDBC PreparedStatement mode");
        System.out.println("Benefits:");
        System.out.println("- Better performance for repeated queries");
        System.out.println("- Protection against SQL injection");
        System.out.println("- Consistent query execution across all commands");
    }

    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("\\connect [dbname] - Connect to a database");
        System.out.println("\\list            - List all databases");
        System.out.println("\\dt              - List all tables");
        System.out.println("\\d [table]       - Describe a table");
        System.out.println("\\timing          - Toggle timing of commands");
        System.out.println("\\mode            - Show current query mode");
        System.out.println("\\help            - Show this help");
        System.out.println("\\quit            - Quit psql");
        System.out.println();
        System.out.println("SQL commands can be entered directly.");
        System.out.println("Commands ending with ; are executed immediately.");
        System.out.println("All queries use PreparedStatement mode for security and performance.");
    }

    private void cleanup() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
            if (terminal != null) {
                terminal.close();
            }
            if (sessionManager != null) {
                sessionManager.cleanup();
            }
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
} 