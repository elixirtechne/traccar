/*
 * Copyright 2018 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class RetranslatorProtocolDecoder extends BaseProtocolDecoder {

    public RetranslatorProtocolDecoder(RetranslatorProtocol protocol) {
        super(protocol);
    }

    private double readDouble(ChannelBuffer buf) {
        byte[] bytes = new byte[8];
        buf.readBytes(bytes);
        return ChannelBuffers.wrappedBuffer(ByteOrder.LITTLE_ENDIAN, bytes).readDouble();
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        if (channel != null) {
            channel.write(ChannelBuffers.wrappedBuffer(new byte[]{0x11}));
        }

        ChannelBuffer buf = (ChannelBuffer) msg;

        buf.readUnsignedInt(); // length

        int idLength = buf.indexOf(buf.readerIndex(), buf.writerIndex(), (byte) 0x00) - buf.readerIndex();
        String id = buf.readBytes(idLength).toString(StandardCharsets.US_ASCII);
        buf.readByte();
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, id);
        if (deviceSession == null) {
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());
        position.setTime(new Date(buf.readUnsignedInt() * 1000));

        buf.readUnsignedInt(); // bit flags

        while (buf.readable()) {

            buf.readUnsignedShort(); // block type
            int blockEnd = buf.readInt() + buf.readerIndex();
            buf.readUnsignedByte(); // security attribute
            int dataType = buf.readUnsignedByte();

            int nameLength = buf.indexOf(buf.readerIndex(), buf.writerIndex(), (byte) 0x00) - buf.readerIndex();
            String name = buf.readBytes(nameLength).toString(StandardCharsets.US_ASCII);
            buf.readByte();

            if (name.equals("posinfo")) {
                position.setValid(true);
                position.setLongitude(readDouble(buf));
                position.setLatitude(readDouble(buf));
                position.setAltitude(readDouble(buf));
                position.setSpeed(buf.readShort());
                position.setCourse(buf.readShort());
                position.set(Position.KEY_SATELLITES, buf.readByte());
            } else {
                switch (dataType) {
                    case 1:
                        int len = buf.indexOf(buf.readerIndex(), buf.writerIndex(), (byte) 0x00) - buf.readerIndex();
                        position.set(name, buf.readBytes(len).toString(StandardCharsets.US_ASCII));
                        buf.readByte();
                        break;
                    case 3:
                        position.set(name, buf.readInt());
                        break;
                    case 4:
                        position.set(name, readDouble(buf));
                        break;
                    case 5:
                        position.set(name, buf.readLong());
                        break;
                    default:
                        break;
                }
            }

            buf.readerIndex(blockEnd);

        }

        if (position.getLatitude() == 0 && position.getLongitude() == 0) {
            getLastLocation(position, position.getDeviceTime());
        }

        return position;
    }

}