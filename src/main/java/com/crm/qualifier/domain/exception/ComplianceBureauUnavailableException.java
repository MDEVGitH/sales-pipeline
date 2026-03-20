package com.crm.qualifier.domain.exception;

public class ComplianceBureauUnavailableException extends Exception {

    public ComplianceBureauUnavailableException(String message) {
        super(message);
    }

    public ComplianceBureauUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
