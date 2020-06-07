package com.alugovoy.finance.storage;

import java.util.Date;
import lombok.Data;

@Data
public class PriceData<T> {

    private final String id;
    private final Date asOf;
    private final T data;
}
