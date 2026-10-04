package com.dranant.entity;

import com.dranant.block.ExaminationBedBlock;
import com.dranant.block.entity.ExaminationBedBlockEntity;
import com.dranant.clinic.ClinicQueueManager;
import com.dranant.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Runs the Experimental Ward. Lays the Line 2 patient on the Examination Bed, brews for 5 seconds with sounds and
 * magic particles, then the patient stands up, drops exactly 64 diamonds and leaves happily.
 */
public class AssistantScientistEntity extends AssistantEntity {
    public static final int TREATMENT_TICKS = 100; // 5 seconds

    @Nullable
    private UUID patient;
    @Nullable
    private BlockPos bedHead;
    @Nullable
    private BlockPos clinicPos;
    private int treatTicks;

    public AssistantScientistEntity(EntityType<? extends AssistantScientistEntity> type, Level level) {
        super(type, level);
    }

    public boolean isBusy() {
        return patient != null;
    }

    @Nullable
    public UUID getPatient() {
        return patient;
    }

    public void beginTreatment(Villager villager, BlockPos bed, BlockPos clinic) {
        this.patient = villager.getUUID();
        this.bedHead = bed;
        this.clinicPos = clinic;
        this.treatTicks = 0;
        ClinicQueueManager.markPatient(villager, clinic, level().getGameTime());
        villager.getNavigation().stop();
        villager.setNoAi(true);
        layDown(villager, bed);
        if (level().getBlockEntity(bed) instanceof ExaminationBedBlockEntity bedEntity) {
            bedEntity.setPatient(villager.getUUID());
        }
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REGENERATION_SERUM.get()));
        level().playSound(null, bed, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Called when the treatment must stop early (e.g. the expired serum was administered). */
    public void abortTreatment() {
        clearBed();
        patient = null;
        treatTicks = 0;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Lays the (AI-frozen) patient flat on the bed's head half, facing along the bed. */
    public static void layDown(Villager villager, BlockPos bedHead) {
        Direction facing = Direction.NORTH;
        BlockState state = villager.level().getBlockState(bedHead);
        if (state.hasProperty(ExaminationBedBlock.FACING)) facing = state.getValue(ExaminationBedBlock.FACING);
        villager.setPose(Pose.SLEEPING);
        villager.teleportTo(bedHead.getX() + 0.5D, bedHead.getY() + 0.63D, bedHead.getZ() + 0.5D);
        float yaw = facing.getOpposite().toYRot();
        villager.setYRot(yaw);
        villager.setYHeadRot(yaw);
        villager.yBodyRot = yaw;
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    /** Stands the patient up beside the bed. */
    public static void standUp(Villager villager, BlockPos bedHead) {
        Direction facing = Direction.NORTH;
        BlockState state = villager.level().getBlockState(bedHead);
        if (state.hasProperty(ExaminationBedBlock.FACING)) facing = state.getValue(ExaminationBedBlock.FACING);
        BlockPos foot = bedHead.relative(facing.getOpposite());
        BlockPos stand = foot.relative(facing.getOpposite());
        villager.setPose(Pose.STANDING);
        villager.teleportTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
    }

    private void clearBed() {
        if (bedHead != null && level().getBlockEntity(bedHead) instanceof ExaminationBedBlockEntity bedEntity) {
            bedEntity.setPatient(null);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && patient != null) {
            tickTreatment((ServerLevel) level());
        }
    }

    private void tickTreatment(ServerLevel level) {
        Entity entity = level.getEntity(patient);
        if (!(entity instanceof Villager villager) || !villager.isAlive() || bedHead == null) {
            abortTreatment();
            return;
        }
        treatTicks++;
        getLookControl().setLookAt(villager, 30.0F, 30.0F);
        if (!villager.hasPose(Pose.SLEEPING)) {
            layDown(villager, bedHead);
        }
        double x = villager.getX(), y = villager.getY() + 0.5, z = villager.getZ();
        if (treatTicks % 10 == 0) {
            level.playSound(null, bedHead, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.8F, 0.85F + random.nextFloat() * 0.3F);
        }
        if (treatTicks % 3 == 0) {
            level.sendParticles(ParticleTypes.WITCH, x, y, z, 5, 0.5, 0.3, 0.5, 0.05);
            level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 12, 0.6, 0.4, 0.6, 0.6);
            level.sendParticles(ParticleTypes.EFFECT, x, y + 0.2, z, 4, 0.4, 0.2, 0.4, 0.02);
        }
        if (treatTicks % 20 == 0) {
            swing(InteractionHand.MAIN_HAND);
        }
        if (treatTicks >= TREATMENT_TICKS) {
            completeTreatment(level, villager);
        }
    }

    private void completeTreatment(ServerLevel level, Villager villager) {
        standUp(villager, bedHead);
        villager.setNoAi(false);
        ItemEntity diamonds = new ItemEntity(level, villager.getX(), villager.getY() + 0.6, villager.getZ(), new ItemStack(Items.DIAMOND, 64));
        diamonds.getPersistentData().putBoolean(ClinicQueueManager.INCOME_TAG, true);
        diamonds.setDefaultPickUpDelay();
        level.addFreshEntity(diamonds);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, villager.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7F, 1.4F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 30, 0.6, 0.6, 0.6, 0.1);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.0, villager.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
        if (clinicPos != null) {
            ClinicQueueManager.depart(level, villager, clinicPos);
        } else {
            ClinicQueueManager.release(villager);
        }
        abortTreatment();
    }

    @Override
    protected String greeting() {
        return "Welcome to the Experimental Ward. Line 2 patients pay 64 diamonds... results guaranteed!";
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (patient != null) tag.putUUID("Patient", patient);
        if (bedHead != null) tag.putLong("BedHead", bedHead.asLong());
        if (clinicPos != null) tag.putLong("Clinic", clinicPos.asLong());
        tag.putInt("TreatTicks", treatTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        patient = tag.hasUUID("Patient") ? tag.getUUID("Patient") : null;
        bedHead = tag.contains("BedHead") ? BlockPos.of(tag.getLong("BedHead")) : null;
        clinicPos = tag.contains("Clinic") ? BlockPos.of(tag.getLong("Clinic")) : null;
        treatTicks = tag.getInt("TreatTicks");
    }
}
