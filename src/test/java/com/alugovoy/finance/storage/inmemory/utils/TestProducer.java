package com.alugovoy.finance.storage.inmemory.utils;

import static com.alugovoy.finance.storage.inmemory.utils.DateUtils.randomDate;
import static java.util.stream.Collectors.toList;

import com.alugovoy.finance.storage.Batch;
import com.alugovoy.finance.storage.PriceData;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.val;

public class TestProducer<T> {

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
                val chunk = nextChunk(chunkSize);
                chunk.forEach(d -> loadedIds.add(d.getId()));
                batch.upload(chunk);
            });
        batch.complete();
    }

    public List<PriceData<T>> nextChunk(int chunkSize) {
        return IntStream.range(0, chunkSize)
            .map(i -> ThreadLocalRandom.current().nextInt(ids.size() - 1))
            .mapToObj(ind -> buildRandom(ids.get(ind)))
            .collect(toList());
    }

    public String randomId() {
        val i = ThreadLocalRandom.current().nextInt(ids.size() - 1);
        return ids.get(i);
    }

    public int size() {
        return ids.size();
    }

    public Collection<String> loadedIds() {
        return loadedIds;
    }

    private PriceData<T> buildRandom(String id) {
        return new PriceData<>(id, randomDate(), contentSupplier.get());
    }

}
