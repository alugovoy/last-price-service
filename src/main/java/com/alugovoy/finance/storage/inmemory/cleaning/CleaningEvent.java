package com.alugovoy.finance.storage.inmemory.cleaning;

import com.alugovoy.finance.storage.inmemory.DataWrapper;
import java.util.UUID;
import lombok.Data;

@Data
public class CleaningEvent {

    public enum Action {
        CANCEL, COMPLETE
    }

    private DataWrapper<?> wrapper;
    private UUID modificationId;
    private Action action;
}
