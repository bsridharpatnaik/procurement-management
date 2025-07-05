package com.sungroup.procurement.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends CustomException {
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
