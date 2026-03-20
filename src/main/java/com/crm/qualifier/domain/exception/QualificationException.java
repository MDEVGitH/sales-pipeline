package com.crm.qualifier.domain.exception;

public class QualificationException extends RuntimeException {

    public QualificationException(String message) {
        super(message);
    }

    public QualificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
