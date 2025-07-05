package com.sungroup.procurement.exception;

import org.springframework.http.HttpStatus;

class UnauthorizedAccessException extends CustomException {
    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS");
    }
}
