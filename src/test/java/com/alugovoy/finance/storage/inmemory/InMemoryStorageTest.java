package com.alugovoy.finance.storage.inmemory;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.alugovoy.finance.storage.PriceData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import lombok.val;
import org.junit.Test;

public class InMemoryStorageTest {

    private static final int SIZE = 100_000;
    private static final int CHUNK_SIZE = 1000;
    private static final Date FROM = toDate("2020-05-01");
    private static final Date TO = toDate("2020-06-01");

    @Test
    public void test() throws InterruptedException {
        val ids = IntStream.range(0, SIZE).mapToObj(n -> UUID.randomUUID().toString()).collect(toList());
        val storage = new InMemoryStorage<Byte[]>(SIZE);
        val batch = storage.startBatch();
        val data = new HashMap<String, PriceData<Byte[]>>(SIZE);
        val chunk = new ArrayList<PriceData<Byte[]>>(CHUNK_SIZE);
        for (val id : ids) {
            if (chunk.size() == CHUNK_SIZE) {
                batch.upload(chunk);
                chunk.clear();
                continue;
            }
            val item = buildRandom(id);
            chunk.add(item);
            data.put(id, item);
        }
        if (!chunk.isEmpty()) {
            batch.upload(chunk);
        }
        data.entrySet().parallelStream().forEach(e -> assertNull(storage.findLatest(e.getKey())));
        batch.complete();
        val failedCounter = new AtomicInteger();
        data.entrySet().parallelStream().forEach(e -> {
            if (e.getValue() != storage.findLatest(e.getKey())) {
                failedCounter.incrementAndGet();
                storage.dump(e.getKey(), System.out);
            }
        });
        storage.shutdown();
        assertEquals(0, failedCounter.get());
    }

    private static PriceData<Byte[]> buildRandom(String id) {
        return new PriceData<>(id, randomDate(), new Byte[128]);
    }

    private static Date randomDate() {
        return new Date(ThreadLocalRandom.current().nextLong(FROM.getTime(), TO.getTime()));
    }

    private static Date toDate(String date) {
        return new Date(TimeUnit.SECONDS.toMillis(Instant.parse(date + "T00:00:00Z").getEpochSecond()));
    }

}