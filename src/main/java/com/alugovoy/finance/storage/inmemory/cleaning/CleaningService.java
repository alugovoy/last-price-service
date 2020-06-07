package com.alugovoy.finance.storage.inmemory.cleaning;

import com.alugovoy.finance.storage.inmemory.DataWrapper;
import com.lmax.disruptor.TimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public interface CleaningService {

    void submitCompleted(DataWrapper<?> wrapper, UUID modificationId);

    void submitCancel(DataWrapper<?> wrapper, UUID modificationId);

    void shutdown();

    void shutdown(long timeout, TimeUnit timeUnit) throws TimeoutException;
}
