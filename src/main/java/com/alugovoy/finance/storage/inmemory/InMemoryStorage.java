package com.alugovoy.finance.storage.inmemory;

import static java.util.Comparator.comparing;

import com.alugovoy.finance.storage.Batch;
import com.alugovoy.finance.storage.BatchFactory;
import com.alugovoy.finance.storage.PriceData;
import com.alugovoy.finance.storage.Storage;
import com.alugovoy.finance.storage.inmemory.DataWrapper.Modification;
import com.alugovoy.finance.storage.inmemory.DataWrapper.ModificationStatus;
import com.alugovoy.finance.storage.inmemory.Update.Status;
import com.alugovoy.finance.storage.inmemory.cleaning.CleaningService;
import com.alugovoy.finance.storage.inmemory.cleaning.DisruptorCleaningService;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class InMemoryStorage<T> implements Storage<T>, BatchFactory<T> {

    private volatile boolean shutdownInProgress = false;
    private final ConcurrentMap<String, DataWrapper<T>> storage;
    private final ConcurrentMap<UUID, Update> updatesInProgress;
    private final CleaningService cleaningService;
    private final int initialCapacity;
    private final ExecutorService cleaningPool;

    public InMemoryStorage(int size) {
        this(size, new DisruptorCleaningService());
    }

    InMemoryStorage(int size, CleaningService cleaningService) {
        this.storage = new ConcurrentHashMap<>(size);
        this.updatesInProgress = new ConcurrentHashMap<>();
        this.initialCapacity = size;
        this.cleaningService = cleaningService;
        this.cleaningPool = Executors.newFixedThreadPool(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PriceData<T> findLatest(String id) {
        if (shutdownInProgress) {
            throw new IllegalStateException("Service is under shutting down");
        }
        val data = storage.get(id);
        return data == null ? null : data.getLatest();
    }

    /**
     * For debug purposes
     *
     * @param id instrument ID
     * @param printStream where to output details
     */
    void dump(String id, PrintStream printStream) {
        val data = storage.get(id);
        if (data == null) {
            printStream.println("null");
            return;
        }
        data.dumpState(printStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Batch<T> startBatch() {
        if (shutdownInProgress) {
            throw new IllegalStateException("Service is under shutting down");
        }
        val update = new Update(Math.max(storage.size(), initialCapacity));
        updatesInProgress.put(update.id, update);
        return new InMemoryBatch(update);
    }

    /**
     * Shuts down the service. Clears all data.
     */
    public void shutdown() {
        val start = System.nanoTime();
        log.info("Shutdown has started");
        this.shutdownInProgress = true;
        this.updatesInProgress.values().forEach(Update::shutdown);
        this.updatesInProgress.clear();
        this.cleaningService.shutdown();
        this.cleaningPool.shutdown();
        this.storage.values().forEach(DataWrapper::clear);
        this.storage.clear();
        log.info("Storage shutdown completed. Took " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
    }

    /**
     * Cleans existing update asynchronously. Main purpose is to remove uncleared modification data.
     */
    private void cleanUpdate(final Update update) {
        val status = update.status.value();
        cleaningPool.submit(() -> {
            if (log.isDebugEnabled()) {
                log.debug("Clean of " + update.id + " is started");
            }
            update.participants().forEach(participantId -> {
                val wrapper = storage.get(participantId);
                if (status == Status.COMPLETED) {
                    cleaningService.submitCompleted(wrapper, update.id);
                }
                if (status == Status.CANCELED) {
                    cleaningService.submitCancel(wrapper, update.id);
                }
            });
            update.clear();
            updatesInProgress.remove(update.id);
            if (log.isDebugEnabled()) {
                log.debug("Clean of " + update.id + " is finished");
            }
        });
    }

    private Comparator<PriceData<T>> getDefaultSuccessorComparator() {
        return comparing(PriceData::getAsOf);
    }

    /**
     * In memory implementation of the batch operation.
     */
    private class InMemoryBatch implements Batch<T> {

        private final Update update;

        private InMemoryBatch(Update update) {
            this.update = update;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void upload(Collection<PriceData<T>> chunk) {
            if (update.status.value() != ModificationStatus.IN_PROGRESS) {
                throw new IllegalStateException("Cannot upload for finished batch operation");
            }
            chunk.forEach(p -> {
                if (shutdownInProgress) {
                    throw new IllegalStateException("Service is under shutting down");
                }
                storage.compute(p.getId(), (id, wrapper) -> {
                    val modification = new Modification<>(update.id, update.status, p);
                    if (wrapper == null) {
                        return new DataWrapper<T>(modification, getDefaultSuccessorComparator());
                    }
                    wrapper.updateInProgress(modification);
                    return wrapper;
                });
                update.addParticipant(p.getId());
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void complete() {
            update.status.complete();
            cleanUpdate(update);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void cancel() {
            update.status.cancel();
            cleanUpdate(update);
        }
    }
}
