package org.thingsboard.integration.custom.message;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomIntegrationMsg {
    private final String id;
    private final String imei;
    private final String name;
    private final MsgType msgType;
    private final MsgBody msgBody;
}
