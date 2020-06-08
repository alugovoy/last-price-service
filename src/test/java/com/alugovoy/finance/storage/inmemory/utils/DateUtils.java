package com.alugovoy.finance.storage.inmemory.utils;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DateUtils {

    private static final Date FROM = toDate("2020-05-01");
    private static final Date TO = toDate("2020-06-01");

    public static Date randomDate() {
        return new Date(ThreadLocalRandom.current().nextLong(FROM.getTime(), TO.getTime()));
    }

    public static Date toDate(String date) {
        return new Date(TimeUnit.SECONDS.toMillis(Instant.parse(date + "T00:00:00Z").getEpochSecond()));
    }


}
