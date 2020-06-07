package com.alugovoy.finance.storage.inmemory.benchmarks;

import com.alugovoy.finance.storage.PriceData;
import com.alugovoy.finance.storage.inmemory.DataWrapper;
import com.alugovoy.finance.storage.inmemory.DataWrapper.Modification;
import com.alugovoy.finance.storage.inmemory.Update;
import com.alugovoy.finance.storage.inmemory.Update.Status;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * <pre>
 * Benchmark                                                     (DELAY_BETWEEN_UPDATES)  (MAX_PARALLEL_UPDATES)  Mode  Cnt     Score      Error  Units
 * DataWrapperBenchmark.parallel                                                     100                       8  avgt    5   496.074 ±  817.981  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                      100                       8  avgt    5     8.587 ±    1.641  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithoutUpdates                             100                       8  avgt    5     9.122 ±    3.685  ns/op
 * DataWrapperBenchmark.parallel:updateInProgress                                    100                       8  avgt    5  3906.342 ± 6553.520  ns/op
 * DataWrapperBenchmark.parallel                                                      10                       8  avgt    5   162.518 ±  115.732  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                       10                       8  avgt    5     9.104 ±    2.651  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithoutUpdates                              10                       8  avgt    5     9.043 ±    3.955  ns/op
 * DataWrapperBenchmark.parallel:updateInProgress                                     10                       8  avgt    5  1236.657 ±  924.291  ns/op
 * DataWrapperBenchmark.parallel                                                       0                       8  avgt    5    15.758 ±    2.032  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                        0                       8  avgt    5    10.453 ±    0.823  ns/op
 * DataWrapperBenchmark.parallel:getLatestWithoutUpdates                               0                       8  avgt    5    10.554 ±    1.529  ns/op
 * DataWrapperBenchmark.parallel:updateInProgress                                      0                       8  avgt    5    52.489 ±   10.325  ns/op
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 1)
//@Measurement(iterations = 8)
public class DataWrapperBenchmark {

    private static final Comparator<PriceData<Object>> DEFAULT_SUCCESSOR_CHOICE = Comparator
        .comparing(PriceData::getAsOf);
    private static final Date FROM = toDate("2020-05-01");
    private static final Date TO = toDate("2020-06-01");

    private DataWrapper<Object> wrapper;
    private DataWrapper<Object> wrapperNoUpdates;

    public static void main(String[] args) throws RunnerException {

        Options opt = new OptionsBuilder()
            .include(DataWrapperBenchmark.class.getSimpleName())
//            .addProfiler("stack", "lines=10")
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Thread)
    public static class ModificationState {

        private static final AtomicInteger inProgressUpdates = new AtomicInteger();

        @Param({"8"})
        private int MAX_PARALLEL_UPDATES;
        @Param({"100", "10", "0"})
        private int DELAY_BETWEEN_UPDATES;

        private Modification<Object> modification;

        @Setup(Level.Invocation)
        public void setup() {
            if (inProgressUpdates.incrementAndGet() > MAX_PARALLEL_UPDATES) {
                inProgressUpdates.decrementAndGet();
                modification = null;
            } else {
                modification = buildRandomModification();
            }
        }

        @Setup(Level.Invocation)
        public void tearDown() throws InterruptedException {
            if (modification == null) {
                return;
            }
            Thread.sleep(DELAY_BETWEEN_UPDATES);
            ((Update.Status) modification.getStatus()).complete();
            inProgressUpdates.decrementAndGet();
        }
    }

    @Setup
    public void setup() {
        val initial = buildRandomModification();
        wrapper = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        wrapperNoUpdates = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        ((Update.Status) initial.getStatus()).complete();
    }

    private static Modification<Object> buildRandomModification() {
        return new Modification<>(UUID.randomUUID(), new Status(),
            new PriceData<>(UUID.randomUUID() + "", randomDate(), new Object()));
    }

    private static Date randomDate() {
        return new Date(ThreadLocalRandom.current().nextLong(FROM.getTime(), TO.getTime()));
    }

    private static Date toDate(String date) {
        return new Date(TimeUnit.SECONDS.toMillis(Instant.parse(date + "T00:00:00Z").getEpochSecond()));
    }


    @Group("parallel")
    @GroupThreads(3)
    @Benchmark
    public void getLatestWithUpdatesInParallel(Blackhole bh) {
        bh.consume(wrapper.getLatest());
    }

    @Group("parallel")
    @GroupThreads(4)
    @Benchmark
    public void getLatestWithoutUpdates(Blackhole bh) {
        bh.consume(wrapperNoUpdates.getLatest());
    }

    @Group("parallel")
    @GroupThreads(1)
    @Benchmark
    public void updateInProgress(ModificationState state) {
        if (state.modification == null) {
            return;
        }
        wrapper.updateInProgress(state.modification);
    }
}