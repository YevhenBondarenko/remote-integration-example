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
package org.thingsboard.integration.custom.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.integration.custom.client.CustomClient;
import org.thingsboard.integration.custom.client.CustomClient2;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.thingsboard.integration.custom.util.Converter.BcdToString;
import static org.thingsboard.integration.custom.util.Converter.hexStringToByteArray;

@Slf4j
public class CustomIntegration extends AbstractIntegration<CustomIntegrationMsg> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int bindPort = 5555;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workGroup;
    private Channel serverChannel;
    private CustomClient client;
    private CustomClient2 client2;
    private static final String NULL_DEVICE_IMEI = "0000000000000000";

    private static final byte[] firstResponsePart = new byte[]{0x2B, 0x54, 0x4F, 0x50, 0x53, 0x41, 0x49, 0x4C};
    private static final SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmss");

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        JsonNode configuration = mapper.readTree(params.getConfiguration().getConfiguration().get("configuration").asText());
        try {
            bossGroup = new NioEventLoopGroup();
            workGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new ByteArrayEncoder(), new ByteArrayDecoder(), new LineBasedFrameDecoder(1024));
                    socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<byte[]>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                            CustomIntegrationMsg message = convertMsg(msg);
                            log.debug("Server received the message: {}", message);

                            if (!message.getImei().equals(NULL_DEVICE_IMEI)) {
                                if (message.getMsgType() == MsgType.REGISTRATION) {
                                    log.info("[{}] Device is connected.", message.getImei());
                                } else {
                                    process(message);
                                }
                                ctx.writeAndFlush(createResponse());
                            } else {
                                log.info("ALL DEVICES CONNECTED.");
                            }
                        }
                    });
                }
            });
            int port = getBindPort(configuration);
            serverChannel = bootstrap.bind(port).sync().channel();

            client = new CustomClient(port);
            client2 = new CustomClient2(port);
        } catch (Exception e) {
            log.error("Failed to init TCP server!", e);
            throw new RuntimeException();
        }
    }

    protected String getUplinkContentType() {
        return "TEXT";
    }

    @Override
    public void destroy() {
        client.destroy();
        client2.destroy();
        try {
            serverChannel.close().sync();
        } catch (Exception e) {
            log.error("Failed to close the channel!", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }

    @Override
    public void process(CustomIntegrationMsg customIntegrationMsg) {
        if (!this.configuration.isEnabled()) {
            log.warn("Integration is disabled.");
            return;
        }
        String status = "OK";
        Exception exception = null;
        try {
            doProcess(customIntegrationMsg);
            integrationStatistics.incMessagesProcessed();
        } catch (Exception e) {
            log.debug("Failed to apply data converter function: {}", e.getMessage(), e);
            exception = e;
            status = "ERROR";
        }
        if (status.equals("ERROR")) {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getUplinkContentType(), mapper.writeValueAsString(customIntegrationMsg), status, exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message!", e);
            }
        }
    }

    private void doProcess(CustomIntegrationMsg msg) throws Exception {
        byte[] data = mapper.writeValueAsBytes(msg);
        Map<String, String> metadataMap = new HashMap<>(metadataTemplate.getKvMap());
        List<UplinkData> uplinkDataList = convertToUplinkDataList(context, data, new UplinkMetaData(getUplinkContentType(), metadataMap));
        if (uplinkDataList != null && !uplinkDataList.isEmpty()) {
            for (UplinkData uplinkData : uplinkDataList) {
                UplinkData uplinkDataResult = UplinkData.builder()
                        .deviceName(uplinkData.getDeviceName())
                        .deviceType(uplinkData.getDeviceType())
                        .telemetry(uplinkData.getTelemetry())
                        .attributesUpdate(uplinkData.getAttributesUpdate())
                        .customerName(uplinkData.getCustomerName())
                        .build();
                processUplinkData(context, uplinkDataResult);
            }
        }
    }

    private int getBindPort(JsonNode configuration) {
        int port;
        if (configuration.has("port")) {
            port = configuration.get("port").asInt();
        } else {
            log.warn("Failed to find [port] field in integration config, default value [{}] is used!", bindPort);
            port = bindPort;
        }
        return port;
    }

    private CustomIntegrationMsg convertMsg(byte[] data) {
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

    private byte[] createResponse() {
        String time = format.format(new Date());
        byte[] hexTime = hexStringToByteArray(time);
        byte[] response = new byte[firstResponsePart.length + hexTime.length];
        System.arraycopy(firstResponsePart, 0, response, 0, firstResponsePart.length);
        System.arraycopy(hexTime, 0, response, firstResponsePart.length, hexTime.length);
        return response;
    }

}
