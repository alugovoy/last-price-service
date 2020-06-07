package com.alugovoy.finance.storage;

public interface BatchFactory<T> {

    /**
     * Creates batch operation
     *
     * @return created batch
     */
    Batch<T> startBatch();
}
