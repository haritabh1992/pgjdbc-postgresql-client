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
    private AdvancedSqlCompleter advancedCompleter;
    
    private Connection connection;
    private String currentDatabase;
    private String currentUser;
    private String currentHost;
    private int currentPort;
    private boolean timingEnabled = false;
    private boolean inTransaction = false;  // Track transaction state

    public PsqlClient() throws IOException {
        this.sessionManager = new SessionManager();
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
        try {
            PsqlClient client = new PsqlClient();
            System.out.println("Session started with ID: " + client.sessionManager.getSessionId());
            client.run(args);
        } catch (Exception e) {
            System.err.println("Error starting PostgreSQL client: " + e.getMessage());
            logger.error("Error starting PostgreSQL client", e);
            System.exit(1);
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
        inTransaction = false;  // Reset transaction state on new connection
        
        // Upgrade to advanced completer with connection
        upgradeToAdvancedCompleter();
        
        System.out.println("Connected to PostgreSQL successfully!");
        
        // Update the completer with the new connection if it exists
        if (advancedCompleter != null) {
            advancedCompleter.setConnection(connection);
        }
        
    }

    /**
     * Upgrade the line reader to use the advanced completer once we have a connection
     */
    private void upgradeToAdvancedCompleter() {
        if (connection != null) {
            try {
                advancedCompleter = new AdvancedSqlCompleter(connection);
                // Update the reader's completer using implementation cast
                if (reader instanceof org.jline.reader.impl.LineReaderImpl) {
                    ((org.jline.reader.impl.LineReaderImpl) reader).setCompleter(advancedCompleter);
                    reader.setOpt(LineReader.Option.COMPLETE_IN_WORD);
                    logger.info("Upgraded to advanced SQL completer with database metadata");
                } else {
                    logger.warn("Unable to upgrade completer - reader is not LineReaderImpl");
                }
            } catch (Exception e) {
                logger.warn("Failed to upgrade to advanced completer, keeping basic completer", e);
            }
        }
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
        while (true) {
            try {
                // Dynamically generate prompt based on current connection
                String prompt = connection != null ? 
                    currentDatabase + (inTransaction ? "*" : "") + "=> " : "psql=> ";
                    
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
                case "\\begin":
                    handleBeginTransaction();
                    break;
                case "\\commit":
                    handleCommitTransaction();
                    break;
                case "\\rollback":
                    handleRollbackTransaction();
                    break;
                case "\\savepoint":
                    handleSavepoint(args);
                    break;
                case "\\release":
                    handleReleaseSavepoint(args);
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
        
        // Handle transaction commands
        String trimmedSql = sql.trim().toUpperCase();
        if (trimmedSql.equals("BEGIN") || trimmedSql.startsWith("BEGIN ") || 
            trimmedSql.equals("START TRANSACTION") || trimmedSql.startsWith("START TRANSACTION ")) {
            try {
                handleBeginTransaction();
                return;
            } catch (SQLException e) {
                System.err.println("SQL Error: " + e.getMessage());
                logger.error("SQL execution error", e);
                return;
            }
        } else if (trimmedSql.equals("COMMIT") || trimmedSql.startsWith("COMMIT ")) {
            try {
                handleCommitTransaction();
                return;
            } catch (SQLException e) {
                System.err.println("SQL Error: " + e.getMessage());
                logger.error("SQL execution error", e);
                return;
            }
        } else if (trimmedSql.equals("ROLLBACK") || trimmedSql.startsWith("ROLLBACK ")) {
            try {
                handleRollbackTransaction();
                return;
            } catch (SQLException e) {
                System.err.println("SQL Error: " + e.getMessage());
                logger.error("SQL execution error", e);
                return;
            }
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Use PreparedStatement mode for all SQL commands
            logger.info("Executing SQL using PreparedStatement mode");
            
            // Execute the SQL using our unified method
            executeSql(sql);
            
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
        // Implementation for \connect command
        DatabaseManager.ConnectionConfig config = new DatabaseManager.ConnectionConfig();
        
        // Parse connection string
        if (!args.isEmpty()) {
            // Check if the args contains host:port/database format
            if (args.contains(":") && args.contains("/")) {
                // Format: host:port/database
                String[] hostPort = args.split(":");
                String[] portDb = hostPort[1].split("/");
                config.host = hostPort[0];
                config.port = Integer.parseInt(portDb[0]);
                config.database = portDb[1];
            } else if (args.contains("/")) {
                // Format: host/database (default port)
                String[] parts = args.split("/");
                config.host = parts[0];
                config.database = parts[1];
                config.port = 5432;
            } else {
                // Format: database (local connection)
                config.database = args;
                config.host = "localhost";
                config.port = 5432;
            }
        }
        
        // Prompt for missing connection details
        if (config.host == null || config.host.isEmpty()) {
            config.host = reader.readLine("Host [localhost]: ");
            if (config.host.isEmpty()) config.host = "localhost";
        }
        
        if (config.port == 0) {
            String portStr = reader.readLine("Port [5432]: ");
            config.port = portStr.isEmpty() ? 5432 : Integer.parseInt(portStr);
        }
        
        if (config.database == null || config.database.isEmpty()) {
            config.database = reader.readLine("Database: ");
        }
        
        if (config.username == null || config.username.isEmpty()) {
            String defaultUser = currentUser != null ? currentUser : System.getProperty("user.name");
            String username = reader.readLine("Username [" + defaultUser + "]: ");
            config.username = username.isEmpty() ? defaultUser : username;
        }
        
        if (config.password == null) {
            config.password = reader.readLine("Password: ", '*');
        }
        
        try {
            // Close existing connection if any
            if (connection != null && !connection.isClosed()) {
                logger.info("Closing existing connection to {}:{}/{}", currentHost, currentPort, currentDatabase);
                connection.close();
            }
            
            // Establish new connection
            connection = databaseManager.connect(config);
            
            // Update connection state
            currentHost = config.host;
            currentPort = config.port;
            currentDatabase = config.database;
            currentUser = config.username;
            inTransaction = false;  // Reset transaction state on new connection
            
            // Upgrade to advanced completer with connection
            upgradeToAdvancedCompleter();
            
            System.out.println("You are now connected to database \"" + currentDatabase + "\" as user \"" + currentUser + "\".");
            
            // Update the completer with the new connection if it exists
            if (advancedCompleter != null) {
                advancedCompleter.setConnection(connection);
            }
            
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            logger.error("Failed to connect to database", e);
            // Restore previous connection state if new connection failed
            if (connection != null && !connection.isClosed()) {
                System.out.println("Previous connection remains active.");
            }
            throw e;
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
        // Implementation for \timing command
        if (args.isEmpty()) {
            // Toggle timing
            timingEnabled = !timingEnabled;
            System.out.println("Timing is " + (timingEnabled ? "on." : "off."));
        } else {
            // Set timing explicitly
            String setting = args.toLowerCase().trim();
            if ("on".equals(setting)) {
                timingEnabled = true;
                System.out.println("Timing is on.");
            } else if ("off".equals(setting)) {
                timingEnabled = false;
                System.out.println("Timing is off.");
            } else {
                System.err.println("\\timing: unrecognized value \"" + args + "\"; assuming \"on\"");
                timingEnabled = true;
                System.out.println("Timing is on.");
            }
        }
    }

    private void handleBeginTransaction() throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (inTransaction) {
            System.out.println("WARNING: there is already a transaction in progress");
            return;
        }
        
        try {
            connection.setAutoCommit(false);
            inTransaction = true;
            System.out.println("BEGIN");
            logger.info("Started new transaction");
        } catch (SQLException e) {
            System.err.println("Error starting transaction: " + e.getMessage());
            throw e;
        }
    }

    private void handleCommitTransaction() throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (!inTransaction) {
            System.out.println("WARNING: there is no transaction in progress");
            return;
        }
        
        try {
            connection.commit();
            connection.setAutoCommit(true);
            inTransaction = false;
            System.out.println("COMMIT");
            logger.info("Committed transaction");
        } catch (SQLException e) {
            System.err.println("Error committing transaction: " + e.getMessage());
            throw e;
        }
    }

    private void handleRollbackTransaction() throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (!inTransaction) {
            System.out.println("WARNING: there is no transaction in progress");
            return;
        }
        
        try {
            connection.rollback();
            connection.setAutoCommit(true);
            inTransaction = false;
            System.out.println("ROLLBACK");
            logger.info("Rolled back transaction");
        } catch (SQLException e) {
            System.err.println("Error rolling back transaction: " + e.getMessage());
            throw e;
        }
    }

    private void handleSavepoint(String savepointName) throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (!inTransaction) {
            System.err.println("ERROR: SAVEPOINT can only be used in transaction blocks");
            return;
        }
        
        if (savepointName.isEmpty()) {
            System.err.println("\\savepoint requires a savepoint name");
            return;
        }
        
        try {
            connection.setSavepoint(savepointName);
            System.out.println("SAVEPOINT " + savepointName);
            logger.info("Created savepoint: {}", savepointName);
        } catch (SQLException e) {
            System.err.println("Error creating savepoint: " + e.getMessage());
            throw e;
        }
    }

    private void handleReleaseSavepoint(String savepointName) throws SQLException {
        if (connection == null) {
            System.err.println("Not connected to any database.");
            return;
        }
        
        if (!inTransaction) {
            System.err.println("ERROR: RELEASE SAVEPOINT can only be used in transaction blocks");
            return;
        }
        
        if (savepointName.isEmpty()) {
            System.err.println("\\release requires a savepoint name");
            return;
        }
        
        try {
            // PostgreSQL doesn't have a direct releaseSavepoint in JDBC, we use SQL
            try (PreparedStatement pstmt = connection.prepareStatement("RELEASE SAVEPOINT " + savepointName)) {
                pstmt.execute();
            }
            System.out.println("RELEASE SAVEPOINT " + savepointName);
            logger.info("Released savepoint: {}", savepointName);
        } catch (SQLException e) {
            System.err.println("Error releasing savepoint: " + e.getMessage());
            throw e;
        }
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
        System.out.println("\\connect [database]  - Connect to a database");
        System.out.println("\\c [database]        - Connect to a database (shorthand)");
        System.out.println("\\list, \\l            - List all databases");
        System.out.println("\\dt                  - List all tables");
        System.out.println("\\d [table]           - Describe a table");
        System.out.println("\\timing [on|off]     - Toggle or set timing of commands");
        System.out.println("\\begin               - Start a transaction");
        System.out.println("\\commit              - Commit the current transaction");
        System.out.println("\\rollback            - Rollback the current transaction");
        System.out.println("\\savepoint [name]    - Create a savepoint");
        System.out.println("\\release [name]      - Release a savepoint");
        System.out.println("\\mode                - Show current query mode");
        System.out.println("\\help, \\h            - Show this help");
        System.out.println("\\quit, \\q            - Quit psql");
        System.out.println();
        System.out.println("Connection formats:");
        System.out.println("  \\connect database");
        System.out.println("  \\connect host/database");
        System.out.println("  \\connect host:port/database");
        System.out.println();
        System.out.println("SQL commands can be entered directly.");
        System.out.println("Commands ending with ; are executed immediately.");
        System.out.println("All queries use PreparedStatement mode for security and performance.");
    }

    private void cleanup() {
        try {
            if (connection != null && !connection.isClosed()) {
                // Rollback any active transaction before closing
                if (inTransaction) {
                    try {
                        connection.rollback();
                        logger.info("Rolled back active transaction during cleanup");
                    } catch (SQLException e) {
                        logger.error("Error rolling back transaction during cleanup", e);
                    }
                }
                connection.close();
            }
            if (sessionManager != null) {
                sessionManager.cleanup();
            }
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
} 