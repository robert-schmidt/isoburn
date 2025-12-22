package com.isoburn.model;

public class BurnProgress {

    public enum Phase {
        PREPARING("Preparing..."),
        UNMOUNTING("Unmounting drive..."),
        FORMATTING("Formatting drive..."),
        MOUNTING_ISO("Mounting ISO..."),
        CHECKING_WIM("Checking WIM file size..."),
        SPLITTING_WIM("Splitting WIM file..."),
        COPYING("Copying files..."),
        CLEANUP("Cleaning up..."),
        COMPLETE("Complete"),
        ERROR("Error"),
        CANCELLED("Cancelled");

        private final String description;

        Phase(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private Phase phase;
    private double percentage;
    private String currentFile;
    private String message;
    private long bytesTransferred;
    private long totalBytes;

    public BurnProgress() {}

    public BurnProgress(Phase phase, double percentage, String currentFile,
                        String message, long bytesTransferred, long totalBytes) {
        this.phase = phase;
        this.percentage = percentage;
        this.currentFile = currentFile;
        this.message = message;
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
    }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public static BurnProgress of(Phase phase, String message) {
        BurnProgress p = new BurnProgress();
        p.phase = phase;
        p.message = message;
        p.percentage = 0;
        return p;
    }

    public static BurnProgress of(Phase phase, double percentage, String message) {
        BurnProgress p = new BurnProgress();
        p.phase = phase;
        p.percentage = percentage;
        p.message = message;
        return p;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Phase phase;
        private double percentage;
        private String currentFile;
        private String message;
        private long bytesTransferred;
        private long totalBytes;

        public Builder phase(Phase phase) { this.phase = phase; return this; }
        public Builder percentage(double percentage) { this.percentage = percentage; return this; }
        public Builder currentFile(String currentFile) { this.currentFile = currentFile; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder bytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; return this; }
        public Builder totalBytes(long totalBytes) { this.totalBytes = totalBytes; return this; }

        public BurnProgress build() {
            return new BurnProgress(phase, percentage, currentFile, message, bytesTransferred, totalBytes);
        }
    }
}
