package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class DocumentParseException extends ApiException {

    public DocumentParseException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_FAILED", "Parse Failed", detail);
    }

    public DocumentParseException(String detail, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_FAILED", "Parse Failed", detail, cause);
    }
}
