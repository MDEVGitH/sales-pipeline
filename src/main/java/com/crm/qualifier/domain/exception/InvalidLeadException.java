package com.crm.qualifier.domain.exception;

public class InvalidLeadException extends IllegalArgumentException {

    public InvalidLeadException(String message) {
        super(message);
    }

    public InvalidLeadException(String message, Throwable cause) {
        super(message, cause);
    }
}
