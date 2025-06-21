package com.aurora.psql;

import org.jline.reader.History;
import org.jline.reader.impl.history.DefaultHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages command history for the PostgreSQL client
 */
public class CommandHistory {
    private static final Logger logger = LoggerFactory.getLogger(CommandHistory.class);
    private final History history;

    public CommandHistory() {
        this.history = new DefaultHistory();
    }

    /**
     * Get the JLine history object
     */
    public History getHistory() {
        return history;
    }

    /**
     * Add a command to history
     */
    public void addCommand(String command) {
        if (command != null && !command.trim().isEmpty()) {
            history.add(command);
            logger.debug("Added command to history: {}", command);
        }
    }

    /**
     * Save history to file
     */
    public void saveHistory() {
        try {
            history.save();
            logger.info("Command history saved");
        } catch (Exception e) {
            logger.error("Error saving command history", e);
        }
    }

    /**
     * Load history from file
     */
    public void loadHistory() {
        try {
            history.load();
            logger.info("Command history loaded");
        } catch (Exception e) {
            logger.error("Error loading command history", e);
        }
    }
} 