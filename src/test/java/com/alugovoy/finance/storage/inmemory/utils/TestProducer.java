package com.alugovoy.finance.storage.inmemory.utils;

import static java.util.stream.Collectors.toList;

import com.alugovoy.finance.storage.Batch;
import com.alugovoy.finance.storage.PriceData;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.val;

public class TestProducer<T> {

    private static final Date FROM = toDate("2020-05-01");
    private static final Date TO = toDate("2020-06-01");

    private final List<String> ids;
    private final Set<String> loadedIds;
    private final Supplier<T> contentSupplier;

    public TestProducer(int size, Supplier<T> contentSupplier) {
        this.ids = IntStream.range(0, size).mapToObj(n -> UUID.randomUUID().toString()).collect(toList());
        this.contentSupplier = contentSupplier;
        this.loadedIds = ConcurrentHashMap.newKeySet(size);
    }

    public void uploadBatch(Batch<T> batch, int chunkSize) {

        IntStream.range(1, ids.size() / chunkSize)
            .parallel()
            .forEach(x -> {
                val chunk = randomChunk(chunkSize);
                chunk.forEach(d -> loadedIds.add(d.getId()));
                batch.upload(chunk);
            });
        batch.complete();
    }

    private List<PriceData<T>> randomChunk(int chunkSize) {
        return IntStream.range(0, chunkSize)
            .map(i -> ThreadLocalRandom.current().nextInt(ids.size() - 1))
            .mapToObj(ind -> buildRandom(ids.get(ind)))
            .collect(toList());
    }

    public Collection<String> loadedIds() {
        return loadedIds;
    }

    private PriceData<T> buildRandom(String id) {
        return new PriceData<>(id, randomDate(), contentSupplier.get());
    }

    private static Date randomDate() {
        return new Date(ThreadLocalRandom.current().nextLong(FROM.getTime(), TO.getTime()));
    }

    private static Date toDate(String date) {
        return new Date(TimeUnit.SECONDS.toMillis(Instant.parse(date + "T00:00:00Z").getEpochSecond()));
    }
}
