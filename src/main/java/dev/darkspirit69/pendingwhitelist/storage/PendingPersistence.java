package dev.darkspirit69.pendingwhitelist.storage;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Owns asynchronous persistence and lifecycle management for pending data
 * files.
 */
final class PendingPersistence {

    private final PendingFileStore fileStore;
    private final ExecutorService executor;
    private final Object saveLock = new Object();
    private List<PendingEntry> latestSnapshot = List.of();
    private long saveGeneration;
    private boolean saveQueued;

    PendingPersistence(PendingFileStore fileStore) {
        this.fileStore = fileStore;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PendingWhitelist-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    List<PendingEntry> load() throws IOException {
        return fileStore.load();
    }

    void scheduleSave(List<PendingEntry> snapshot) {
        synchronized (saveLock) {
            if (executor.isShutdown()) {
                return;
            }
            latestSnapshot = snapshot;
            saveGeneration++;
            if (saveQueued) {
                return;
            }
            saveQueued = true;
            try {
                executor.execute(this::saveLatestSnapshots);
            } catch (RuntimeException ex) {
                saveQueued = false;
                throw ex;
            }
        }
    }

    private void saveLatestSnapshots() {
        for (;;) {
            List<PendingEntry> snapshot;
            long generation;
            synchronized (saveLock) {
                snapshot = latestSnapshot;
                generation = saveGeneration;
            }
            save(snapshot);
            synchronized (saveLock) {
                if (generation == saveGeneration) {
                    saveQueued = false;
                    return;
                }
            }
        }
    }

    boolean execute(Runnable task) {
        if (executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException ex) {
            return false;
        }
    }

    void flush(List<PendingEntry> snapshot, String operation) {
        if (executor.isShutdown()) {
            return;
        }
        try {
            scheduleSave(snapshot);
            waitFor(executor.submit(() -> {
            }), operation);
        } catch (RejectedExecutionException ex) {
            DebugLog.warn("Pending storage was already shutting down while trying to " + operation + ".");
        }
    }

    void shutdown(List<PendingEntry> snapshot, String operation) {
        flush(snapshot, operation);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                DebugLog.warn("Timed out while waiting for pending storage writes to finish.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DebugLog.warn("Interrupted while waiting for pending storage writes to finish.");
        }
    }

    private void save(List<PendingEntry> snapshot) {
        DebugLog.debug("Writing pending snapshot: entries=" + snapshot.size());
        try {
            fileStore.save(snapshot);
        } catch (IOException ex) {
            DebugLog.error("Failed to save pending.json: " + ex.getMessage(), ex);
        }
    }

    private boolean waitFor(Future<?> future, String operation) {
        try {
            future.get();
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DebugLog.warn("Interrupted while trying to " + operation + ".");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            DebugLog.error("Failed to " + operation + ": "
                    + (cause == null ? "unknown error" : cause.getMessage()), cause);
        }
        return false;
    }
}
