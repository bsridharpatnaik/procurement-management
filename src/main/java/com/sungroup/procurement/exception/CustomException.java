package com.sungroup.procurement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public CustomException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.errorCode = "BAD_REQUEST";
    }

    public CustomException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = status.name();
    }

    public CustomException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}


