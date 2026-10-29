package com.duespilot.api.llm.error;

public class UltraCallLimitExceededException extends RuntimeException {
    public UltraCallLimitExceededException(String message) {
        super(message);
    }
}
