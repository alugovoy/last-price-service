package com.alugovoy.finance.storage.inmemory;

import com.alugovoy.finance.storage.PriceData;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import lombok.Data;
import lombok.val;

/**
 * Container which returns the latest price details. It keeps the latest data available as well as in progress
 * modifications that can override current published information. On read it goes through existing modification to check
 * if any was completed since last read.
 */
public class DataWrapper<T> {

    private final AtomicReference<PriceData<T>> data;

    private final AtomicReferenceArray<Modification<T>> modifications;
    private final AtomicInteger modificationsSize;
    private final AtomicInteger version;
    private final Comparator<PriceData<T>> successorChooser;

    public DataWrapper(Modification<T> modification, Comparator<PriceData<T>> successorChooser) {
        this.data = new AtomicReference<>(null);
        this.modifications = new AtomicReferenceArray<>(100);
        this.modifications.set(0, modification);
        this.modificationsSize = new AtomicInteger(1);
        this.successorChooser = successorChooser;
        this.version = new AtomicInteger(0);
    }

    public void updateInProgress(Modification<T> modification) {
        //Add modification only if it can replace current data.
        if (this.data.get() != null && successorChooser.compare(modification.data, this.data.get()) < 0) {
            return;
        }
        version.incrementAndGet();
        modificationsSize.incrementAndGet();
        while (true) {
            for (int i = 0; i < modifications.length(); i++) {
                //TODO merge modifications with same batch ID
                if (modifications.compareAndSet(i, null, modification)) {
                    return;
                }
            }
        }
    }

    public void updateCanceled(UUID updateId) {
        int stillToVisit = modificationsSize.get();
        if (stillToVisit == 0) {
            return;
        }
        for (int i = 0; i < modifications.length(); i++) {
            if (stillToVisit == 0) {
                break;
            }
            val modification = modifications.get(i);
            if (modification == null) {
                continue;
            }
            stillToVisit--;
            if (modification.id == updateId) {
                if (modifications.compareAndSet(i, modification, null)) {
                    modificationsSize.decrementAndGet();
                }
            }
        }
    }

    public void updateCompleted(UUID updateId) {
        int stillToVisit = modificationsSize.get();
        if (stillToVisit == 0) {
            return;
        }

        for (int i = 0; i < modifications.length(); i++) {
            if (stillToVisit <= 0) {
                break;
            }
            val modification = modifications.get(i);
            if (modification == null) {
                continue;
            }
            stillToVisit--;
            if (modification.id == updateId) {
                getLatest();
                return;
            }
        }
    }

    /**
     * Returns latest published price details.
     *
     * @return price data that was published and is maximum according to #successorChooser strategy.
     */
    public PriceData<T> getLatest() {
        if (modificationsSize.get() == 0) {
            return data.get();
        }
        int expectedVersion;
        int stillToVisit;
        do {
            expectedVersion = version.get();
            stillToVisit = modificationsSize.get();
            for (int i = 0; i < modifications.length(); i++) {
                if (stillToVisit == 0) {
                    break;
                }
                val modification = modifications.get(i);
                if (modification == null) {
                    continue;
                }
                stillToVisit--;
                if (modification.status.value() == ModificationStatus.CANCELED) {
                    if (modifications.compareAndSet(i, modification, null)) {
                        modificationsSize.decrementAndGet();
                    }
                    continue;
                }

                if (modification.status.value() != ModificationStatus.COMPLETED) {
                    continue;
                }

                data.accumulateAndGet(modification.data,
                    (prev, next) -> prev == null ? next : successorChooser.compare(prev, next) > 0 ? prev : next);

                version.incrementAndGet();
                if (modifications.compareAndSet(i, modification, null)) {
                    modificationsSize.decrementAndGet();
                    expectedVersion++;
                } else {
                    version.decrementAndGet();
                }
            }
            //means somebody has made some changes in parallel. Need to
        } while (expectedVersion != version.get());
        return data.get();
    }

    void dumpState(PrintStream printStream) {
        printStream.println("data: " + data.get());
        printStream.println("modifications: " + modifications);
    }

    void clear() {
        for (int i = 0; i < modifications.length(); i++) {
            modifications.set(i, null);
        }
        modificationsSize.set(0);
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
