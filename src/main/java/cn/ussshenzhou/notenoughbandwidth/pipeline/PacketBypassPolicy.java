package cn.ussshenzhou.notenoughbandwidth.pipeline;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.Packet;

import com.google.common.collect.BiMap;

import cn.ussshenzhou.notenoughbandwidth.util.NebVarInt;
import io.netty.buffer.ByteBuf;

public class PacketBypassPolicy {

    private final Set<Integer> immediatePacketIds = new HashSet<>();
    private final Set<String> immediateChannels = new HashSet<>();
    private final Set<String> immediatePacketTypes = new HashSet<>();
    private final CustomPayloadLookup payloadLookup;
    private BiMap<Integer, Class<? extends Packet>> resolvedProtocol;

    public PacketBypassPolicy(Collection<String> channels, CustomPayloadLookup payloadLookup) {
        this(channels, Collections.emptySet(), payloadLookup);
    }

    public PacketBypassPolicy(Collection<String> channels, Collection<String> packetTypes,
        CustomPayloadLookup payloadLookup) {
        if (channels != null) {
            for (String channel : channels) {
                if (channel != null && !channel.isEmpty()) {
                    immediateChannels.add(channel);
                }
            }
        }
        if (packetTypes != null) {
            for (String packetType : packetTypes) {
                if (packetType != null && !packetType.isEmpty()) {
                    immediatePacketTypes.add(packetType);
                }
            }
        }
        this.payloadLookup = payloadLookup;
    }

    public void refresh(BiMap<Integer, Class<? extends Packet>> protocol) {
        if (protocol == null || protocol == resolvedProtocol) {
            return;
        }
        resolvedProtocol = protocol;
        immediatePacketIds.clear();
        for (Map.Entry<Integer, Class<? extends Packet>> entry : protocol.entrySet()) {
            Class<? extends Packet> packetType = entry.getValue();
            if (packetType != null
                && (ProxyPacketPolicy.requiresVisibility(packetType) || matchesConfiguredType(packetType))) {
                immediatePacketIds.add(entry.getKey());
            }
        }
    }

    public boolean shouldBypass(ByteBuf body, int payloadId) {
        NebVarInt.Cursor cursor = new NebVarInt.Cursor();
        cursor.at(body.readerIndex());
        if (!NebVarInt.readAt(body, cursor)) {
            return false;
        }
        if (immediatePacketIds.contains(cursor.value)) {
            return true;
        }
        String channel = payloadLookup.channelOf(body, payloadId);
        return channel != null
            && (ProxyPacketPolicy.requiresVisibility(channel) || immediateChannels.contains(channel));
    }

    private boolean matchesConfiguredType(Class<? extends Packet> packetType) {
        String fullName = packetType.getName();
        String simpleName = packetType.getSimpleName();
        return immediatePacketTypes.contains(fullName) || immediatePacketTypes.contains(simpleName);
    }
}
