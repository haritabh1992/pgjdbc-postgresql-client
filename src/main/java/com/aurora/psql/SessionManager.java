package com.aurora.psql;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Manages session-specific logging for the PostgreSQL client
 */
public class SessionManager {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    private final String sessionId;
    private final String logFileName;
    private FileAppender<ILoggingEvent> sessionAppender;
    
    public SessionManager() {
        this.sessionId = generateSessionId();
        this.logFileName = "logs/pgjdbc-postgresql-client-" + sessionId + ".log";
        initializeSessionLogging();
    }
    
    /**
     * Generate a unique session ID with timestamp and UUID
     */
    private String generateSessionId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid;
    }
    
    /**
     * Initialize session-specific logging
     */
    private void initializeSessionLogging() {
        try {
            // Ensure logs directory exists
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // Create encoder
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();
            
            // Create file appender
            sessionAppender = new FileAppender<>();
            sessionAppender.setContext(context);
            sessionAppender.setName("SESSION_FILE_" + sessionId);
            sessionAppender.setFile(logFileName);
            sessionAppender.setEncoder(encoder);
            sessionAppender.start();
            
            // Add appender to root logger for this package
            Logger rootLogger = (Logger) LoggerFactory.getLogger("com.aurora.psql");
            rootLogger.addAppender(sessionAppender);
            
            logger.info("Session logging initialized for session: {}", sessionId);
            logger.info("Session log file: {}", logFileName);
            
        } catch (Exception e) {
            logger.error("Failed to initialize session logging", e);
        }
    }
    
    /**
     * Get the current session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Get the session log file name
     */
    public String getLogFileName() {
        return logFileName;
    }
    
    /**
     * Cleanup session resources
     */
    public void cleanup() {
        logger.info("Cleaning up session: {}", sessionId);
        
        if (sessionAppender != null) {
            try {
                // Remove appender from logger
                Logger rootLogger = (Logger) LoggerFactory.getLogger("com.aurora.psql");
                rootLogger.detachAppender(sessionAppender);
                
                // Stop the appender
                sessionAppender.stop();
                
                logger.info("Session logging cleaned up for session: {}", sessionId);
            } catch (Exception e) {
                logger.error("Error cleaning up session logging", e);
            }
        }
    }
}