package com.alugovoy.finance.storage;

public interface Storage<T> {

    /**
     * Returns latest published price information
     *
     * @return last price data that is available. If there are no price details for this ID then null will be returned.
     */
    PriceData<T> findLatest(String id);
}
