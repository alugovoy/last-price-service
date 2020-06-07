package com.alugovoy.finance.storage.inmemory.cleaning;

import com.alugovoy.finance.storage.inmemory.DataWrapper;
import com.alugovoy.finance.storage.inmemory.cleaning.CleaningEvent.Action;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.val;

/**
 * Disruptor based implementation of cleaning service
 */
public class DisruptorCleaningService implements CleaningService {

    private static final int DEFAULT_SIZE = 1 << 10;
    private static final EventFactory<CleaningEvent> EVENT_FACTORY = CleaningEvent::new;

    private final Disruptor<CleaningEvent> disruptor;
    private final RingBuffer<CleaningEvent> ringBuffer;

    public DisruptorCleaningService() {
        disruptor = new Disruptor<>(EVENT_FACTORY, DEFAULT_SIZE, DaemonThreadFactory.INSTANCE,
            ProducerType.SINGLE, new SleepingWaitStrategy());
        disruptor.handleEventsWith(new CleanHandler());
        ringBuffer = disruptor.start();
    }

    @Override
    public void submitCompleted(final DataWrapper<?> wrapper, final UUID modificationId) {
        publishCleanEvent(wrapper, modificationId, Action.COMPLETE);
    }

    @Override
    public void submitCancel(final DataWrapper<?> wrapper, final UUID modificationId) {
        publishCleanEvent(wrapper, modificationId, Action.CANCEL);
    }

    private void publishCleanEvent(final DataWrapper<?> wrapper, final UUID modificationId, final Action action) {
        val seq = ringBuffer.next();
        try {
            val event = ringBuffer.get(seq);
            event.setAction(action);
            event.setModificationId(modificationId);
            event.setWrapper(wrapper);
        } finally {
            ringBuffer.publish(seq);
        }
    }

    @Override
    public void shutdown() {
        this.disruptor.shutdown();
    }

    @Override
    public void shutdown(final long timeout, final TimeUnit timeUnit) throws TimeoutException {
        this.disruptor.shutdown(timeout, timeUnit);
    }

    private static class CleanHandler implements EventHandler<CleaningEvent> {

        @Override
        public void onEvent(final CleaningEvent cleaningEvent, long l, boolean b) {
            val wrapper = cleaningEvent.getWrapper();
            val modificationId = cleaningEvent.getModificationId();
            switch (cleaningEvent.getAction()) {
                case CANCEL:
                    wrapper.updateCanceled(modificationId);
                case COMPLETE:
                    wrapper.updateCompleted(modificationId);
            }
            clearEvent(cleaningEvent);
        }

        private void clearEvent(final CleaningEvent event) {
            event.setWrapper(null);
            event.setModificationId(null);
            event.setAction(null);
        }
    }
}
