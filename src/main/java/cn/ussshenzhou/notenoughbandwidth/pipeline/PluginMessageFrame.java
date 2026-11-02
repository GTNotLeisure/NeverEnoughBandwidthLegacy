package cn.ussshenzhou.notenoughbandwidth.pipeline;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.ussshenzhou.notenoughbandwidth.util.NebVarInt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;

public class PluginMessageFrame {

    public static final String CHANNEL = "NEB|Frame";
    public static final int MAX_PAYLOAD_BYTES = Short.MAX_VALUE - 1;
    public static final int MAX_FRAGMENT_BYTES = MAX_PAYLOAD_BYTES - 6;

    private static final byte[] CHANNEL_BYTES = CHANNEL.getBytes(StandardCharsets.UTF_8);

    public static int wireSize(int frameSize) {
        return wireSize(frameSize, 0);
    }

    public static int wireSize(int frameSize, int payloadId) {
        checkFrame(frameSize, payloadId);
        int bytes = 0;
        for (int offset = 0; offset < frameSize; offset += MAX_FRAGMENT_BYTES) {
            int fragmentSize = Math.min(MAX_FRAGMENT_BYTES, frameSize - offset);
            int bodySize = headerSize(payloadId) + NebVarInt.sizeOf(frameSize)
                + NebVarInt.sizeOf(offset)
                + fragmentSize;
            bytes += NebVarInt.sizeOf(bodySize) + bodySize;
        }
        return bytes;
    }

    public static ChannelFuture write(ChannelHandlerContext ctx, ByteBuf frame, int payloadId) {
        List<ByteBuf> packets = encode(ctx.alloc(), frame, payloadId);
        if (packets.size() == 1) {
            return ctx.write(packets.get(0))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
        ChannelPromise completion = ctx.newPromise();
        completion.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        ChannelPromiseAggregator aggregator = new ChannelPromiseAggregator(completion);
        ChannelPromise[] promises = new ChannelPromise[packets.size()];
        for (int i = 0; i < promises.length; i++) {
            promises[i] = ctx.newPromise();
        }
        aggregator.add(promises);
        int next = 0;
        try {
            while (next < packets.size()) {
                int index = next++;
                ctx.write(packets.get(index), promises[index]);
            }
        } catch (Throwable failure) {
            for (int i = next; i < packets.size(); i++) {
                packets.get(i)
                    .release();
            }
            completion.tryFailure(failure);
            throw failure;
        }
        return completion;
    }

    public static List<ByteBuf> encode(ByteBufAllocator allocator, ByteBuf frame, int payloadId) {
        int frameSize = frame.readableBytes();
        checkFrame(frameSize, payloadId);
        List<ByteBuf> packets = new ArrayList<>((frameSize + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES);
        try {
            for (int offset = 0; offset < frameSize; offset += MAX_FRAGMENT_BYTES) {
                int fragmentSize = Math.min(MAX_FRAGMENT_BYTES, frameSize - offset);
                int payloadSize = NebVarInt.sizeOf(frameSize) + NebVarInt.sizeOf(offset) + fragmentSize;
                ByteBuf header = allocator.buffer(headerSize(payloadId) + payloadSize - fragmentSize);
                CompositeByteBuf packet = null;
                boolean headerTransferred = false;
                try {
                    NebVarInt.write(header, payloadId);
                    NebVarInt.write(header, CHANNEL_BYTES.length);
                    header.writeBytes(CHANNEL_BYTES);
                    header.writeShort(payloadSize);
                    NebVarInt.write(header, frameSize);
                    NebVarInt.write(header, offset);
                    packet = allocator.compositeBuffer(2);
                    packet.addComponent(header);
                    headerTransferred = true;
                    packet.addComponent(
                        frame.slice(frame.readerIndex() + offset, fragmentSize)
                            .retain());
                    packet.writerIndex(header.readableBytes() + fragmentSize);
                    packets.add(packet);
                } catch (Throwable failure) {
                    if (packet != null) {
                        packet.release();
                    }
                    if (!headerTransferred) {
                        header.release();
                    }
                    throw failure;
                }
            }
            return packets;
        } catch (Throwable failure) {
            for (ByteBuf packet : packets) {
                packet.release();
            }
            throw failure;
        }
    }

    public static boolean matches(ByteBuf packet, int payloadId) {
        if (payloadId < 0) {
            return false;
        }
        NebVarInt.Cursor cursor = new NebVarInt.Cursor();
        cursor.at(packet.readerIndex());
        if (!NebVarInt.readAt(packet, cursor) || cursor.value != payloadId
            || !NebVarInt.readAt(packet, cursor)
            || cursor.value != CHANNEL_BYTES.length
            || packet.writerIndex() - cursor.index < CHANNEL_BYTES.length) {
            return false;
        }
        for (int i = 0; i < CHANNEL_BYTES.length; i++) {
            if (packet.getByte(cursor.index + i) != CHANNEL_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private static int headerSize(int payloadId) {
        return NebVarInt.sizeOf(payloadId) + NebVarInt.sizeOf(CHANNEL_BYTES.length) + CHANNEL_BYTES.length + 2;
    }

    private static void checkFrame(int frameSize, int payloadId) {
        if (frameSize <= 0 || frameSize > NebFrame.MAX_WIRE_BYTES || payloadId < 0) {
            throw new IllegalArgumentException("NEB: invalid plugin message frame size or packet id");
        }
    }

    private PluginMessageFrame() {}
}
