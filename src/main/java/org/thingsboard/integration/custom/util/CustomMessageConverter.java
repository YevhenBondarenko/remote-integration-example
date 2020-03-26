package org.thingsboard.integration.custom.util;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.integration.custom.message.CustomIntegrationMsg;
import org.thingsboard.integration.custom.message.DataStatus;
import org.thingsboard.integration.custom.message.MsgBody;
import org.thingsboard.integration.custom.message.MsgData;
import org.thingsboard.integration.custom.message.MsgType;
import org.thingsboard.integration.custom.message.Unit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.thingsboard.integration.custom.util.ByteConverter.BcdToString;

@Slf4j
public class CustomMessageConverter {

    public CustomIntegrationMsg convertMsg(byte[] data) {
        String id = new String(Arrays.copyOfRange(data, 0, 4));
        String imei = new String(Arrays.copyOfRange(data, 4, 20));
        String name = new String(Arrays.copyOfRange(data, 20, 36));
        MsgType msgType = MsgType.of(data[36]);

        if (msgType == MsgType.REGISTRATION) {
            return new CustomIntegrationMsg(id, imei, name, msgType, null);
        }

        return new CustomIntegrationMsg(id, imei, name, msgType, convertMsgBody(Arrays.copyOfRange(data, 40, data.length)));
    }

    private MsgBody convertMsgBody(byte[] msgBody) {
        String timeStr = BcdToString(Arrays.copyOfRange(msgBody, 0, 6));
        Date ts;
        try {
            ts = new SimpleDateFormat("yyMMddHHmmss").parse(timeStr);
        } catch (ParseException e) {
            log.error("Failed to parse date: [{}]", timeStr);
            throw new IllegalArgumentException("Failed to parse date.", e);
        }
        int detectingInterval = msgBody[6] & 0xff;
        int batteryVolume = Integer.parseInt(BcdToString(msgBody[7]));
        int signalStrength = Integer.parseInt(BcdToString(msgBody[8]));

        String[] dataGroups = BcdToString(Arrays.copyOfRange(msgBody, 10, msgBody.length)).split("(?<=\\G........)");
        List<MsgData> msgDataList = new ArrayList<>();

        for (String dataGroup : dataGroups) {
            msgDataList.add(convertData(dataGroup));
        }

        return new MsgBody(msgDataList, ts, detectingInterval, batteryVolume, signalStrength);
    }

    private MsgData convertData(String data) {
        String[] dataDigits = data.split("");
        Unit unit = Unit.of(dataDigits[1]);
        DataStatus dataStatus;
        String dataStatusStr = dataDigits[0];

        if (unit == Unit.ANGLE) {
            switch (dataStatusStr) {
                case "0":
                    dataStatus = DataStatus.NORMAL;
                    break;
                case "1":
                    dataStatus = DataStatus.COLLIDE;
                    break;
                case "2":
                    dataStatus = DataStatus.CERTAIN_ANGLE;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown data status: " + dataStatusStr);
            }
        } else {
            switch (dataStatusStr) {
                case "0":
                    dataStatus = DataStatus.NORMAL;
                    break;
                case "1":
                    dataStatus = DataStatus.CRITICAL_LOW;
                    break;
                case "2":
                    dataStatus = DataStatus.CRITICAL_HIGH;
                    break;
                case "3":
                    dataStatus = DataStatus.DEVICE_PROBLEM;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown data status: " + dataStatusStr);
            }
        }


        int locationOfDecimalPoint = Integer.parseInt(dataDigits[2]);
        StringBuilder valueBuilder = new StringBuilder();

        if (unit == Unit.TEMPERATURE) {
            if (dataDigits[3].equals("1")) {
                valueBuilder.append("-");
            }
        } else {
            valueBuilder.append(dataDigits[3]);
        }

        valueBuilder.append(dataDigits[4]);
        valueBuilder.append(dataDigits[5]);
        valueBuilder.append(dataDigits[6]);
        valueBuilder.append(dataDigits[7]);

        double value = Double.parseDouble(valueBuilder.toString()) / Math.pow(10, locationOfDecimalPoint);

        return new MsgData(dataStatus, unit, value);
    }

}
