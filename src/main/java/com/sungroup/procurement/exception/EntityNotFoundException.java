package com.sungroup.procurement.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends CustomException {
    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND");
    }
}