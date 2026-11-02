package cn.ussshenzhou.notenoughbandwidth.pipeline;

import cn.ussshenzhou.notenoughbandwidth.util.NebVarInt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.CorruptedFrameException;

public class PluginMessageAssembler {

    private ByteBuf pending;
    private int expectedSize;
    private int wireBytes;

    public DecodedFrame accept(ByteBufAllocator allocator, ByteBuf packet, int payloadId) {
        try {
            if (!PluginMessageFrame.matches(packet, payloadId)) {
                throw new CorruptedFrameException("NEB: unexpected plugin message channel");
            }
            ByteBuf view = packet.duplicate();
            NebVarInt.read(view);
            int channelLength = NebVarInt.read(view);
            view.skipBytes(channelLength);
            int payloadSize = view.readUnsignedShort();
            if (payloadSize > PluginMessageFrame.MAX_PAYLOAD_BYTES || payloadSize != view.readableBytes()) {
                throw new CorruptedFrameException("NEB: invalid plugin message payload size");
            }
            int total = NebVarInt.read(view);
            int offset = NebVarInt.read(view);
            int size = view.readableBytes();
            if (total <= 0 || total > NebFrame.MAX_WIRE_BYTES
                || offset < 0
                || offset >= total
                || size != Math.min(PluginMessageFrame.MAX_FRAGMENT_BYTES, total - offset)) {
                throw new CorruptedFrameException("NEB: invalid plugin message fragment bounds");
            }
            if (pending == null) {
                if (offset != 0) {
                    throw new CorruptedFrameException("NEB: missing initial plugin message fragment");
                }
                if (size == total) {
                    return new DecodedFrame(view.retain(), wireSize(packet));
                }
                pending = allocator.directBuffer(total, total);
                expectedSize = total;
            } else if (total != expectedSize || offset != pending.writerIndex()) {
                throw new CorruptedFrameException("NEB: out of order plugin message fragment");
            }
            pending.writeBytes(view);
            wireBytes += wireSize(packet);
            if (pending.writerIndex() != expectedSize) {
                return null;
            }
            DecodedFrame result = new DecodedFrame(pending, wireBytes);
            pending = null;
            expectedSize = 0;
            wireBytes = 0;
            return result;
        } catch (Throwable failure) {
            clear();
            throw failure;
        }
    }

    public void clear() {
        if (pending != null) {
            pending.release();
            pending = null;
        }
        expectedSize = 0;
        wireBytes = 0;
    }

    private static int wireSize(ByteBuf packet) {
        return packet.readableBytes() + NebVarInt.sizeOf(packet.readableBytes());
    }

    public record DecodedFrame(ByteBuf payload, int wireBytes) {}
}
