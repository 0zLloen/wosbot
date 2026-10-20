package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.diagnostics.DiagnosticSnapshotStore;
import dev.frostguard.engine.service.StaminaService;

class StaminaHelperHudReadTest {

    @TempDir
    Path workspace;

    @Test
    void directHudEntryPersistsTheReadingAndCleansUpOnce() {
        StaminaService service = StaminaService.getServices();
        RecordingHudUi ui = new RecordingHudUi(42);
        StaminaHelper helper = new StaminaHelper(service, 9301L, ui);

        helper.updateStaminaFromHud();

        assertEquals(List.of("open-hud", "read", "cleanup"), ui.events);
        assertEquals(1, ui.dialogOpenCount);
        assertEquals(1, ui.ocrCount);
        assertEquals(5, ui.ocrAttempts);
        assertEquals(200L, ui.ocrRetryMs);
        assertEquals(1, ui.cleanupCount);
        assertEquals(42, service.findCurrentStamina(9301L).orElseThrow());
    }

    @Test
    void exhaustedOcrCapturesStaminaFailureBeforeOneCleanup() throws IOException {
        StaminaService service = StaminaService.getServices();
        RecordingHudUi ui = new RecordingHudUi(null);
        SnapshotStaminaHelper helper = new SnapshotStaminaHelper(service, 9302L, ui, workspace);

        helper.updateStaminaFromHud();

        assertTrue(service.findCurrentStamina(9302L).isEmpty());
        assertEquals(List.of("open-hud", "read", "snapshot", "cleanup"), ui.events);
        assertEquals(1, ui.cleanupCount);
        assertEquals(1, helper.captureCount);
        assertTrue(helper.snapshotResult.startsWith("snapshot=logs/snapshot/stamina/"));
        Path captures = workspace.resolve("logs/snapshot/stamina");
        try (var files = Files.list(captures)) {
            List<Path> saved = files.toList();
            assertEquals(1, saved.size());
            assertTrue(saved.getFirst().getFileName().toString().endsWith("-ocr-failed.png"));
        }
    }

    @Test
    void disabledDiagnosticsDoNotCaptureOrWriteAFile() {
        StaminaService service = StaminaService.getServices();
        RecordingHudUi ui = new RecordingHudUi(null);
        SnapshotStaminaHelper helper = new SnapshotStaminaHelper(service, 9303L, ui, workspace);
        helper.snapshotsEnabled = false;

        helper.updateStaminaFromHud();

        assertTrue(service.findCurrentStamina(9303L).isEmpty());
        assertEquals(List.of("open-hud", "read", "cleanup"), ui.events);
        assertEquals(0, helper.captureCount);
        assertEquals(1, ui.cleanupCount);
        assertEquals("snapshot=disabled", helper.snapshotResult);
        assertTrue(Files.notExists(workspace.resolve("logs/snapshot/stamina")));
    }

    @Test
    void readExceptionLeavesStaminaUnknownAndStillCleansUp() {
        StaminaService service = StaminaService.getServices();
        RecordingHudUi ui = new RecordingHudUi(null);
        ui.failRead = true;
        StaminaHelper helper = new StaminaHelper(service, 9304L, ui);

        helper.updateStaminaFromHud();

        assertTrue(service.findCurrentStamina(9304L).isEmpty());
        assertEquals(List.of("open-hud", "read", "cleanup"), ui.events);
        assertEquals(1, ui.cleanupCount);
    }

    private static final class RecordingHudUi implements StaminaHelper.HudStaminaUi {
        private final Integer reading;
        private boolean failRead;
        private final List<String> events = new ArrayList<>();
        private int dialogOpenCount;
        private int ocrCount;
        private int ocrAttempts;
        private long ocrRetryMs;
        private int cleanupCount;

        private RecordingHudUi(Integer reading) {
            this.reading = reading;
        }

        @Override
        public void openStaminaDialog(int settleMs) {
            events.add("open-hud");
            dialogOpenCount++;
        }

        @Override
        public Integer readStamina(int attempts, long retryMs) {
            events.add("read");
            ocrCount++;
            ocrAttempts = attempts;
            ocrRetryMs = retryMs;
            if (failRead) {
                throw new IllegalStateException("simulated OCR failure");
            }
            return reading;
        }

        @Override
        public void cleanup() {
            events.add("cleanup");
            cleanupCount++;
        }
    }

    private static final class SnapshotStaminaHelper extends StaminaHelper {
        private final RecordingHudUi ui;
        private final DiagnosticSnapshotStore snapshots;
        private boolean snapshotsEnabled = true;
        private int captureCount;
        private String snapshotResult;

        private SnapshotStaminaHelper(StaminaService persistence, Long accountKey,
                                      RecordingHudUi ui, Path workspace) {
            super(persistence, accountKey, ui);
            this.ui = ui;
            this.snapshots = new DiagnosticSnapshotStore(workspace);
        }

        @Override
        RawImageData captureStaminaDiagnosticFrame() {
            captureCount++;
            ui.events.add("snapshot");
            return RawImageData.capture(new byte[2 * 2 * 4], 2, 2, 32);
        }

        @Override
        DiagnosticSnapshotStore diagnosticSnapshotStore() {
            return snapshots;
        }

        @Override
        boolean diagnosticSnapshotsEnabled() {
            return snapshotsEnabled;
        }

        @Override
        String retainStaminaOcrFailureSnapshot() {
            snapshotResult = super.retainStaminaOcrFailureSnapshot();
            return snapshotResult;
        }
    }
}
