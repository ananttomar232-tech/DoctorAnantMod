package com.dranant.clinic;

import com.dranant.block.DiamondCashRegisterBlock;
import com.dranant.block.PosterBlock;
import com.dranant.block.entity.ItemDisplayPedestalBlockEntity;
import com.dranant.entity.AssistantEntity;
import com.dranant.entity.AssistantPharmacistEntity;
import com.dranant.entity.AssistantScientistEntity;
import com.dranant.registry.ModBlocks;
import com.dranant.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Small block-placing toolkit shared by the Central Shop and clinic builders. */
public final class BuildHelper {
    public static final int FLAGS = Block.UPDATE_CLIENTS;

    public static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    public static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    public static void fill(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    if (level.getBlockState(m) != state) level.setBlock(m, state, FLAGS);
                }
    }

    /** Clears a volume to air and removes loose item entities inside it. */
    public static void clear(ServerLevel level, BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        // top-down so falling blocks / attached blocks do not pop off as items
        for (int y = y1; y >= y0; y--)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, air, FLAGS | Block.UPDATE_SUPPRESS_DROPS);
                }
        AABB box = new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
        for (Entity e : level.getEntitiesOfClass(ItemEntity.class, box)) e.discard();
    }

    public static void placeRegister(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, ModBlocks.DIAMOND_CASH_REGISTER.get().defaultBlockState().setValue(DiamondCashRegisterBlock.FACING, facing));
    }

    public static void placePedestal(ServerLevel level, BlockPos pos, ItemStack display, int price) {
        set(level, pos, ModBlocks.ITEM_DISPLAY_PEDESTAL.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ItemDisplayPedestalBlockEntity pedestal) {
            pedestal.setDisplayedItem(display);
            pedestal.setDiamondPrice(price);
        }
    }

    /** Places a poster whose printed side faces {@code facing}; returns false if the needed cells are occupied. */
    public static boolean placePoster(ServerLevel level, BlockPos pos, Direction facing, PosterBlock poster, boolean force) {
        Direction side = facing.getClockWise();
        if (!force) {
            for (int w = 0; w < poster.getWidthBlocks(); w++)
                for (int h = 0; h < poster.getHeightBlocks(); h++) {
                    BlockPos cell = pos.relative(side, w).above(h);
                    if (!level.getBlockState(cell).isAir()) return false;
                    BlockPos wall = cell.relative(facing.getOpposite());
                    if (!level.getBlockState(wall).isSolidRender(level, wall)) return false;
                }
        }
        set(level, pos, poster.defaultBlockState().setValue(PosterBlock.FACING, facing));
        return true;
    }

    public static AssistantPharmacistEntity spawnPharmacist(ServerLevel level, BlockPos pos, float yaw) {
        AssistantPharmacistEntity e = ModEntities.ASSISTANT_PHARMACIST.get().create(level);
        if (e == null) return null;
        setup(level, e, pos, yaw, Component.literal("Assistant Pharmacist").withStyle(ChatFormatting.AQUA));
        return e;
    }

    public static AssistantScientistEntity spawnScientist(ServerLevel level, BlockPos pos, float yaw) {
        AssistantScientistEntity e = ModEntities.ASSISTANT_SCIENTIST.get().create(level);
        if (e == null) return null;
        setup(level, e, pos, yaw, Component.literal("Assistant Scientist").withStyle(ChatFormatting.GREEN));
        return e;
    }

    private static void setup(ServerLevel level, AssistantEntity e, BlockPos pos, float yaw, Component name) {
        e.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        e.setYHeadRot(yaw);
        e.yBodyRot = yaw;
        e.setPost(pos);
        e.setCustomName(name);
        e.setCustomNameVisible(true);
        level.addFreshEntity(e);
    }

    /** Yaw (degrees) that makes an entity look along {@code dir}. */
    public static float yawFor(Direction dir) {
        return dir.toYRot();
    }

    private BuildHelper() {}
}
