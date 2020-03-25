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
package org.thingsboard.integration.custom.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomClient2 {

    private final NioEventLoopGroup workGroup;

    private Channel clientChannel;
    private boolean sent = false;

    public CustomClient2(int port) {
        this.workGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(this.workGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new ByteArrayEncoder(), new ByteArrayDecoder(), new LineBasedFrameDecoder(1024));
                    socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<byte[]>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                            log.debug("Client received the message: {}", msg);
                            if (!sent) {
                                byte[] reportingRequest = new byte[]{
                                        0x33, 0x33, 0x33, 0x33,
                                        0x33, 0x35, 0x39, 0x37, 0x37, 0x31, 0x30, 0x34, 0x30, 0x32, 0x36, 0x38, 0x38, 0x38, 0x38, 0x30,
                                        0x33, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x30,
                                        0x09,
                                        0x00,
                                        0x00, 0x12,
                                        0x13, 0x06, 0x13, 0x19, 0x26, 0x29, 0x02, (byte) 0x99, 0x31, 0x00,
                                        0x01, 0x41, 0x22, 0x50, 0x01, 0x41, 0x22, 0x50};
                                clientChannel.writeAndFlush(reportingRequest);
                                sent = true;
                            }
                        }
                    });
                }
            });

            byte[] registrationRequest = new byte[]{
                    0x33, 0x33, 0x33, 0x33,
                    0x33, 0x35, 0x39, 0x37, 0x37, 0x31, 0x30, 0x34, 0x30, 0x32, 0x36, 0x38, 0x38, 0x38, 0x38, 0x30,
                    0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x31, 0x30,
                    0x01,
                    0x00,
                    0x00, 0x02,
                    0x00, 0x1f};

            clientChannel = bootstrap.connect("localhost", port).sync().channel();
            clientChannel.writeAndFlush(registrationRequest);
        } catch (Exception e) {
            log.error("Failed to init TCP client!", e);
            throw new RuntimeException();
        }
    }

    public void destroy() {
        try {
            clientChannel.close().sync();
        } catch (Exception e) {
            log.error("Failed to close the channel!", e);
        } finally {
            this.workGroup.shutdownGracefully();
        }
    }

}
