# pgJDBC PostgreSQL Client

A Java-based PostgreSQL client application similar to the `psql` command-line tool, built with pgJDBC and JLine for an enhanced interactive experience.

## Features

- **Interactive SQL Client**: Command-line interface with syntax highlighting and command completion
- **PostgreSQL Integration**: Full support for PostgreSQL using pgJDBC driver
- **Meta-commands**: Support for psql-like meta-commands (\\dt, \\d, \\list, etc.)
- **Command History**: Persistent command history with JLine
- **Syntax Highlighting**: SQL syntax highlighting for better readability
- **Result Formatting**: Tabular result display similar to psql
- **Connection Management**: Easy database connection with command-line arguments or interactive prompts

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
java -jar target/pgjdbc-postgresql-client-1.0.0.jar -h localhost -p 5432 -U username -d database

# Connect with minimal parameters (will prompt for missing ones)
java -jar target/pgjdbc-postgresql-client-1.0.0.jar -d mydatabase

# Show help
java -jar target/pgjdbc-postgresql-client-1.0.0.jar --help
```

### Interactive Mode

When started without connection parameters, the client will prompt for connection details:

```
Host [localhost]: 
Port [5432]: 
Database: mydatabase
Username: myuser
Password: 
```

### Available Commands

#### Meta-commands (start with \)
- `\connect [dbname]` - Connect to a database
- `\list` or `\l` - List all databases
- `\dt` - List all tables in current database
- `\d [table]` - Describe a table structure
- `\timing` - Toggle timing of commands
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

## Features in Detail

### Command Completion
- SQL keywords are automatically completed
- Meta-commands are completed
- Press Tab to cycle through completions

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
│   │               └── SqlHighlighter.java  # Syntax highlighting
│   └── resources/
│       └── logback.xml                      # Logging configuration
```

### Building from Source
```bash
# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Run with Maven
mvn exec:java
```

### Dependencies
- **pgJDBC**: PostgreSQL JDBC driver
- **JLine**: Command line interface library
- **SLF4J + Logback**: Logging framework
- **Apache Commons Lang**: Utility functions

## Configuration

### Logging
Logging is configured in `src/main/resources/logback.xml`. Logs are written to:
- Console (INFO level and above)
- File: `logs/pgjdbc-postgresql-client.log` (INFO level and above)

### Connection Settings
Default connection settings:
- Host: localhost
- Port: 5432
- Auto-commit: true

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

### Debug Mode
To enable debug logging, modify `logback.xml`:
```xml
<logger name="com.aurora.psql" level="DEBUG" additivity="false">
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