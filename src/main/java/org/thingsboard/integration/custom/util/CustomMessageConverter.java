/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.integration.custom.util;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.integration.custom.message.CustomIntegrationMsg;
import org.thingsboard.integration.custom.message.DataStatus;
import org.thingsboard.integration.custom.message.MsgBody;
import org.thingsboard.integration.custom.message.MsgData;
import org.thingsboard.integration.custom.message.MsgType;
import org.thingsboard.integration.custom.message.Unit;

import java.nio.ByteBuffer;
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

        ByteBuffer wrapped = ByteBuffer.wrap(Arrays.copyOfRange(data, 38, 40));
        int bodyLength = wrapped.getShort();

        byte[] body = Arrays.copyOfRange(data, 40, data.length);
        int currentBodyLength = body.length;


        if (bodyLength != currentBodyLength) {
            log.warn("Expected body length: {}, but current body length: {}", bodyLength, currentBodyLength);

            int newBodyLength = currentBodyLength - (currentBodyLength - 10) % 4;
            body = Arrays.copyOfRange(body, 0, newBodyLength);
        }

        return new CustomIntegrationMsg(id, imei, name, msgType, convertMsgBody(body));
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

        return new MsgBody(ts, detectingInterval, batteryVolume, signalStrength, msgDataList);
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


    public static void main(String[] args) {

        System.out.println(new String(new byte[]{65, 84, 43, 67, 73, 80, 83, 72, 85, 84, 13}));
    }
}
