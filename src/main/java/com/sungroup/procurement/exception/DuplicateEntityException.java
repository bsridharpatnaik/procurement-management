package com.sungroup.procurement.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEntityException extends CustomException {
    public DuplicateEntityException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_ENTITY");
    }
}
