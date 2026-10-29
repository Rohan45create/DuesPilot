package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;

public class DemoReadOnlyException extends ApiException {
    public DemoReadOnlyException() {
        super(
            HttpStatus.FORBIDDEN,
            "DEMO_READ_ONLY",
            "Demo workspace is read-only. Please create an account or sign in to save changes."
        );
    }
}
