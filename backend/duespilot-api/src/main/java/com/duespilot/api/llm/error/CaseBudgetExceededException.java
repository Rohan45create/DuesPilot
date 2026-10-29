package com.duespilot.api.llm.error;

public class CaseBudgetExceededException extends RuntimeException {
    public CaseBudgetExceededException(String message) {
        super(message);
    }
}
