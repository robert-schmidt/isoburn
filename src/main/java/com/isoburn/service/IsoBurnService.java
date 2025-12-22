package com.isoburn.service;

import com.isoburn.model.BurnProgress;
import com.isoburn.model.BurnProgress.Phase;
import com.isoburn.model.BurnResult;
import com.isoburn.model.RemovableDrive;
import com.isoburn.util.PlistParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
public class IsoBurnService {

    private static final Logger log = LoggerFactory.getLogger(IsoBurnService.class);

    private final CommandExecutor commandExecutor;
    private final PlistParser plistParser;
    private final WimSplitService wimSplitService;
    private final DriveDetectionService driveDetectionService;

    @Value("${isoburn.volume-name:ISOBURN}")
    private String volumeName;

    private static final int COPY_BUFFER_SIZE = 8 * 1024 * 1024; // 8MB buffer for fast copying

    private volatile String mountedIsoPath = null;
    private volatile boolean isCancelled = false;

    public IsoBurnService(CommandExecutor commandExecutor, PlistParser plistParser,
                          WimSplitService wimSplitService, DriveDetectionService driveDetectionService) {
        this.commandExecutor = commandExecutor;
        this.plistParser = plistParser;
        this.wimSplitService = wimSplitService;
        this.driveDetectionService = driveDetectionService;
    }

    public void cancel() {
        isCancelled = true;
        commandExecutor.cancel();
    }

    public void reset() {
        isCancelled = false;
        commandExecutor.reset();
        mountedIsoPath = null;
    }

    public BurnResult burn(File isoFile, RemovableDrive targetDrive,
                           boolean bootable, boolean handleLargeWim,
                           Consumer<BurnProgress> progressCallback) {
        reset();
        long startTime = System.currentTimeMillis();

        try {
            if (!isoFile.exists() || !isoFile.canRead()) {
                return BurnResult.failure("ISO file not accessible",
                    "Cannot read file: " + isoFile.getAbsolutePath());
            }

            if (!driveDetectionService.isDriveAvailable(targetDrive.getDeviceIdentifier())) {
                return BurnResult.failure("Drive not available",
                    "The selected drive is no longer available: " + targetDrive.getDeviceIdentifier());
            }

            progressCallback.accept(BurnProgress.of(Phase.UNMOUNTING, "Unmounting drive..."));
            if (!unmountDrive(targetDrive)) {
                return BurnResult.failure("Failed to unmount drive",
                    "Could not unmount " + targetDrive.getDeviceIdentifier());
            }
            checkCancelled();

            progressCallback.accept(BurnProgress.of(Phase.FORMATTING, "Formatting drive as FAT32..."));
            if (!formatDrive(targetDrive)) {
                return BurnResult.failure("Failed to format drive",
                    "Could not format " + targetDrive.getDeviceIdentifier());
            }
            checkCancelled();

            progressCallback.accept(BurnProgress.of(Phase.MOUNTING_ISO, "Mounting ISO image..."));
            String isoMountPoint = mountIso(isoFile);
            if (isoMountPoint == null) {
                return BurnResult.failure("Failed to mount ISO",
                    "Could not mount " + isoFile.getName());
            }
            checkCancelled();

            WimSplitService.WimCheckResult wimCheck = null;
            if (handleLargeWim) {
                progressCallback.accept(BurnProgress.of(Phase.CHECKING_WIM, "Checking for large WIM file..."));
                wimCheck = wimSplitService.checkWimFile(isoMountPoint);

                if (wimCheck.needsSplit()) {
                    if (!wimSplitService.isWimlibInstalled()) {
                        cleanup();
                        return BurnResult.failure("wimlib not installed",
                            wimSplitService.getWimlibInstallInstructions());
                    }
                }
            } else {
                wimCheck = new WimSplitService.WimCheckResult(false, null, 0);
            }
            checkCancelled();

            String usbMountPoint = findUsbMountPoint();
            if (usbMountPoint == null) {
                cleanup();
                return BurnResult.failure("USB drive not mounted",
                    "The formatted drive could not be found");
            }

            progressCallback.accept(BurnProgress.of(Phase.COPYING, 0, "Starting file copy..."));
            if (!copyFiles(isoMountPoint, usbMountPoint, wimCheck, progressCallback)) {
                cleanup();
                return BurnResult.failure("Failed to copy files",
                    "File copy operation failed");
            }
            checkCancelled();

            if (handleLargeWim && wimCheck.needsSplit()) {
                progressCallback.accept(BurnProgress.of(Phase.SPLITTING_WIM, 0, "Splitting install.wim..."));
                try {
                    wimSplitService.splitWimFile(wimCheck.wimFile(), new File(usbMountPoint),
                        (msg, percent) -> {
                            if (percent >= 0) {
                                progressCallback.accept(BurnProgress.of(Phase.SPLITTING_WIM, percent, msg));
                            } else {
                                progressCallback.accept(BurnProgress.of(Phase.SPLITTING_WIM, msg));
                            }
                        });
                } catch (Exception e) {
                    cleanup();
                    return BurnResult.failure("Failed to split WIM file", e.getMessage());
                }
            }

            progressCallback.accept(BurnProgress.of(Phase.CLEANUP, "Ejecting drive..."));
            cleanup();
            ejectDrive(targetDrive);

            long duration = System.currentTimeMillis() - startTime;
            progressCallback.accept(BurnProgress.of(Phase.COMPLETE, 100, "Complete!"));

            return BurnResult.builder()
                    .success(true)
                    .message("ISO burned successfully to " + targetDrive.getDisplayName())
                    .durationMillis(duration)
                    .build();

        } catch (CancelledException e) {
            cleanup();
            return BurnResult.cancelled();
        } catch (Exception e) {
            log.error("Burn operation failed", e);
            cleanup();
            return BurnResult.failure("Unexpected error", e.getMessage());
        }
    }

    private void checkCancelled() throws CancelledException {
        if (isCancelled || commandExecutor.isCancelled()) {
            throw new CancelledException();
        }
    }

    private boolean unmountDrive(RemovableDrive drive) {
        try {
            CommandExecutor.CommandResult result = commandExecutor.execute(
                "diskutil", "unmountDisk", drive.getDeviceIdentifier()
            );
            if (!result.isSuccess()) {
                log.warn("Unmount returned non-zero, but continuing: {}", result.stderr());
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to unmount drive", e);
            return false;
        }
    }

    private boolean formatDrive(RemovableDrive drive) {
        try {
            log.info("Formatting drive: {}", drive.getDeviceIdentifier());

            CommandExecutor.CommandResult result = commandExecutor.execute(
                "diskutil", "eraseDisk", "FAT32", volumeName, "MBRFormat", drive.getDeviceIdentifier()
            );

            if (!result.isSuccess()) {
                log.error("Format failed: {}", result.stderr());
                return false;
            }

            log.info("Drive formatted successfully");
            Thread.sleep(2000);
            return true;

        } catch (Exception e) {
            log.error("Failed to format drive", e);
            return false;
        }
    }

    private String mountIso(File isoFile) {
        try {
            CommandExecutor.CommandResult result = commandExecutor.execute(
                "hdiutil", "mount", "-readonly", "-plist", isoFile.getAbsolutePath()
            );

            if (!result.isSuccess()) {
                log.error("Failed to mount ISO: {}", result.stderr());
                return null;
            }

            String mountPoint = plistParser.parseHdiutilMountPoint(result.stdout());
            if (mountPoint != null) {
                mountedIsoPath = mountPoint;
                log.info("ISO mounted at: {}", mountPoint);
            }

            return mountPoint;

        } catch (Exception e) {
            log.error("Failed to mount ISO", e);
            return null;
        }
    }

    private String findUsbMountPoint() {
        try {
            Thread.sleep(1000);

            File volumesDir = new File("/Volumes");
            File[] volumes = volumesDir.listFiles();

            if (volumes != null) {
                for (File volume : volumes) {
                    if (volume.getName().equalsIgnoreCase(volumeName)) {
                        log.info("Found USB mount point: {}", volume.getAbsolutePath());
                        return volume.getAbsolutePath();
                    }
                }
            }

            log.error("Could not find USB mount point for volume: {}", volumeName);
            return null;

        } catch (Exception e) {
            log.error("Error finding USB mount point", e);
            return null;
        }
    }

    private boolean copyFiles(String source, String dest, WimSplitService.WimCheckResult wimCheck,
                               Consumer<BurnProgress> progressCallback) {
        try {
            Path sourcePath = Paths.get(source);
            Path destPath = Paths.get(dest);

            // Determine exclusion path for large WIM files
            String excludePath = wimCheck.needsSplit() ? "sources/install.wim" : null;

            log.info("Calculating total size to copy...");
            progressCallback.accept(BurnProgress.of(Phase.COPYING, "Calculating size..."));

            // Calculate total size
            long totalSize = calculateTotalSize(sourcePath, excludePath);
            log.info("Total size to copy: {} bytes ({} MB)", totalSize, totalSize / (1024 * 1024));

            // Track progress
            AtomicLong bytesCopied = new AtomicLong(0);
            int[] lastPercent = {-1};

            // Walk and copy files
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    checkCancelledIO();

                    Path relative = sourcePath.relativize(dir);
                    Path targetDir = destPath.resolve(relative);

                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    checkCancelledIO();

                    Path relative = sourcePath.relativize(file);

                    // Skip excluded file
                    if (excludePath != null && relative.toString().equalsIgnoreCase(excludePath)) {
                        log.info("Skipping large WIM file: {}", relative);
                        return FileVisitResult.CONTINUE;
                    }

                    Path targetFile = destPath.resolve(relative);

                    // Copy with progress tracking
                    copyFileWithProgress(file, targetFile, attrs.size(), bytesCopied, totalSize,
                        lastPercent, progressCallback);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.error("Failed to visit file: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });

            // Final 100%
            progressCallback.accept(BurnProgress.builder()
                .phase(Phase.COPYING)
                .percentage(100)
                .message("File copy complete")
                .build());

            log.info("File copy complete");
            return true;

        } catch (CancelledIOException e) {
            log.info("File copy cancelled");
            return false;
        } catch (Exception e) {
            log.error("Failed to copy files", e);
            return false;
        }
    }

    private long calculateTotalSize(Path source, String excludePath) throws IOException {
        AtomicLong totalSize = new AtomicLong(0);

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = source.relativize(file);
                if (excludePath != null && relative.toString().equalsIgnoreCase(excludePath)) {
                    return FileVisitResult.CONTINUE;
                }
                totalSize.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });

        return totalSize.get();
    }

    private void copyFileWithProgress(Path source, Path target, long fileSize,
                                       AtomicLong bytesCopied, long totalSize,
                                       int[] lastPercent, Consumer<BurnProgress> progressCallback)
            throws IOException {

        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                checkCancelledIO();

                out.write(buffer, 0, bytesRead);
                long copied = bytesCopied.addAndGet(bytesRead);

                // Update progress (throttle to avoid too many updates)
                int percent = totalSize > 0 ? (int) ((copied * 100) / totalSize) : 0;
                if (percent != lastPercent[0] && percent < 100) {
                    lastPercent[0] = percent;
                    progressCallback.accept(BurnProgress.builder()
                        .phase(Phase.COPYING)
                        .percentage(percent)
                        .message("Copying files... " + percent + "%")
                        .build());
                }
            }
        }
    }

    private void checkCancelledIO() throws CancelledIOException {
        if (isCancelled || commandExecutor.isCancelled()) {
            throw new CancelledIOException();
        }
    }

    private static class CancelledIOException extends IOException {
        public CancelledIOException() {
            super("Operation cancelled");
        }
    }

    private void cleanup() {
        if (mountedIsoPath != null) {
            try {
                log.info("Unmounting ISO: {}", mountedIsoPath);
                commandExecutor.execute("hdiutil", "unmount", mountedIsoPath);
                mountedIsoPath = null;
            } catch (Exception e) {
                log.error("Failed to unmount ISO", e);
            }
        }
    }

    private void ejectDrive(RemovableDrive drive) {
        try {
            commandExecutor.execute("diskutil", "eject", drive.getDeviceIdentifier());
            log.info("Drive ejected: {}", drive.getDeviceIdentifier());
        } catch (Exception e) {
            log.error("Failed to eject drive", e);
        }
    }

    private static class CancelledException extends Exception {
        public CancelledException() {
            super("Operation cancelled");
        }
    }
}
