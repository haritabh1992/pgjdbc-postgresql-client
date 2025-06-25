package com.aurora.psql;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Manages session-specific functionality including logging
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    private final String sessionId;
    private FileAppender<ILoggingEvent> sessionFileAppender;
    
    public SessionManager() {
        this.sessionId = generateSessionId();
        initializeSessionLogging();
    }
    
    /**
     * Generate a unique session ID
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
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        
        sessionFileAppender = new FileAppender<>();
        sessionFileAppender.setContext(context);
        sessionFileAppender.setName("SESSION_FILE_" + sessionId);
        sessionFileAppender.setFile("logs/pgjdbc-postgresql-client-" + sessionId + ".log");
        sessionFileAppender.setEncoder(encoder);
        sessionFileAppender.start();
        
        ch.qos.logback.classic.Logger rootLogger = 
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.aurora.psql");
        rootLogger.addAppender(sessionFileAppender);
        
        logger.info("Session logging initialized with ID: {}", sessionId);
    }
    
    /**
     * Get the session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Cleanup session resources
     */
    public void cleanup() {
        if (sessionFileAppender != null) {
            logger.info("Closing session log file for session: {}", sessionId);
            
            // Remove appender from logger
            ch.qos.logback.classic.Logger rootLogger = 
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.aurora.psql");
            rootLogger.detachAppender(sessionFileAppender);
            
            // Stop the appender
            sessionFileAppender.stop();
            sessionFileAppender = null;
        }
    }
}