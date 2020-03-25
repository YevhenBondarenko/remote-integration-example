package org.thingsboard.integration.custom.message;

import lombok.Data;

@Data
public class MsgData {
    private final DataStatus dataStatus;
    private final Unit dataUnit;
    private final double value;
}
