package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;
import java.util.UUID;

public class CaseNotFoundException extends ApiException {

    public CaseNotFoundException(UUID caseId) {
        super(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case Not Found", "Case with id " + caseId + " does not exist.");
    }

    public CaseNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case Not Found", message);
    }
}
