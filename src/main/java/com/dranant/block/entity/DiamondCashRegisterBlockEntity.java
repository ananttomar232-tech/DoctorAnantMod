package com.dranant.block.entity;

import com.dranant.clinic.ClinicQueueManager;
import com.dranant.counter.DiamondCounterData;
import com.dranant.counter.DiamondFx;
import com.dranant.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores earned diamonds and owns the two queue lines of its clinic (driven by {@link ClinicQueueManager}).
 */
public class DiamondCashRegisterBlockEntity extends BlockEntity {
    private int storedDiamonds;
    private final List<UUID> line1 = new ArrayList<>();
    private final List<UUID> line2 = new ArrayList<>();
    @Nullable
    private BlockPos bedHead;
    // transient runtime state
    public long nextFacilityScan;
    public int serviceTimer;
    public long nextCustomerSpawn;

    public DiamondCashRegisterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIAMOND_CASH_REGISTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DiamondCashRegisterBlockEntity register) {
        if (level instanceof ServerLevel serverLevel) {
            ClinicQueueManager.tick(serverLevel, pos, state, register);
        }
    }

    /** Deposits diamonds, bursts red + yellow particles, plays the collection sounds and feeds the global Diamond Counter HUD. */
    public void addDiamonds(int amount) {
        if (amount <= 0 || !(level instanceof ServerLevel serverLevel)) return;
        storedDiamonds += amount;
        setChanged();
        DiamondFx.burst(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5);
        DiamondCounterData.get(serverLevel.getServer()).add(serverLevel.getServer(), amount);
    }

    public int takeAllDiamonds() {
        int n = storedDiamonds;
        storedDiamonds = 0;
        setChanged();
        return n;
    }

    public int getStoredDiamonds() {
        return storedDiamonds;
    }

    public List<UUID> getLine1() {
        return line1;
    }

    public List<UUID> getLine2() {
        return line2;
    }

    @Nullable
    public BlockPos getBedHead() {
        return bedHead;
    }

    public void setBedHead(@Nullable BlockPos bedHead) {
        this.bedHead = bedHead;
        setChanged();
    }

    /** Routes shop income to the closest register within 24 blocks; if none exists the HUD counter still receives it. */
    public static void depositToNearest(ServerLevel level, BlockPos origin, int amount) {
        DiamondCashRegisterBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    if (level.getBlockEntity(m) instanceof DiamondCashRegisterBlockEntity reg) {
                        double d = m.distSqr(origin);
                        if (d < bestDist) {
                            bestDist = d;
                            best = reg;
                        }
                    }
                }
            }
        }
        if (best != null) {
            best.addDiamonds(amount);
        } else {
            DiamondFx.burst(level, origin.getX() + 0.5, origin.getY() + 1.2, origin.getZ() + 0.5);
            DiamondCounterData.get(level.getServer()).add(level.getServer(), amount);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("storedDiamonds", storedDiamonds);
        tag.put("line1", writeUuids(line1));
        tag.put("line2", writeUuids(line2));
        if (bedHead != null) tag.putLong("bedHead", bedHead.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedDiamonds = tag.getInt("storedDiamonds");
        readUuids(tag.getList("line1", Tag.TAG_INT_ARRAY), line1);
        readUuids(tag.getList("line2", Tag.TAG_INT_ARRAY), line2);
        bedHead = tag.contains("bedHead") ? BlockPos.of(tag.getLong("bedHead")) : null;
    }

    private static ListTag writeUuids(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(NbtUtils.createUUID(id));
        return list;
    }

    private static void readUuids(ListTag list, List<UUID> out) {
        out.clear();
        for (Tag t : list) out.add(NbtUtils.loadUUID(t));
    }
}
