package com.isoburn.service;

import com.isoburn.model.RemovableDrive;
import com.isoburn.util.PlistParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DriveDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DriveDetectionService.class);

    private final CommandExecutor commandExecutor;
    private final PlistParser plistParser;

    @Value("${isoburn.excluded-disks:disk0,disk1}")
    private String excludedDisks;

    public DriveDetectionService(CommandExecutor commandExecutor, PlistParser plistParser) {
        this.commandExecutor = commandExecutor;
        this.plistParser = plistParser;
    }

    public List<RemovableDrive> detectRemovableDrives() {
        List<RemovableDrive> drives = new ArrayList<>();
        Set<String> excluded = Arrays.stream(excludedDisks.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        try {
            // First try external drives
            CommandExecutor.CommandResult listResult = commandExecutor.execute(
                "diskutil", "list", "-plist", "external"
            );

            if (listResult.isSuccess()) {
                List<RemovableDrive> basicDrives = plistParser.parseDiskutilList(listResult.stdout());
                for (RemovableDrive basicDrive : basicDrives) {
                    addDriveIfValid(basicDrive, excluded, drives);
                }
            }

            // Also scan all disks for removable media (catches SD cards in internal readers)
            CommandExecutor.CommandResult allDisksResult = commandExecutor.execute(
                "diskutil", "list", "-plist"
            );

            if (allDisksResult.isSuccess()) {
                List<RemovableDrive> allDrives = plistParser.parseDiskutilList(allDisksResult.stdout());
                for (RemovableDrive basicDrive : allDrives) {
                    // Skip if already added
                    String deviceId = basicDrive.getDeviceIdentifier();
                    boolean alreadyAdded = drives.stream()
                        .anyMatch(d -> d.getDeviceIdentifier().equals(deviceId));
                    if (!alreadyAdded) {
                        addDriveIfValid(basicDrive, excluded, drives);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error detecting removable drives", e);
        }

        return drives;
    }

    private void addDriveIfValid(RemovableDrive basicDrive, Set<String> excluded, List<RemovableDrive> drives) {
        String deviceId = basicDrive.getDeviceIdentifier();

        if (excluded.contains(deviceId)) {
            log.debug("Skipping excluded disk: {}", deviceId);
            return;
        }

        // Skip partitions like "disk0s1", "disk6s1" - they have "s" followed by a digit after "disk#"
        if (deviceId.matches("disk\\d+s\\d+.*")) {
            log.debug("Skipping partition: {}", deviceId);
            return;
        }

        RemovableDrive detailedDrive = getDriveInfo(deviceId);
        if (detailedDrive != null && isValidRemovableDrive(detailedDrive)) {
            drives.add(detailedDrive);
            log.info("Found removable drive: {}", detailedDrive.getDisplayName());
        }
    }

    public RemovableDrive getDriveInfo(String deviceIdentifier) {
        try {
            CommandExecutor.CommandResult infoResult = commandExecutor.execute(
                "diskutil", "info", "-plist", deviceIdentifier
            );

            if (!infoResult.isSuccess()) {
                log.error("Failed to get disk info for {}: {}", deviceIdentifier, infoResult.stderr());
                return null;
            }

            return plistParser.parseDiskInfo(infoResult.stdout());

        } catch (Exception e) {
            log.error("Error getting drive info for " + deviceIdentifier, e);
            return null;
        }
    }

    private boolean isValidRemovableDrive(RemovableDrive drive) {
        if (drive.getDeviceIdentifier() == null) {
            return false;
        }

        Set<String> excluded = Arrays.stream(excludedDisks.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        if (excluded.contains(drive.getDeviceIdentifier())) {
            return false;
        }

        // Exclude mounted disk images (.dmg, .iso files)
        if (drive.isDiskImage()) {
            log.debug("Skipping disk image: {}", drive.getDeviceIdentifier());
            return false;
        }

        return drive.isExternal() || drive.isRemovable();
    }

    public boolean isDriveAvailable(String deviceIdentifier) {
        RemovableDrive drive = getDriveInfo(deviceIdentifier);
        return drive != null && isValidRemovableDrive(drive);
    }
}
