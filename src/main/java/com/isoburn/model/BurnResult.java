package com.isoburn.model;

public class BurnResult {
    private boolean success;
    private String message;
    private String errorDetails;
    private long durationMillis;

    public BurnResult() {}

    public BurnResult(boolean success, String message, String errorDetails, long durationMillis) {
        this.success = success;
        this.message = message;
        this.errorDetails = errorDetails;
        this.durationMillis = durationMillis;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }

    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }

    public static BurnResult success(String message) {
        BurnResult r = new BurnResult();
        r.success = true;
        r.message = message;
        return r;
    }

    public static BurnResult failure(String message, String errorDetails) {
        BurnResult r = new BurnResult();
        r.success = false;
        r.message = message;
        r.errorDetails = errorDetails;
        return r;
    }

    public static BurnResult cancelled() {
        BurnResult r = new BurnResult();
        r.success = false;
        r.message = "Operation cancelled by user";
        return r;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success;
        private String message;
        private String errorDetails;
        private long durationMillis;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder errorDetails(String errorDetails) { this.errorDetails = errorDetails; return this; }
        public Builder durationMillis(long durationMillis) { this.durationMillis = durationMillis; return this; }

        public BurnResult build() {
            return new BurnResult(success, message, errorDetails, durationMillis);
        }
    }
}
