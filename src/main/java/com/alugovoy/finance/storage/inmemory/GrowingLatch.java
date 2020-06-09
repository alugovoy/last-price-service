package com.alugovoy.finance.storage.inmemory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Copies logic of {@link CountDownLatch} with addition to ability to increase the latch counter. When await is called
 * incrementation would be closed
 */
public class GrowingLatch {

    /**
     * Synchronization control For GrowingLatch. Uses AQS state to represent count.
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 2077141917217188285L;

        private volatile boolean incrementClosed = false;

        Sync() {
            setState(0);
        }

        int getCount() {
            return getState();
        }

        void closeIncrement(){
            incrementClosed = true;
        }

        boolean increment(){
            if (incrementClosed){
                return false;
            }
            for(;;){
                int c = getState();
                int nextC = c +1;
                if (compareAndSetState(c, nextC)){
                    return true;
                }
            }
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (; ; ) {
                int c = getState();
                if (c == 0) {
                    return false;
                }
                int nextc = c - 1;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0;
                }
            }
        }
    }

    private final Sync sync;

    public GrowingLatch() {
        this.sync = new Sync();
    }

    /**
     * Does same as {@link CountDownLatch#await()} but also stops incrementation
     */
    public void await() throws InterruptedException {
        sync.closeIncrement();
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Does same as {@link CountDownLatch#await(long, TimeUnit)} but also stops incrementation
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        sync.closeIncrement();
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Same as countDown from CountDownLatch
     */
    public void decrement() {
        sync.releaseShared(1);
    }

    /**
     * increments counter in the latch.
     * @return true if increment was successful. False would mean that incrementation has been stopped via
     * {{@link #await()}} method
     */
    public boolean increment() {
        return sync.increment();
    }

    /**
     * Same as {@link CountDownLatch#getCount()}
     */
    public long getCount() {
        return sync.getCount();
    }

    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }

}
