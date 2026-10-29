package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    public ValidationException(String detail) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation Failed", detail);
    }
}
