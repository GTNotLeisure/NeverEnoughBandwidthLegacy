package cn.ussshenzhou.notenoughbandwidth.codec;

/** Creates {@link PacketCodec} instances for one {@link PacketCodecType}. */
public interface PacketCodecFactory {

    PacketCodecType type();

    /** Returns whether this backend is available in the current runtime without throwing. */
    boolean available();

    String describe();

    PacketCodec create();
}
