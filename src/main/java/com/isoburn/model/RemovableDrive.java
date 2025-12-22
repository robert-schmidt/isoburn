package com.isoburn.model;

public class RemovableDrive {
    private String deviceIdentifier;
    private String name;
    private long sizeBytes;
    private String mountPoint;
    private boolean removable;
    private boolean external;
    private String busProtocol;

    public RemovableDrive() {}

    public RemovableDrive(String deviceIdentifier, String name, long sizeBytes,
                          String mountPoint, boolean removable, boolean external, String busProtocol) {
        this.deviceIdentifier = deviceIdentifier;
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.mountPoint = mountPoint;
        this.removable = removable;
        this.external = external;
        this.busProtocol = busProtocol;
    }

    public String getDeviceIdentifier() { return deviceIdentifier; }
    public void setDeviceIdentifier(String deviceIdentifier) { this.deviceIdentifier = deviceIdentifier; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getMountPoint() { return mountPoint; }
    public void setMountPoint(String mountPoint) { this.mountPoint = mountPoint; }

    public boolean isRemovable() { return removable; }
    public void setRemovable(boolean removable) { this.removable = removable; }

    public boolean isExternal() { return external; }
    public void setExternal(boolean external) { this.external = external; }

    public String getBusProtocol() { return busProtocol; }
    public void setBusProtocol(String busProtocol) { this.busProtocol = busProtocol; }

    public boolean isDiskImage() {
        return "Disk Image".equals(busProtocol);
    }

    public String getDisplayName() {
        String sizeMB = String.format("%.1f GB", sizeBytes / 1_000_000_000.0);
        String displayName = name != null && !name.isBlank() ? name : "Untitled";
        return String.format("%s (%s) - %s", displayName, deviceIdentifier, sizeMB);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String deviceIdentifier;
        private String name;
        private long sizeBytes;
        private String mountPoint;
        private boolean removable;
        private boolean external;
        private String busProtocol;

        public Builder deviceIdentifier(String deviceIdentifier) { this.deviceIdentifier = deviceIdentifier; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder sizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder mountPoint(String mountPoint) { this.mountPoint = mountPoint; return this; }
        public Builder removable(boolean removable) { this.removable = removable; return this; }
        public Builder external(boolean external) { this.external = external; return this; }
        public Builder busProtocol(String busProtocol) { this.busProtocol = busProtocol; return this; }

        public RemovableDrive build() {
            return new RemovableDrive(deviceIdentifier, name, sizeBytes, mountPoint, removable, external, busProtocol);
        }
    }
}
