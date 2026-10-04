package com.dranant.clinic;

import com.dranant.block.DiamondCashRegisterBlock;
import com.dranant.block.ExaminationBedBlock;
import com.dranant.block.entity.DiamondCashRegisterBlockEntity;
import com.dranant.block.entity.ExaminationBedBlockEntity;
import com.dranant.config.DrAnantConfig;
import com.dranant.entity.AssistantPharmacistEntity;
import com.dranant.entity.AssistantScientistEntity;
import com.dranant.registry.ModBlocks;
import com.dranant.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dual-queue villager AI.
 * <ul>
 *   <li>Villagers within the detection radius (48 by default) notice the clinic's Diamond Cash Register.</li>
 *   <li>70% join Line 1 (Counter Queue): a neat straight line in front of the register, 1.2 blocks apart.
 *       The Assistant Pharmacist sells them medicine for 1-5 diamonds which go straight into the register.</li>
 *   <li>30% join Line 2 (Lab Queue): a line leading to the Examination Bed, served by the Assistant Scientist.</li>
 * </ul>
 * Queue state is stored in each villager's persistent data so it survives saves.
 */
public final class ClinicQueueManager {
    public static final String ROOT = "dranant_clinic";
    public static final String INCOME_TAG = "dranant_income";

    public static final int STATE_NONE = 0;
    public static final int STATE_QUEUED = 1;
    public static final int STATE_PATIENT = 2;
    public static final int STATE_DEPARTING = 3;

    private static final double SPACING = 1.2D;
    private static final double FIRST_SPOT = 1.4D;
    private static final int DEPART_TICKS = 160;

    // ------------------------------------------------------------------ villager tag helpers
    public static CompoundTag data(Villager v) {
        CompoundTag persistent = v.getPersistentData();
        if (!persistent.contains(ROOT)) persistent.put(ROOT, new CompoundTag());
        return persistent.getCompound(ROOT);
    }

    public static int state(Villager v) {
        return v.getPersistentData().getCompound(ROOT).getInt("state");
    }

    private static boolean belongsTo(Villager v, BlockPos clinic) {
        CompoundTag d = v.getPersistentData().getCompound(ROOT);
        return d.contains("clinic") && d.getLong("clinic") == clinic.asLong();
    }

    public static void markPatient(Villager v, BlockPos clinic, long now) {
        CompoundTag d = data(v);
        d.putInt("state", STATE_PATIENT);
        d.putLong("clinic", clinic.asLong());
        d.putLong("since", now);
        d.putBoolean("frozen", false);
    }

    /** Ends the villager's visit: walks away from the clinic, then becomes a normal villager again after a cooldown. */
    public static void depart(ServerLevel level, Villager v, BlockPos clinic) {
        CompoundTag d = data(v);
        long now = level.getGameTime();
        Direction away = Direction.SOUTH;
        BlockState regState = level.getBlockState(clinic);
        if (regState.is(ModBlocks.DIAMOND_CASH_REGISTER.get())) away = regState.getValue(DiamondCashRegisterBlock.FACING);
        Vec3 exit = Vec3.atBottomCenterOf(clinic).add(away.getStepX() * 22.0D + (level.random.nextDouble() - 0.5D) * 8.0D, 0.0D,
                away.getStepZ() * 22.0D + (level.random.nextDouble() - 0.5D) * 8.0D);
        if (v.isNoAi()) v.setNoAi(false);
        d.putInt("state", STATE_DEPARTING);
        d.putLong("clinic", clinic.asLong());
        d.putLong("until", now + DEPART_TICKS);
        d.putLong("cooldown", now + DrAnantConfig.SERVED_COOLDOWN.get());
        d.putDouble("tx", exit.x);
        d.putDouble("tz", exit.z);
        d.putBoolean("frozen", false);
        v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** Fully resets a villager back to vanilla behaviour. */
    public static void release(Villager v) {
        CompoundTag d = data(v);
        if (d.getBoolean("frozen") || d.getInt("state") == STATE_PATIENT) {
            if (v.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) v.setPose(net.minecraft.world.entity.Pose.STANDING);
            v.setNoAi(false);
        }
        long cooldown = d.getLong("cooldown");
        boolean customer = d.getBoolean("customer");
        v.getPersistentData().remove(ROOT);
        CompoundTag fresh = data(v);
        fresh.putLong("cooldown", cooldown);
        if (customer) fresh.putBoolean("customer", true);
    }

    /** Called when a register is broken: every villager tied to it returns to normal. */
    public static void releaseAll(ServerLevel level, BlockPos clinic) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(clinic).inflate(96.0D), v -> belongsTo(v, clinic))) {
            release(v);
        }
    }

    /** Safety net for villagers loaded with a stale queue state (e.g. chunk unloaded mid-visit). */
    public static void validateOnLoad(ServerLevel level, Villager v) {
        CompoundTag d = v.getPersistentData().getCompound(ROOT);
        if (d.isEmpty() || !d.contains("clinic")) return;
        BlockPos clinic = BlockPos.of(d.getLong("clinic"));
        int st = d.getInt("state");
        if (st == STATE_PATIENT && level.getGameTime() - d.getLong("since") > 1200L) {
            release(v);
            return;
        }
        if (level.isLoaded(clinic) && !level.getBlockState(clinic).is(ModBlocks.DIAMOND_CASH_REGISTER.get())) {
            release(v);
        }
    }

    // ------------------------------------------------------------------ main tick (called by the register block entity)
    public static void tick(ServerLevel level, BlockPos pos, BlockState state, DiamondCashRegisterBlockEntity reg) {
        long now = level.getGameTime();
        if ((now + (pos.asLong() & 7L)) % 10L != 0L) return;
        Direction facing = state.getValue(DiamondCashRegisterBlock.FACING);

        if (now >= reg.nextFacilityScan) {
            reg.setBedHead(findBed(level, pos));
            reg.nextFacilityScan = now + 200L;
        }
        BlockPos bedHead = reg.getBedHead();
        if (bedHead != null && !level.getBlockState(bedHead).is(ModBlocks.EXAMINATION_BED.get())) {
            reg.setBedHead(null);
            bedHead = null;
        }

        prune(level, reg.getLine1(), pos, 1);
        prune(level, reg.getLine2(), pos, 2);

        if (now % 40L < 10L) {
            recruit(level, pos, reg, bedHead, now);
        }
        if (DrAnantConfig.SPAWN_CUSTOMERS.get() && now >= reg.nextCustomerSpawn) {
            reg.nextCustomerSpawn = now + DrAnantConfig.CUSTOMER_SPAWN_INTERVAL.get();
            int queued = reg.getLine1().size() + reg.getLine2().size();
            if (queued < DrAnantConfig.MAX_QUEUE_LENGTH.get() + 2) {
                spawnCustomer(level, pos);
            }
        }

        Vec3 registerCenter = Vec3.atCenterOf(pos);
        // ---- Line 1: straight queue in front of the counter
        List<UUID> line1 = reg.getLine1();
        for (int i = 0; i < line1.size(); i++) {
            Villager v = villager(level, line1.get(i));
            if (v == null) continue;
            Vec3 spot = line1Spot(level, pos, facing, i);
            moveOrHold(v, spot, registerCenter);
        }
        serveLine1(level, pos, reg, facing);

        // ---- Line 2: lab queue leading to the Examination Bed
        List<UUID> line2 = reg.getLine2();
        if (bedHead != null) {
            BlockState bedState = level.getBlockState(bedHead);
            Direction bedFacing = bedState.getValue(ExaminationBedBlock.FACING);
            Vec3 bedCenter = Vec3.atCenterOf(bedHead);
            for (int i = 0; i < line2.size(); i++) {
                Villager v = villager(level, line2.get(i));
                if (v == null) continue;
                moveOrHold(v, line2Spot(level, bedHead, bedFacing, i), bedCenter);
            }
            serveLine2(level, pos, reg, bedHead, bedFacing);
        } else if (!line2.isEmpty()) {
            // lab bed was removed: move everyone to the counter queue
            for (UUID id : line2) {
                Villager v = villager(level, id);
                if (v != null && line1.size() < DrAnantConfig.MAX_QUEUE_LENGTH.get()) {
                    data(v).putInt("line", 1);
                    line1.add(id);
                } else if (v != null) {
                    release(v);
                }
            }
            line2.clear();
        }

        tickDeparting(level, pos, now);
    }

    // ------------------------------------------------------------------ queue geometry
    public static Vec3 line1Spot(ServerLevel level, BlockPos register, Direction facing, int index) {
        double dist = FIRST_SPOT + SPACING * index;
        double x = register.getX() + 0.5D + facing.getStepX() * dist;
        double z = register.getZ() + 0.5D + facing.getStepZ() * dist;
        return new Vec3(x, standY(level, x, z, register.getY()), z);
    }

    public static Vec3 line2Spot(ServerLevel level, BlockPos bedHead, Direction bedFacing, int index) {
        BlockPos foot = bedHead.relative(bedFacing.getOpposite());
        Direction out = bedFacing.getOpposite();
        double dist = FIRST_SPOT - 0.2D + SPACING * index;
        double x = foot.getX() + 0.5D + out.getStepX() * dist;
        double z = foot.getZ() + 0.5D + out.getStepZ() * dist;
        return new Vec3(x, standY(level, x, z, bedHead.getY()), z);
    }

    /** Finds the floor height near startY at (x, z): first air-with-solid-below scanning from +2 down to -4. */
    private static double standY(ServerLevel level, double x, double z, int startY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int y = startY + 2; y >= startY - 4; y--) {
            m.set(bx, y, bz);
            BlockState here = level.getBlockState(m);
            BlockState above = level.getBlockState(m.above());
            BlockState below = level.getBlockState(m.below());
            if (here.getCollisionShape(level, m).isEmpty() && above.getCollisionShape(level, m.above()).isEmpty()
                    && below.isFaceSturdy(level, m.below(), Direction.UP)) {
                return y;
            }
        }
        return startY;
    }

    // ------------------------------------------------------------------ movement
    private static void moveOrHold(Villager v, Vec3 spot, Vec3 lookAt) {
        CompoundTag d = data(v);
        double dx = v.getX() - spot.x, dz = v.getZ() - spot.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        boolean frozen = d.getBoolean("frozen");
        if (horiz < 0.5D && Math.abs(v.getY() - spot.y) < 1.1D) {
            if (!frozen) {
                v.getNavigation().stop();
                v.setNoAi(true);
                d.putBoolean("frozen", true);
            }
            v.teleportTo(spot.x, v.getY(), spot.z);
            float yaw = (float) (Mth.atan2(lookAt.z - spot.z, lookAt.x - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
            v.setYRot(yaw);
            v.setYHeadRot(yaw);
            v.yBodyRot = yaw;
        } else {
            if (frozen) {
                v.setNoAi(false);
                d.putBoolean("frozen", false);
            }
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.6F, 0));
            v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(BlockPos.containing(lookAt)));
        }
    }

    private static boolean isAtFront(Villager v) {
        return data(v).getBoolean("frozen");
    }

    // ------------------------------------------------------------------ serving
    private static void serveLine1(ServerLevel level, BlockPos pos, DiamondCashRegisterBlockEntity reg, Direction facing) {
        List<UUID> line1 = reg.getLine1();
        if (line1.isEmpty()) {
            reg.serviceTimer = 0;
            return;
        }
        Villager front = villager(level, line1.get(0));
        if (front == null || !isAtFront(front)) {
            reg.serviceTimer = 0;
            return;
        }
        List<AssistantPharmacistEntity> pharmacists = level.getEntitiesOfClass(AssistantPharmacistEntity.class, new AABB(pos).inflate(10.0D));
        if (pharmacists.isEmpty()) return;
        reg.serviceTimer += 10;
        if (reg.serviceTimer < 40) return;
        reg.serviceTimer = 0;

        AssistantPharmacistEntity pharmacist = pharmacists.get(0);
        int tier = 1 + level.random.nextInt(ModItems.MEDICINES.size());
        ItemStack medicine = new ItemStack(ModItems.MEDICINES.get(tier - 1).get());
        int diamonds = 1 + level.random.nextInt(5); // 1-5 diamonds
        pharmacist.serveCustomer(front, medicine);
        reg.addDiamonds(diamonds);
        front.setItemSlot(EquipmentSlot.MAINHAND, medicine.copy());
        level.playSound(null, front.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, front.getX(), front.getY() + 1.8D, front.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.05D);
        line1.remove(0);
        depart(level, front, pos);
        reg.setChanged();
    }

    private static void serveLine2(ServerLevel level, BlockPos pos, DiamondCashRegisterBlockEntity reg, BlockPos bedHead, Direction bedFacing) {
        List<UUID> line2 = reg.getLine2();
        if (line2.isEmpty()) return;
        Villager front = villager(level, line2.get(0));
        if (front == null || !isAtFront(front)) return;
        if (level.getBlockEntity(bedHead) instanceof ExaminationBedBlockEntity bed && bed.getPatient() != null) {
            Entity current = level.getEntity(bed.getPatient());
            if (current != null && current.isAlive()) return; // bed busy
            bed.setPatient(null);
        }
        AssistantScientistEntity scientist = null;
        for (AssistantScientistEntity s : level.getEntitiesOfClass(AssistantScientistEntity.class, new AABB(bedHead).inflate(12.0D))) {
            if (!s.isBusy()) {
                scientist = s;
                break;
            }
        }
        if (scientist == null) return;
        line2.remove(0);
        front.setNoAi(false);
        scientist.beginTreatment(front, bedHead, pos);
        reg.setChanged();
    }

    // ------------------------------------------------------------------ recruiting / pruning
    private static void prune(ServerLevel level, List<UUID> line, BlockPos clinic, int lineId) {
        Iterator<UUID> it = line.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Villager v = villager(level, id);
            if (v == null || !v.isAlive()) {
                it.remove();
                continue;
            }
            CompoundTag d = v.getPersistentData().getCompound(ROOT);
            if (d.getInt("state") != STATE_QUEUED || d.getLong("clinic") != clinic.asLong() || d.getInt("line") != lineId) {
                it.remove();
            }
        }
    }

    private static void recruit(ServerLevel level, BlockPos pos, DiamondCashRegisterBlockEntity reg, @Nullable BlockPos bedHead, long now) {
        int radius = DrAnantConfig.DETECTION_RADIUS.get();
        int max = DrAnantConfig.MAX_QUEUE_LENGTH.get();
        double counterChance = DrAnantConfig.COUNTER_QUEUE_CHANCE.get();
        Set<UUID> queued = new HashSet<>(reg.getLine1());
        queued.addAll(reg.getLine2());
        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(radius), Villager::isAlive);
        for (Villager v : nearby) {
            if (queued.contains(v.getUUID()) || v.isBaby() || v.isSleeping() || v.isTrading()) continue;
            CompoundTag d = v.getPersistentData().getCompound(ROOT);
            int st = d.getInt("state");
            if (st == STATE_QUEUED && d.getLong("clinic") == pos.asLong()) {
                // re-attach a villager that was queued before a save/reload
                List<UUID> line = d.getInt("line") == 2 && bedHead != null ? reg.getLine2() : reg.getLine1();
                if (line.size() < max) {
                    data(v).putInt("line", line == reg.getLine2() ? 2 : 1);
                    line.add(v.getUUID());
                } else {
                    release(v);
                }
                continue;
            }
            if (st != STATE_NONE || d.getLong("cooldown") > now) continue;
            if (v.isNoAi()) continue; // never hijack map-maker NPCs
            boolean toCounter = bedHead == null || level.random.nextDouble() < counterChance;
            List<UUID> line = toCounter ? reg.getLine1() : reg.getLine2();
            if (line.size() >= max) {
                line = toCounter ? reg.getLine2() : reg.getLine1();
                toCounter = !toCounter;
                if (line.size() >= max || (!toCounter && bedHead == null)) continue;
            }
            CompoundTag nd = data(v);
            nd.putInt("state", STATE_QUEUED);
            nd.putInt("line", toCounter ? 1 : 2);
            nd.putLong("clinic", pos.asLong());
            nd.putBoolean("frozen", false);
            line.add(v.getUUID());
            reg.setChanged();
        }
    }

    private static void tickDeparting(ServerLevel level, BlockPos pos, long now) {
        List<Villager> leaving = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(64.0D),
                v -> state(v) == STATE_DEPARTING && belongsTo(v, pos));
        for (Villager v : leaving) {
            CompoundTag d = data(v);
            if (now < d.getLong("until")) {
                Vec3 exit = new Vec3(d.getDouble("tx"), v.getY(), d.getDouble("tz"));
                v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(exit, 0.6F, 1));
            } else {
                v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (d.getBoolean("customer")) {
                    level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1.0D, v.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.02D);
                    v.discard();
                } else {
                    release(v);
                }
            }
        }
    }

    /** Brand-new customer walking in from 18-26 blocks away (config: spawnCustomerVillagers). */
    private static void spawnCustomer(ServerLevel level, BlockPos clinic) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double dist = 18.0D + level.random.nextDouble() * 8.0D;
            int x = Mth.floor(clinic.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(clinic.getZ() + Math.sin(angle) * dist);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - clinic.getY()) > 10) continue;
            BlockPos spawn = new BlockPos(x, y, z);
            if (!level.getFluidState(spawn.below()).isEmpty()) continue;
            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return;
            v.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            v.setVillagerData(v.getVillagerData().setType(VillagerType.byBiome(level.getBiome(spawn))));
            data(v).putBoolean("customer", true);
            level.addFreshEntity(v);
            return;
        }
    }

    // ------------------------------------------------------------------ facilities
    /** Finds the head of the nearest Examination Bed (prefers one with a scientist close by). */
    @Nullable
    public static BlockPos findBed(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (!s.is(ModBlocks.EXAMINATION_BED.get()) || s.getValue(ExaminationBedBlock.PART) != BedPart.HEAD) continue;
                    double score = m.distSqr(center);
                    if (!level.getEntitiesOfClass(AssistantScientistEntity.class, new AABB(m).inflate(6.0D)).isEmpty()) score -= 10000.0D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static Villager villager(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        return e instanceof Villager v ? v : null;
    }

    private ClinicQueueManager() {}
}
