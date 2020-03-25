package org.thingsboard.integration.custom.message;

public enum MsgType {
    REGISTRATION(0x01),REPORTING(0x09);

    private int value;

    MsgType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MsgType of(byte value) {
        for (MsgType msgType : MsgType.values()) {
            if (msgType.getValue() == value) {
                return msgType;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
