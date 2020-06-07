package com.alugovoy.finance.storage.inmemory;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.alugovoy.finance.storage.PriceData;
import com.alugovoy.finance.storage.inmemory.DataWrapper.Modification;
import com.alugovoy.finance.storage.inmemory.Update.Status;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.val;
import org.junit.Test;

public class DataWrapperTest {

    private static final Comparator<PriceData<Object>> DEFAULT_SUCCESSOR_CHOICE = Comparator
        .comparing(PriceData::getAsOf);

    @Test
    public void testInitial() {
        val dataId = UUID.randomUUID();
        val initial = buildModification(dataId, "2020-05-01");
        val dataWrapper = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        assertNull(dataWrapper.getLatest());
        ((Update.Status) initial.getStatus()).complete();
        assertSame(initial.getData(), dataWrapper.getLatest());
    }

    @Test
    public void testCancel() {
        val dataId = UUID.randomUUID();
        val initial = buildModification(dataId, "2020-05-01");
        val dataWrapper = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        assertNull(dataWrapper.getLatest());
        ((Update.Status) initial.getStatus()).cancel();
        val second = buildModification(dataId, "2020-04-30");
        dataWrapper.updateInProgress(second);
        ((Update.Status) second.getStatus()).complete();
        assertSame(second.getData(), dataWrapper.getLatest());
    }

    @Test
    public void testSeveralUpdates() {
        val dataId = UUID.randomUUID();
        val initial = buildModification(dataId, "2020-05-01");
        val dataWrapper = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        val second = buildModification(dataId, "2020-05-02");
        dataWrapper.updateInProgress(second);
        val third = buildModification(dataId, "2020-05-03");
        dataWrapper.updateInProgress(third);
        assertNull(dataWrapper.getLatest());
        ((Update.Status) initial.getStatus()).complete();
        ((Update.Status) second.getStatus()).complete();
        ((Update.Status) third.getStatus()).complete();

        assertSame(third.getData(), dataWrapper.getLatest());
    }

    @Test
    public void testConcurrentUpdates() {
        val dataId = UUID.randomUUID();
        val initial = buildModification(dataId, "2020-04-01");
        ((Update.Status) initial.getStatus()).complete();
        val dataWrapper = new DataWrapper<>(initial, DEFAULT_SUCCESSOR_CHOICE);
        val nonCompleted = buildModification(dataId, "2020-05-07");
        val canceled = buildModification(dataId, "2020-05-08");

        dataWrapper.updateInProgress(nonCompleted);
        dataWrapper.updateInProgress(canceled);

        ((Update.Status) canceled.getStatus()).cancel();

        val modifications = IntStream.range(1, ForkJoinPool.commonPool().getParallelism())
            .mapToObj(i -> buildModification(dataId, String.format("2020-04-%02d", i)))
            .collect(toList());

        val latch = new CountDownLatch(modifications.size());
        val maxSoFar = modifications.stream().max(Comparator.comparing(m -> m.getData().getAsOf())).get();
        modifications.parallelStream()
            .forEach(m -> {
                dataWrapper.updateInProgress(m);
                ((Update.Status) m.getStatus()).complete();
                awaitForOthers(latch);
                assertSame(maxSoFar.getData(), dataWrapper.getLatest());
            });

        ((Update.Status) nonCompleted.getStatus()).complete();
        assertSame(nonCompleted.getData(), dataWrapper.getLatest());
    }

    private void awaitForOthers(CountDownLatch latch) {
        latch.countDown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Was interrupted during wait", e);
        }
    }

    private Modification<Object> buildModification(UUID dataId, String date) {
        return new Modification<>(UUID.randomUUID(), new Status(),
            new PriceData<>(dataId.toString(), toDate(date), new Object()));
    }

    private Date toDate(String date) {
        return new Date(TimeUnit.SECONDS.toMillis(Instant.parse(date + "T00:00:00Z").getEpochSecond()));
    }
}