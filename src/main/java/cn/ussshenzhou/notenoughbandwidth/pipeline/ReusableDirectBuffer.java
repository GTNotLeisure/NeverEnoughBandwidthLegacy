package cn.ussshenzhou.notenoughbandwidth.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Keeps one direct buffer for a connection instead of allocating one for every frame.
 * <p>
 * Minecraft 1.7.10 ships Netty 4.0.10. Its allocator is unpooled, and it frees direct memory through
 * {@code sun.misc.Cleaner}, which no longer exists since Java 9. There a released direct buffer keeps its memory until
 * the garbage collector finds it, so a direct buffer per frame piles up native memory between collections.
 * <p>
 * Not thread safe: use it from the channel's event loop only.
 */
final class ReusableDirectBuffer {

    private static final int MIN_CAPACITY = 8192;

    /** Larger buffers are released after use, so one oversized frame does not hold its memory for the whole session. */
    private static final int MAX_RETAINED_CAPACITY = 1 << 20;

    private ByteBuf spare;
    private boolean closed;

    /**
     * @return an empty direct buffer with room for {@code minCapacity} bytes. The caller owns it until {@link #give}.
     */
    ByteBuf take(ByteBufAllocator allocator, int minCapacity) {
        ByteBuf buffer = spare;
        spare = null;
        if (buffer != null) {
            if (buffer.capacity() >= minCapacity) {
                return buffer.clear();
            }
            buffer.release();
        }
        return allocator.directBuffer(capacityFor(minCapacity));
    }

    /** Takes back a buffer from {@link #take}. The caller must not use it afterwards. */
    void give(ByteBuf buffer) {
        if (buffer == null) {
            return;
        }
        // A buffer something else still references cannot be handed out again.
        if (!closed && spare == null && buffer.refCnt() == 1 && buffer.capacity() <= MAX_RETAINED_CAPACITY) {
            spare = buffer;
        } else {
            buffer.release();
        }
    }

    /** Releases the kept buffer. Buffers given back later are released at once. */
    void close() {
        closed = true;
        if (spare != null) {
            spare.release();
            spare = null;
        }
    }

    private static int capacityFor(int minCapacity) {
        if (minCapacity <= MIN_CAPACITY) {
            return MIN_CAPACITY;
        }
        if (minCapacity > MAX_RETAINED_CAPACITY) {
            return minCapacity;
        }
        // Round up so frames that grow a little at a time do not each need a new buffer.
        return Integer.highestOneBit(minCapacity - 1) << 1;
    }
}
