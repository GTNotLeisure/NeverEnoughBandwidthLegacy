package cn.ussshenzhou.notenoughbandwidth.codec;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * Compression backend for one direction pair of one connection.
 * Implementations may retain state between calls; buffers use their current position and limit.
 */
public interface PacketCodec extends Closeable {

    PacketCodecType type();

    /**
     * @return whether this backend needs {@code src} and {@code dst} to be direct buffers
     */
    boolean requiresDirectBuffers();

    /**
     * Worst case output size for {@code rawSize} input bytes, used to size the destination buffer.
     */
    int compressBound(int rawSize);

    void compress(ByteBuffer src, ByteBuffer dst);

    void decompress(ByteBuffer src, ByteBuffer dst);

    @Override
    void close();
}
