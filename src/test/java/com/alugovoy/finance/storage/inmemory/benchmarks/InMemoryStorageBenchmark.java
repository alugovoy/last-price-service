package com.alugovoy.finance.storage.inmemory.benchmarks;

import static java.util.stream.Collectors.toList;

import com.alugovoy.finance.storage.Batch;
import com.alugovoy.finance.storage.PriceData;
import com.alugovoy.finance.storage.inmemory.DataWrapper.Modification;
import com.alugovoy.finance.storage.inmemory.InMemoryStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * <pre>
 * Benchmark                                                         (SIZE)  Mode  Cnt        Score        Error  Units
 * InMemoryStorageBenchmark.getLatestWithoutParallelUpdated         1000000  avgt    5      578.589 ±     14.930  ns/op
 * InMemoryStorageBenchmark.getLatestWithoutParallelUpdated          100000  avgt    5      445.980 ±     23.089  ns/op
 * InMemoryStorageBenchmark.getLatestWithoutParallelUpdated           10000  avgt    5      152.892 ±     89.405  ns/op
 * InMemoryStorageBenchmark.parallel                                1000000  avgt    5   253331.487 ± 103734.831  ns/op
 * InMemoryStorageBenchmark.parallel:getLatest                      1000000  avgt    5     1503.328 ±    319.491  ns/op
 * InMemoryStorageBenchmark.parallel:uploadBatch                    1000000  avgt    5  2016128.604 ± 828586.999  ns/op
 * InMemoryStorageBenchmark.parallel                                 100000  avgt    5   147512.463 ±  16621.843  ns/op
 * InMemoryStorageBenchmark.parallel:getLatest                       100000  avgt    5      662.027 ±     72.414  ns/op
 * InMemoryStorageBenchmark.parallel:uploadBatch                     100000  avgt    5  1175465.515 ± 132471.110  ns/op
 * InMemoryStorageBenchmark.parallel                                  10000  avgt    5    71885.219 ±   3232.486  ns/op
 * InMemoryStorageBenchmark.parallel:getLatest                        10000  avgt    5      269.708 ±     45.554  ns/op
 * InMemoryStorageBenchmark.parallel:uploadBatch                      10000  avgt    5   573193.798 ±  25923.420  ns/op
 * </pre>
 */

@Slf4j
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx4G"})
@Warmup(iterations = 1)
//@Measurement(iterations = 8)
public class InMemoryStorageBenchmark {

    private static final Date FROM = toDate("2020-05-01");
    private static final Date TO = toDate("2020-06-01");
    private static final int CHUNK_SIZE = 1000;

    private static InMemoryStorage<Byte[]> storage;
    private static InMemoryStorage<Byte[]> storageNoParallelUpdates;
    private static List<String> ids;

    @Param({"1000000", "100000", "10000"})
    private int SIZE;

    public static void main(String[] args) throws RunnerException {

        Options opt = new OptionsBuilder()
            .include(InMemoryStorageBenchmark.class.getSimpleName())
//            .addProfiler("stack", "lines=10")
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Thread)
    public static class ReadState {

        private String id;

        @Setup(Level.Invocation)
        public void determineId() {
            val index = ThreadLocalRandom.current().nextInt(0, ids.size() - 1);
            this.id = ids.get(index);
        }
    }

    @State(Scope.Thread)
    public static class WriteState {

        private static final AtomicInteger chunksLoaded = new AtomicInteger();
        private static volatile long batchStart;

        private static final AtomicReference<Batch<Byte[]>> batchRef = new AtomicReference<>(null);
        private Collection<PriceData<Byte[]>> chunk;

/*
                @Param({"3"})
                private int MAX_PARALLEL_UPDATES;
                @Param({"100", "10", "0"})
                private int DELAY_BETWEEN_UPDATES;
*/


        private Modification<Object> modification;


        @Setup(Level.Invocation)
        public void setup() {
            if (batchRef.get() == null) {
                val newBatch = storage.startBatch();
                if (!batchRef.compareAndSet(null, newBatch)) {
                    newBatch.cancel();
                } else {
                    batchStart = System.nanoTime();
                }
            }
            chunk = IntStream.range(0, CHUNK_SIZE)
                .map(i -> ThreadLocalRandom.current()
                    .nextInt(ids.size() - 1)).mapToObj(ind -> buildRandom(ids.get(ind))).collect(toList());
        }

        public Batch<Byte[]> getBatch() {
            return batchRef.get();
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            chunksLoaded.incrementAndGet();
            int loaded;
            while ((loaded = chunksLoaded.get()) > 1.1 * ids.size() / CHUNK_SIZE) {
                if (chunksLoaded.compareAndSet(loaded, 0)) {
                    Batch<Byte[]> b = batchRef.get();
                    b.complete();
                    val newBatch = storage.startBatch();
                    if (!batchRef.compareAndSet(b, newBatch)) {
                        newBatch.cancel();
                    } else {
                        val tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStart);
                        log.debug("Full batch load took " + tookMillis + "ms");
                        batchStart = System.nanoTime();
                    }
                }
            }
        }
    }

    @Setup
    public void setup() {
        ids = IntStream.range(0, SIZE).mapToObj(n -> UUID.randomUUID().toString()).collect(toList());
        storage = new InMemoryStorage<>(SIZE);
        storageNoParallelUpdates = new InMemoryStorage<>(SIZE);
        val batch = storage.startBatch();
        val batch2 = storageNoParallelUpdates.startBatch();
        List<PriceData<Byte[]>> chunk = new ArrayList<>(CHUNK_SIZE);
        for (val id : ids) {
            if (chunk.size() == CHUNK_SIZE) {
                batch.upload(chunk);
                batch2.upload(chunk);
                chunk.clear();
                continue;
            }
            chunk.add(buildRandom(id));
        }
        if (!chunk.isEmpty()) {
            batch.upload(chunk);
            batch2.upload(chunk);
        }
        batch.complete();
        batch2.complete();
    }

    @TearDown
    public void tearDown() {
        storage.shutdown();
        storageNoParallelUpdates.shutdown();
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


    @Benchmark
    public void getLatestWithoutParallelUpdated(Blackhole bh, ReadState state) {
        bh.consume(storageNoParallelUpdates.findLatest(state.id));
    }

    @Group("parallel")
    @GroupThreads(7)
    @Benchmark
    public void getLatest(Blackhole bh, ReadState state) {
        bh.consume(storage.findLatest(state.id));
    }

    @Group("parallel")
    @GroupThreads(1)
    @Benchmark
    public void uploadBatch(WriteState state) {
        try {
            state.getBatch().upload(state.chunk);
        } catch (IllegalStateException e) {
            //ignore
        }
    }

}