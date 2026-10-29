package com.duespilot.api.llm.error;

public class DailyBudgetExceededException extends RuntimeException {
    public DailyBudgetExceededException(String message) {
        super(message);
    }
}
