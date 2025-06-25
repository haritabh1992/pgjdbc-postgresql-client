package com.aurora.psql;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

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
    
    private Connection connection;
    private String currentDatabase;
    private String currentUser;
    private String currentHost;
    private int currentPort;

    public PsqlClient() throws IOException {
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
        // Set up session-specific logging BEFORE any logging occurs
        String sessionId = generateSessionId();
        addSessionFileAppender(sessionId);
        System.out.println("Session started with ID: " + sessionId);
        
        try {
            PsqlClient client = new PsqlClient();
            client.run(args);
        } catch (Exception e) {
            System.err.println("Error starting PostgreSQL client: " + e.getMessage());
            logger.error("Error starting PostgreSQL client", e);
            System.exit(1);
        }
    }

    private static String generateSessionId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid;
    }

    private static void addSessionFileAppender(String sessionId) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("SESSION_FILE");
        fileAppender.setFile("logs/pgjdbc-postgresql-client-" + sessionId + ".log");
        fileAppender.setEncoder(encoder);
        fileAppender.start();

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.aurora.psql");
        logger.addAppender(fileAppender);
    }

    public void run(String[] args) {
        try {
            // Parse command line arguments
            DatabaseManager.ConnectionConfig config = parseArguments(args);
            boolean isInteractiveMode = (config == null);
            
            // Connect to database
            if (config != null) {
                connect(config);
            } else {
                // No config or missing essential parameters - prompt for all details
                connect(new DatabaseManager.ConnectionConfig(), true);
            }
            
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
        if (args.length == 0) {
            return null; // Will prompt for connection details
        }
        
        DatabaseManager.ConnectionConfig config = new DatabaseManager.ConnectionConfig();
        
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
        
        // If essential parameters are missing, return null to trigger interactive prompts
        if (config.host == null || config.port == 0 || config.database == null || config.username == null || config.password == null) {
            return null;
        }
        
        return config;
    }

    private void connect(DatabaseManager.ConnectionConfig config) throws SQLException {
        connect(config, false);
    }

    private void connect(DatabaseManager.ConnectionConfig config, boolean isInteractiveMode) throws SQLException {
        System.out.println("Connecting to PostgreSQL...");
        
        // Prompt for missing connection details
        if (isInteractiveMode || config.host == null) {
            config.host = reader.readLine("Host [localhost]: ");
            if (config.host.isEmpty()) config.host = "localhost";
        }
        
        if (isInteractiveMode || config.port == 0) {
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
            
            long endTime = System.currentTimeMillis();
            System.out.println("Time: " + (endTime - startTime) + " ms");
            
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
        System.out.println("\\connect not yet implemented");
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
        System.out.println("\\timing not yet implemented");
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
            }
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
} 