package org.thingsboard.integration.custom.message;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class MsgBody {
    private final List<MsgData> msgData;
    private final Date ts;
    private final int detectingInterval;
    private final int batteryVolume;
    private final int signalStrength;
}
