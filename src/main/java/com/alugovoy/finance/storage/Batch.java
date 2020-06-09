package com.alugovoy.finance.storage;

import java.util.Collection;

public interface Batch<T> {

    /**
     * Uploads chunk of price data
     *
     * @param chunk represent data to be uploaded
     * @throws IllegalStateException in case if batch is finished (completed or canceled) or when service was shutdown.
     */
    void upload(Collection<PriceData<T>> chunk) throws IllegalStateException;

    /**
     * Completes the batch. Which results in publishing of the data uploaded during this batch
     *
     * @throws IllegalStateException in case if batch was canceled or when service was shutdown
     */
    void complete() throws IllegalStateException, InterruptedException;

    /**
     * Cancels the batch. All data sent during batch is discarded
     *
     * @throws IllegalStateException in case if batch was completed or when service was shutdown
     */
    void cancel() throws IllegalStateException;
}
