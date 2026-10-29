package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class UnsupportedDocumentTypeException extends ApiException {

    public UnsupportedDocumentTypeException(String detail) {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TYPE", "Unsupported Document Type", detail);
    }
}
