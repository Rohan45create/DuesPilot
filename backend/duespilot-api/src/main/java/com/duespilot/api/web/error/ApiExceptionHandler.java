package com.duespilot.api.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String ERROR_BASE_URL = "https://duespilot.com/errors/";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getDetail());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(URI.create(ERROR_BASE_URL + ex.getCode().toLowerCase()));
        problemDetail.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        StringBuilder detailBuilder = new StringBuilder("Validation failed for fields: ");

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            detailBuilder.append(fieldError.getField()).append(" (").append(fieldError.getDefaultMessage()).append("); ");
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailBuilder.toString().trim());
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create(ERROR_BASE_URL + "validation_failed"));
        problemDetail.setProperty("code", "VALIDATION_FAILED");
        problemDetail.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Uploaded file exceeds maximum allowed size of 10 MB (Rule R-53)."
        );
        problemDetail.setTitle("File Too Large");
        problemDetail.setType(URI.create(ERROR_BASE_URL + "file_too_large"));
        problemDetail.setProperty("code", "FILE_TOO_LARGE");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problemDetail);
    }

    @ExceptionHandler(com.duespilot.api.llm.error.DailyBudgetExceededException.class)
    public ResponseEntity<ProblemDetail> handleDailyBudgetExceeded(com.duespilot.api.llm.error.DailyBudgetExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            ex.getMessage() != null ? ex.getMessage() : "Daily LLM cost budget cap reached. Live analysis is disabled; replay mode remains active."
        );
        problemDetail.setTitle("Daily Budget Exceeded");
        problemDetail.setType(URI.create(ERROR_BASE_URL + "daily_budget_exceeded"));
        problemDetail.setProperty("code", "DAILY_BUDGET_EXCEEDED");

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }

    @ExceptionHandler(com.duespilot.api.llm.error.CaseBudgetExceededException.class)
    public ResponseEntity<ProblemDetail> handleCaseBudgetExceeded(com.duespilot.api.llm.error.CaseBudgetExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            ex.getMessage() != null ? ex.getMessage() : "Per-case cost cap reached. Analysis was stopped to prevent excess spending."
        );
        problemDetail.setTitle("Per-Case Cap Exceeded");
        problemDetail.setType(URI.create(ERROR_BASE_URL + "per_case_cap_exceeded"));
        problemDetail.setProperty("code", "PER_CASE_CAP_EXCEEDED");

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred."
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create(ERROR_BASE_URL + "internal_server_error"));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
