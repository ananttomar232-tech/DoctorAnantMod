package com.dranant.counter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/** World-saved Diamond Counter (stored in the overworld data folder as dranant_diamond_counter.dat). */
public class DiamondCounterData extends SavedData {
    private static final String NAME = "dranant_diamond_counter";

    private int total;
    private boolean running;
    private boolean visible;

    public static DiamondCounterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DiamondCounterData::new, DiamondCounterData::load, null), NAME);
    }

    private static DiamondCounterData load(CompoundTag tag, HolderLookup.Provider registries) {
        DiamondCounterData data = new DiamondCounterData();
        data.total = tag.getInt("total");
        data.running = tag.getBoolean("running");
        data.visible = tag.getBoolean("visible");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("total", total);
        tag.putBoolean("running", running);
        tag.putBoolean("visible", visible);
        return tag;
    }

    /** Adds earned diamonds (only while the counter is running) and pushes a HUD update with a "+X" popup. */
    public void add(MinecraftServer server, int amount) {
        if (!running || amount <= 0) return;
        total += amount;
        setDirty();
        PacketDistributor.sendToAllPlayers(new DiamondCounterPayload(total, amount, running, visible));
    }

    public void setRunning(MinecraftServer server, boolean run) {
        running = run;
        if (run) visible = true;
        setDirty();
        broadcast();
    }

    public void reset(MinecraftServer server) {
        total = 0;
        setDirty();
        broadcast();
    }

    public void broadcast() {
        PacketDistributor.sendToAllPlayers(new DiamondCounterPayload(total, 0, running, visible));
    }

    public void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DiamondCounterPayload(total, 0, running, visible));
    }

    public int getTotal() {
        return total;
    }

    public boolean isRunning() {
        return running;
    }
}
