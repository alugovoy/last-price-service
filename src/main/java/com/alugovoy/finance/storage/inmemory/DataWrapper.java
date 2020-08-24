package com.alugovoy.finance.storage.inmemory;

import com.alugovoy.finance.storage.PriceData;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.val;

/**
 * Container which returns the latest price details. It keeps the latest data available as well as in progress
 * modifications that can override current published information. On read it goes through existing modification to check
 * if any was completed since last read.
 */
public class DataWrapper<T> {

    private final AtomicReference<PriceData<T>> data;

    private final ConcurrentMap<UUID, Modification<T>> modifications;
    private final Comparator<PriceData<T>> successorChooser;

    public DataWrapper(Modification<T> modification, Comparator<PriceData<T>> successorChooser) {
        this.data = new AtomicReference<>(null);
        this.modifications = new ConcurrentHashMap<>();
        this.modifications.put(modification.id, modification);
        this.successorChooser = successorChooser;
    }

    public void updateInProgress(Modification<T> modification) {
        //Add modification only if it can replace current data.
        if (this.data.get() != null && successorChooser.compare(modification.data, this.data.get()) < 0) {
            return;
        }
        modifications.merge(modification.id, modification,
            (prev, next) -> successorChooser.compare(prev.data, next.data) > 0 ? prev : next);
    }

    public void updateCanceled(UUID updateId) {
        modifications.remove(updateId);
    }

    public void updateCompleted(UUID updateId) {
        if (modifications.containsKey(updateId)) {
            getLatest();
        }
    }

    /**
     * Returns latest published price details.
     *
     * @return price data that was published and is maximum according to #successorChooser strategy.
     */
    public PriceData<T> getLatest() {
        if (modifications.isEmpty()) {
            return data.get();
        }
        for (val modification : modifications.values()) {
            if (modification.status.value() == ModificationStatus.CANCELED) {
                modifications.remove(modification.id);
                continue;
            }

            if (modification.status.value() != ModificationStatus.COMPLETED) {
                if (data.get() != null && successorChooser.compare(data.get(), modification.getData()) > 0) {
                    modifications.remove(modification.id);
                }
                continue;
            }

            data.accumulateAndGet(modification.data,
                (prev, next) -> prev == null ? next : successorChooser.compare(prev, next) > 0 ? prev : next);

            modifications.remove(modification.id, modification);
        }
        return data.get();
    }

    void dumpState(PrintStream printStream) {
        printStream.println("data: " + data.get());
        printStream.println("modifications: " + modifications);
    }

    void clear() {
        modifications.clear();
    }

    @Data
    public static class Modification<T> {

        private final UUID id;
        private final ModificationStatus status;
        private final PriceData<T> data;
    }

    public interface ModificationStatus {

        int IN_PROGRESS = 0;
        int COMPLETED = 1;
        int CANCELED = 2;
        int SHUTDOWN = -1;

        int value();
    }
}
