package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException(String detail) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File Too Large", detail);
    }
}
