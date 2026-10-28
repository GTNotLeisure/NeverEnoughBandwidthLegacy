package cn.ussshenzhou.notenoughbandwidth.stat;

/** Side-wide totals for all connections. */
public class NebStats {

    private static final TrafficStats TRAFFIC = new TrafficStats();
    private static final TrafficTimeline TIMELINE = new TrafficTimeline();
    private static final PacketProfile OUTBOUND = new PacketProfile();
    private static final PacketProfile INBOUND = new PacketProfile();

    public static TrafficStats traffic() {
        return TRAFFIC;
    }

    public static TrafficTimeline timeline() {
        return TIMELINE;
    }

    public static PacketProfile outboundProfile() {
        return OUTBOUND;
    }

    public static PacketProfile inboundProfile() {
        return INBOUND;
    }

    public static TrafficSnapshot snapshot() {
        return TrafficSnapshot.of(TRAFFIC);
    }

    public static void reset() {
        TRAFFIC.reset();
        TIMELINE.reset();
        OUTBOUND.reset();
        INBOUND.reset();
    }

    private NebStats() {}
}
