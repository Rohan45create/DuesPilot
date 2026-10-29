package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class TooManyDocumentsException extends ApiException {

    public TooManyDocumentsException(String detail) {
        super(HttpStatus.BAD_REQUEST, "TOO_MANY_DOCUMENTS", "Too Many Documents", detail);
    }
}
