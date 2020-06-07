package com.alugovoy.finance.storage.inmemory;

import com.alugovoy.finance.storage.inmemory.DataWrapper.ModificationStatus;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps details about ongoing update operation
 */
public class Update {

    private final Set<String> participants;
    final Status status;
    final UUID id;

    public Update(int initialCapacity) {
        this.participants = ConcurrentHashMap.newKeySet(initialCapacity);
        this.status = new Status();
        this.id = UUID.randomUUID();
    }

    public void addParticipant(String id) {
        participants.add(id);
    }

    public Iterable<String> participants() {
        return participants;
    }

    public void clear() {
        this.participants.clear();
    }

    void shutdown() {
        this.status.shutdown();
        participants.clear();
    }

    public static class Status implements ModificationStatus {

        private final AtomicInteger value;

        public Status() {
            this.value = new AtomicInteger(IN_PROGRESS);
        }

        public void complete() {
            if (value.compareAndSet(IN_PROGRESS, COMPLETED) || value.get() == COMPLETED) {
                return;
            }
            if (value.get() == SHUTDOWN) {
                throw new IllegalStateException("Shutdown is in progress");
            }
            throw new IllegalStateException("Cannot complete canceled update");
        }


        public void cancel() {
            if (value.compareAndSet(IN_PROGRESS, CANCELED) || value.get() == CANCELED) {
                return;
            }
            if (value.get() == SHUTDOWN) {
                throw new IllegalStateException("Shutdown is in progress");
            }
            throw new IllegalStateException("Cannot cancel completed update");
        }

        private void shutdown() {
            value.set(SHUTDOWN);
        }

        public int value() {
            return value.get();
        }
    }
}
