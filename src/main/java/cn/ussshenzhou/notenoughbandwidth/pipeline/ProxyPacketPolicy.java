package cn.ussshenzhou.notenoughbandwidth.pipeline;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S0APacketUseBed;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S10PacketSpawnPainting;
import net.minecraft.network.play.server.S11PacketSpawnExperienceOrb;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S1EPacketRemoveEntityEffect;
import net.minecraft.network.play.server.S20PacketEntityProperties;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S3APacketTabComplete;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;

import com.google.common.collect.BiMap;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.util.NebVarInt;
import io.netty.buffer.ByteBuf;

public class ProxyPacketPolicy {

    private static final Set<Class<? extends Packet>> VISIBLE_TYPES = new HashSet<>(
        Arrays.asList(
            C00PacketKeepAlive.class,
            C01PacketChatMessage.class,
            C02PacketUseEntity.class,
            C0APacketAnimation.class,
            C0BPacketEntityAction.class,
            C14PacketTabComplete.class,
            C15PacketClientSettings.class,
            S00PacketKeepAlive.class,
            S01PacketJoinGame.class,
            S02PacketChat.class,
            S04PacketEntityEquipment.class,
            S07PacketRespawn.class,
            S0APacketUseBed.class,
            S0BPacketAnimation.class,
            S0CPacketSpawnPlayer.class,
            S0DPacketCollectItem.class,
            S0EPacketSpawnObject.class,
            S0FPacketSpawnMob.class,
            S10PacketSpawnPainting.class,
            S11PacketSpawnExperienceOrb.class,
            S12PacketEntityVelocity.class,
            S13PacketDestroyEntities.class,
            S14PacketEntity.class,
            S18PacketEntityTeleport.class,
            S19PacketEntityHeadLook.class,
            S19PacketEntityStatus.class,
            S1BPacketEntityAttach.class,
            S1CPacketEntityMetadata.class,
            S1DPacketEntityEffect.class,
            S1EPacketRemoveEntityEffect.class,
            S20PacketEntityProperties.class,
            S25PacketBlockBreakAnim.class,
            S2CPacketSpawnGlobalEntity.class,
            S38PacketPlayerListItem.class,
            S3APacketTabComplete.class,
            S3BPacketScoreboardObjective.class,
            S3CPacketUpdateScore.class,
            S3DPacketDisplayScoreboard.class,
            S3EPacketTeams.class,
            S40PacketDisconnect.class));

    private static final Set<String> VISIBLE_CHANNELS = new HashSet<>(
        Arrays.asList(
            ModConstants.CHANNEL,
            PluginMessageFrame.CHANNEL,
            "FML",
            "FML|HS",
            "REGISTER",
            "UNREGISTER",
            "BungeeCord"));

    public static boolean requiresVisibility(Class<? extends Packet> packetType) {
        for (Class<?> type = packetType; type != null
            && Packet.class.isAssignableFrom(type); type = type.getSuperclass()) {
            if (VISIBLE_TYPES.contains(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean requiresVisibility(String channel) {
        return channel != null
            && (VISIBLE_CHANNELS.contains(channel) || channel.startsWith("MC|") || channel.startsWith("FML|"));
    }

    public static boolean resetsClientSession(ByteBuf packet, BiMap<Integer, Class<? extends Packet>> protocol,
        CustomPayloadLookup lookup) {
        int payloadId = lookup.idOf(protocol);
        if (protocol == null || protocol.get(payloadId) != S3FPacketCustomPayload.class
            || !"FML|HS".equals(lookup.channelOf(packet, payloadId))) {
            return false;
        }
        ByteBuf view = packet.duplicate();
        NebVarInt.read(view);
        int channelLength = NebVarInt.read(view);
        view.skipBytes(channelLength);
        return view.readableBytes() >= 3 && view.readUnsignedShort() == view.readableBytes() && view.readByte() == -2;
    }

    private ProxyPacketPolicy() {}
}
