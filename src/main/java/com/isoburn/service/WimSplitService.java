package com.isoburn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WimSplitService {

    private static final Logger log = LoggerFactory.getLogger(WimSplitService.class);

    private final CommandExecutor commandExecutor;

    @Value("${isoburn.wim-max-size-gb:4}")
    private long wimMaxSizeGb;

    @Value("${isoburn.wim-split-size-mb:3800}")
    private int wimSplitSizeMb;

    private static final String INSTALL_WIM_PATH = "sources/install.wim";
    private static final Pattern WIMLIB_PROGRESS_PATTERN = Pattern.compile("\\((\\d+)%\\)");

    public WimSplitService(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public record WimCheckResult(boolean needsSplit, File wimFile, long sizeBytes) {}

    public WimCheckResult checkWimFile(String isoMountPoint) {
        File wimFile = new File(isoMountPoint, INSTALL_WIM_PATH);

        if (!wimFile.exists()) {
            log.debug("No install.wim found at {}", wimFile.getAbsolutePath());
            return new WimCheckResult(false, null, 0);
        }

        long sizeBytes = wimFile.length();
        long maxSizeBytes = wimMaxSizeGb * 1024L * 1024L * 1024L;

        log.info("Found install.wim: {} bytes ({} GB)", sizeBytes,
                String.format("%.2f", sizeBytes / 1_000_000_000.0));

        boolean needsSplit = sizeBytes > maxSizeBytes;

        if (needsSplit) {
            log.info("install.wim exceeds {} GB limit, will need to split", wimMaxSizeGb);
        }

        return new WimCheckResult(needsSplit, wimFile, sizeBytes);
    }

    public boolean isWimlibInstalled() {
        try {
            CommandExecutor.CommandResult result = commandExecutor.execute("which", "wimlib-imagex");
            return result.isSuccess() && !result.stdout().isBlank();
        } catch (Exception e) {
            log.error("Error checking for wimlib", e);
            return false;
        }
    }

    public boolean splitWimFile(File sourceWim, File destDir, BiConsumer<String, Integer> progressCallback)
            throws Exception {

        if (!isWimlibInstalled()) {
            throw new IllegalStateException(
                "wimlib is not installed. Please install it with: brew install wimlib"
            );
        }

        File sourcesDir = new File(destDir, "sources");
        if (!sourcesDir.exists() && !sourcesDir.mkdirs()) {
            throw new RuntimeException("Failed to create sources directory: " + sourcesDir);
        }

        File outputSwm = new File(sourcesDir, "install.swm");

        String command = String.format(
            "wimlib-imagex split '%s' '%s' %d",
            sourceWim.getAbsolutePath(),
            outputSwm.getAbsolutePath(),
            wimSplitSizeMb
        );

        log.info("Splitting WIM file: {}", command);
        progressCallback.accept("Splitting install.wim (this may take several minutes)...", 0);

        CommandExecutor.CommandResult result = commandExecutor.executeShell(
            line -> {
                log.debug("wimlib: {}", line);
                // Parse percentage from wimlib output like "(45%)"
                Matcher matcher = WIMLIB_PROGRESS_PATTERN.matcher(line);
                if (matcher.find()) {
                    try {
                        int percent = Integer.parseInt(matcher.group(1));
                        progressCallback.accept("Splitting: " + line.trim(), percent);
                    } catch (NumberFormatException ignored) {
                        progressCallback.accept("Splitting: " + line.trim(), -1);
                    }
                }
            },
            line -> log.warn("wimlib stderr: {}", line),
            command
        );

        if (!result.isSuccess()) {
            log.error("wimlib-imagex split failed: {}", result.stderr());
            throw new RuntimeException("Failed to split WIM file: " + result.stderr());
        }

        log.info("WIM file split successfully");
        progressCallback.accept("WIM file split complete", 100);

        return true;
    }

    public String getWimlibInstallInstructions() {
        return """
            wimlib is required to handle Windows 11 ISOs with large install.wim files.

            To install wimlib on macOS, run:
                brew install wimlib

            If you don't have Homebrew installed, visit: https://brew.sh
            """;
    }
}
