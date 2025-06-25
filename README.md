# pgJDBC PostgreSQL Client

A Java-based PostgreSQL client application similar to the `psql` command-line tool, built with pgJDBC and JLine for an enhanced interactive experience.

## Features

### ✅ Fully Implemented
- **Interactive SQL Client**: Command-line interface with syntax highlighting and command completion
- **PostgreSQL Integration**: Full support for PostgreSQL using pgJDBC driver
- **Command History**: Persistent command history with JLine
- **Syntax Highlighting**: SQL syntax highlighting for better readability
- **Result Formatting**: Tabular result display similar to psql
- **Connection Management**: Easy database connection with command-line arguments or interactive prompts
- **Smart Parameter Prompting**: Only prompts for missing connection parameters
- **PreparedStatement Mode**: All SQL commands use PreparedStatement for security and performance
- **Logging**: Comprehensive logging with console output management
- **Session-specific logging**: Dynamic log file creation per session with automatic cleanup
- **Advanced command completion**: Context-aware SQL completion with database metadata caching
- **Transaction management**: Explicit transaction control commands with visual indicators
- **Meta-commands**: Full support for psql-like meta-commands
  - ✅ `\connect [database]` or `\c` - Connect to a database (supports multiple formats)
  - ✅ `\list` or `\l` - List all databases
  - ✅ `\dt` - List all tables in current database  
  - ✅ `\d [table]` - Describe a table structure
  - ✅ `\timing [on|off]` - Toggle or set timing of commands
  - ✅ `\begin` - Start a transaction
  - ✅ `\commit` - Commit the current transaction
  - ✅ `\rollback` - Rollback the current transaction
  - ✅ `\savepoint [name]` - Create a savepoint
  - ✅ `\release [name]` - Release a savepoint
  - ✅ `\help` or `\h` - Show help
  - ✅ `\quit` or `\q` - Quit the client
  - ✅ `\mode` - Show current query mode

### ❌ Not Yet Implemented
- **Additional psql meta-commands**: Other psql commands like `\copy`, `\i`, etc.

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- PostgreSQL server running and accessible

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd pgjdbc-postgresql-client
```

2. Build the project:
```bash
mvn clean package
```

3. Run the application:
```bash
java -jar target/pgjdbc-postgresql-client-1.0.0.jar
```

## Usage

### Command Line Arguments

```bash
# Connect with all parameters
java -jar target/pgjdbc-postgresql-client-1.0.0.jar -h localhost -p 5432 -U username -d database -W password

# Connect with minimal parameters (will prompt for missing ones)
java -jar target/pgjdbc-postgresql-client-1.0.0.jar -d mydatabase

# Show help
java -jar target/pgjdbc-postgresql-client-1.0.0.jar --help
```

**Available Options:**
- `-h, --host`: Database host (default: localhost)
- `-p, --port`: Database port (default: 5432)
- `-U, --username`: Database username
- `-d, --database`: Database name
- `-W, --password`: Database password
- `--help`: Show help information

### Interactive Mode

When started without connection parameters, the client will prompt for connection details:

```
Host [localhost]: 
Port [5432]: 
Database: mydatabase
Username: myuser
Password: 
```

**Note**: The client will only prompt for parameters that were not provided via command line arguments.

### Available Commands

#### Meta-commands (start with \)
- `\connect [database]` or `\c` - Connect to a database
  - `\connect database` - Connect to database on localhost:5432
  - `\connect host/database` - Connect to database on specified host
  - `\connect host:port/database` - Connect to database on specified host and port
- `\list` or `\l` - List all databases
- `\dt` - List all tables in current database
- `\d [table]` - Describe a table structure
- `\timing [on|off]` - Toggle or set timing of commands
  - `\timing` - Toggle timing on/off
  - `\timing on` - Enable timing
  - `\timing off` - Disable timing
- `\mode` - Show current query mode
- `\help` or `\h` - Show help
- `\quit` or `\q` - Quit the client

#### SQL Commands
Execute any standard SQL command:
```sql
SELECT * FROM users;
CREATE TABLE test (id SERIAL PRIMARY KEY, name VARCHAR(100));
INSERT INTO test (name) VALUES ('test');
UPDATE test SET name = 'updated' WHERE id = 1;
DELETE FROM test WHERE id = 1;
```

**Note**: All SQL commands are executed using PreparedStatement mode for enhanced security and performance.

## Features in Detail

### Command Completion
- SQL keywords are automatically completed
- Meta-commands are completed
- **Context-aware completion**: Tables, columns, and functions are suggested based on SQL context
- **Database metadata caching**: Efficient completion with 1-minute cache refresh
- **Schema-aware**: Supports schema.table notation
- Press Tab to cycle through completions

### Advanced SQL Completion
The client provides intelligent SQL completion based on the context of your query:
- **After FROM/JOIN**: Suggests table names from all accessible schemas
- **After SELECT/WHERE**: Suggests column names from referenced tables
- **After UPDATE SET**: Suggests columns from the table being updated
- **After INSERT INTO**: Suggests columns for the target table
- **Function names**: Both system and user-defined functions
- **Schema support**: Complete with schema.table format
- **Alias support**: Recognizes table aliases in queries

### Transaction Management
Full support for database transactions with visual feedback:
- **Visual indicator**: Prompt shows `*` when in a transaction
- **Meta-commands**: `\begin`, `\commit`, `\rollback`, `\savepoint`, `\release`
- **SQL commands**: BEGIN, COMMIT, ROLLBACK are intercepted and handled
- **Automatic cleanup**: Active transactions are rolled back on disconnect
- **Savepoint support**: Create and release savepoints within transactions

### Syntax Highlighting
- SQL keywords are highlighted in blue
- String literals are highlighted in green
- Numbers are highlighted in yellow
- Comments are highlighted in cyan
- Meta-commands are highlighted in magenta

### Result Formatting
Query results are displayed in a formatted table:
```
+----+----------+--------+
| id | name     | email  |
+----+----------+--------+
| 1  | John     | john@  |
| 2  | Jane     | jane@  |
+----+----------+--------+
(2 rows)
```

### Command History
- Commands are automatically saved to history
- Use arrow keys to navigate through history
- History persists between sessions

### Query Mode
The client operates in PreparedStatement mode for all SQL commands:
- Better performance for repeated queries
- Protection against SQL injection
- Consistent query execution across all commands

## Development

### Project Structure
```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── aurora/
│   │           └── psql/
│   │               ├── PsqlClient.java      # Main application
│   │               ├── DatabaseManager.java # Database connection management
│   │               ├── ResultFormatter.java # Result formatting
│   │               ├── CommandHistory.java  # Command history
│   │               ├── SqlCompleter.java    # Command completion
│   │               ├── SqlHighlighter.java  # Syntax highlighting
│   │               └── SessionManager.java  # Session-specific logging
│   └── resources/
│       └── logback.xml                      # Logging configuration
```

### Building from Source
```bash
# Compile
mvn compile

# Package
mvn package

# Run with Maven
mvn exec:java
```

### Dependencies
- **pgJDBC**: PostgreSQL JDBC driver (42.7.1)
- **JLine**: Command line interface library (3.24.1)
- **SLF4J + Logback**: Logging framework (2.0.9 + 1.4.14)
- **Apache Commons Lang**: Utility functions (3.14.0)
- **JUnit**: Testing framework (5.10.1) - test scope

## Configuration

### Logging
Logging is configured in `src/main/resources/logback.xml`. Logs are written to:
- Console (INFO level and above, with suppressed verbose startup messages)
- File: `logs/pgjdbc-postgresql-client.log` (INFO level and above) - Main application log
- Session File: `logs/pgjdbc-postgresql-client-{timestamp}_{sessionId}.log` - Session-specific logs
- Rolling file with daily rotation and 10MB max file size
- 30 days retention policy

**Logging Features:**
- Suppressed verbose startup messages from logback and JLine
- Application logs shown on console
- All logs captured in files
- WARN and ERROR level logs from all sources go to files only
- Each session gets its own log file with unique session ID
- Session logs are cleaned up on disconnect
- Session ID is displayed on startup for easy log file identification

### Connection Settings
Default connection settings:
- Host: localhost
- Port: 5432
- Auto-commit: true
- Connection timeout: Default JDBC timeout

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Ensure PostgreSQL server is running
   - Check host and port settings
   - Verify firewall settings

2. **Authentication Failed**
   - Verify username and password
   - Check pg_hba.conf configuration
   - Ensure user has proper permissions

3. **Driver Not Found**
   - Ensure pgJDBC dependency is included
   - Check classpath configuration

4. **Verbose Logging Messages**
   - The current configuration suppresses verbose startup messages
   - If you need debug information, modify `logback.xml`:
   ```xml
   <logger name="com.aurora.psql" level="DEBUG" additivity="false">
   ```

### Debug Mode
To enable debug logging, modify `logback.xml`:
```xml
<logger name="com.aurora.psql" level="DEBUG" additivity="false">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="ROLLING_FILE"/>
</logger>
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- PostgreSQL JDBC Driver team
- JLine project for the excellent command line interface library
- Original psql tool for inspiration 