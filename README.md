# Aurora Limitless Benchmark

A Java-based command execution and logging system with PostgreSQL integration for benchmarking and performance monitoring.

## Overview

This application provides a robust platform for:
- Executing system commands and capturing their output
- Storing command metadata and execution results in PostgreSQL
- Comprehensive logging of command execution details
- Performance benchmarking and analysis

## Features

### Command Management
- **Add Commands**: Store command metadata with descriptions
- **Execute Commands**: Run commands and capture real-time output
- **Command History**: View execution history and results
- **List Commands**: Browse all stored commands

### Logging & Output
- **Database Storage**: PostgreSQL integration for structured data storage
- **File Logging**: Detailed logs written to file system
- **Real-time Output**: Stream command execution output
- **Performance Metrics**: Execution time, exit codes, and timestamps

### Data Models
- **Commands Table**: Command metadata (id, name, description, created_at)
- **Command_Results Table**: Execution results (command_id, output, exit_code, execution_time, timestamp)

## Technology Stack

- **Java 11+** - Core application language
- **Maven** - Build and dependency management
- **PostgreSQL** - Database for command and result storage
- **PostgreSQL JDBC Driver** - Database connectivity
- **Apache Commons Lang** - Utility functions
- **SLF4J + Logback** - Logging framework
- **HikariCP** - Database connection pooling

## Project Structure

```
AuroraLimitlessBenchmark/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── aurora/
│       │           ├── Main.java                 # Application entry point
│       │           ├── CommandExecutor.java      # Command execution engine
│       │           ├── DatabaseManager.java      # Database operations
│       │           ├── CommandLogger.java        # Logging functionality
│       │           └── model/
│       │               ├── Command.java          # Command data model
│       │               └── CommandResult.java    # Result data model
│       └── resources/
│           ├── application.properties            # Configuration
│           └── database/
│               └── schema.sql                    # Database schema
├── logs/                                          # Output log files
├── pom.xml                                        # Maven configuration
└── README.md                                      # This file
```

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- PostgreSQL 12+

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd AuroraLimitlessBenchmark
   ```

2. **Set up PostgreSQL**
   - Create a database for the application
   - Update `src/main/resources/application.properties` with your database credentials

3. **Build the project**
   ```bash
   mvn clean compile
   ```

4. **Initialize the database**
   ```bash
   mvn exec:java -Dexec.mainClass="com.aurora.DatabaseManager" -Dexec.args="init"
   ```

### Usage

#### Add a new command
```bash
java -jar target/aurora-benchmark-1.0.0.jar add "ls -la" "List directory contents"
```

#### Execute a command
```bash
java -jar target/aurora-benchmark-1.0.0.jar execute 1
```

#### List all commands
```bash
java -jar target/aurora-benchmark-1.0.0.jar list
```

#### View execution history
```bash
java -jar target/aurora-benchmark-1.0.0.jar history 1
```

## Configuration

### Database Configuration
Edit `src/main/resources/application.properties`:
```properties
# Database Configuration
db.url=jdbc:postgresql://localhost:5432/aurora_benchmark
db.username=your_username
db.password=your_password
db.pool.size=10

# Logging Configuration
logging.level=INFO
logging.file.path=logs/
logging.file.pattern=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

## Development

### Building from source
```bash
mvn clean package
```

### Running tests
```bash
mvn test
```

### Code style
This project follows standard Java conventions and uses Maven for build management.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Roadmap

- [ ] Command scheduling and automation
- [ ] Web-based dashboard
- [ ] Performance analytics and reporting
- [ ] Command templates and parameterization
- [ ] Integration with monitoring systems
- [ ] REST API for external integrations

## Support

For support and questions, please open an issue in the GitHub repository. 