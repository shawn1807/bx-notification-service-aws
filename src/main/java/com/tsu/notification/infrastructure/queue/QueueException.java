package com.tsu.notification.infrastructure.queue;

/**
 * Exception thrown when queue operations fail
 */
public class QueueException extends RuntimeException {

    public QueueException(String message) {
        super(message);
    }

    public QueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
