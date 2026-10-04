package com.dranant.counter;

import com.dranant.DoctorAnantMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: current Diamond Counter total, the delta of the latest transaction, and the run/visible flags. */
public record DiamondCounterPayload(int total, int delta, boolean running, boolean visible) implements CustomPacketPayload {
    public static final Type<DiamondCounterPayload> TYPE = new Type<>(DoctorAnantMod.id("diamond_counter"));

    public static final StreamCodec<ByteBuf, DiamondCounterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DiamondCounterPayload::total,
            ByteBufCodecs.VAR_INT, DiamondCounterPayload::delta,
            ByteBufCodecs.BOOL, DiamondCounterPayload::running,
            ByteBufCodecs.BOOL, DiamondCounterPayload::visible,
            DiamondCounterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
