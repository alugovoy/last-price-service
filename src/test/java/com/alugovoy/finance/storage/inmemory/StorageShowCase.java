package com.alugovoy.finance.storage.inmemory;

import static org.junit.Assert.assertEquals;

import com.alugovoy.finance.storage.inmemory.utils.TestProducer;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StorageShowCase {

    private InMemoryStorage<PriceDetails> storage;
    private TestProducer<PriceDetails> producer;

    @Before
    public void initStorage() throws InterruptedException {
        storage = new InMemoryStorage<>(1 << 16);
        producer = new TestProducer<>(1 << 16, PriceDetails::new);
        producer.uploadBatch(storage.startBatch(), 1000);
        producer.uploadBatch(storage.startBatch(), 1000);
    }

    @Test
    public void testGetLatest() {
        val fails = new AtomicInteger(0);
        producer.loadedIds().parallelStream().forEach(id -> {
            if (storage.findLatest(id) == null) {
                fails.incrementAndGet();
            }
        });
        assertEquals(0, fails.get());
    }


    @After
    public void shutDown() {
        storage.shutdown();
    }

    private class PriceDetails {

        private final byte[] data = new byte[1024];
    }
}
