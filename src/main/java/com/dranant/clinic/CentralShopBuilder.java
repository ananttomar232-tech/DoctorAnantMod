package com.dranant.clinic;

import com.dranant.item.MedicineItem;
import com.dranant.registry.ModBlocks;
import com.dranant.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally constructs the Central Shop (pure Java, no schematic):
 * 15 x 7 x 15 volume centred on the spawner, quartz/terracotta checker floor, glass walls with quartz pillars,
 * sea-lantern ceiling, customer counter with the Diamond Cash Register, 12 pre-loaded floating pedestals,
 * the Assistant Pharmacist and Dr. Anant posters on the outer walls.
 */
public final class CentralShopBuilder {
    private static final int HALF = 7;   // 15 wide
    private static final int HEIGHT = 7; // floor + 5 wall layers + ceiling

    public static void build(ServerLevel level, BlockPos origin, @Nullable Player player) {
        int cx = origin.getX(), cz = origin.getZ();
        int y0 = origin.getY() - 1;              // floor layer
        int yTop = y0 + HEIGHT - 1;              // ceiling layer
        int x0 = cx - HALF, x1 = cx + HALF, z0 = cz - HALF, z1 = cz + HALF;

        // 1) clear the bounding volume
        BuildHelper.clear(level, new BlockPos(x0, y0, z0), new BlockPos(x1, yTop, z1));

        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState terracotta = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
        BlockState white = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState bricks = Blocks.QUARTZ_BRICKS.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

        // 2) floor: polished quartz / terracotta checkerboard with a white border
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                BlockState f = border ? quartz : (((x + z) & 1) == 0 ? terracotta : white);
                BuildHelper.set(level, x, y0, z, f);
            }
        }
        // 3) walls: quartz pillars on corners + every 4 blocks, brick base, glass panels
        for (int y = y0 + 1; y < yTop; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
                    if (!edgeX && !edgeZ) continue;
                    boolean isPillar = (edgeX && edgeZ) || (edgeZ && (x - x0) % 4 == 0) || (edgeX && (z - z0) % 4 == 0);
                    BlockState w = isPillar ? pillar : (y == y0 + 1 || y == yTop - 1 ? bricks : glass);
                    BuildHelper.set(level, x, y, z, w);
                }
            }
        }
        // 4) ceiling with lighting grid
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                boolean light = !border && (x - x0) % 3 == 0 && (z - z0) % 3 == 0;
                BuildHelper.set(level, x, yTop, z, border ? bricks : light ? Blocks.SEA_LANTERN.defaultBlockState() : quartz);
            }
        }
        // 5) entrance (south side, 3 wide x 3 tall) + welcome carpet
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = y0 + 1; y <= y0 + 3; y++) BuildHelper.set(level, x, y, z1, Blocks.AIR.defaultBlockState());
            BuildHelper.set(level, x, y0, z1, Blocks.CYAN_TERRACOTTA.defaultBlockState());
        }

        // 6) customer counter (row of tables) with the Diamond Cash Register in the middle
        int counterZ = cz - 3;
        for (int x = cx - 4; x <= cx + 4; x++) {
            boolean end = x == cx - 4 || x == cx + 4;
            BuildHelper.set(level, x, y0 + 1, counterZ, end ? pillar : Blocks.POLISHED_DIORITE.defaultBlockState());
        }
        BuildHelper.placeRegister(level, new BlockPos(cx, y0 + 2, counterZ), Direction.SOUTH);
        BuildHelper.set(level, cx - 3, y0 + 2, counterZ, Blocks.BREWING_STAND.defaultBlockState());
        BuildHelper.set(level, cx + 3, y0 + 2, counterZ, Blocks.POTTED_RED_TULIP.defaultBlockState());
        // back wall shelves behind the pharmacist
        for (int x = cx - 5; x <= cx + 5; x++) {
            BuildHelper.set(level, x, y0 + 1, z0 + 1, Blocks.BOOKSHELF.defaultBlockState());
            BuildHelper.set(level, x, y0 + 2, z0 + 1, (x & 1) == 0 ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        }
        BuildHelper.set(level, cx - 5, y0 + 1, z0 + 2, Blocks.CAULDRON.defaultBlockState());
        BuildHelper.set(level, cx + 5, y0 + 1, z0 + 2, Blocks.BREWING_STAND.defaultBlockState());

        // 7) waiting benches along the entrance corners
        BlockState benchE = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState benchW = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        BuildHelper.set(level, x0 + 1, y0 + 1, z1 - 1, benchW);
        BuildHelper.set(level, x1 - 1, y0 + 1, z1 - 1, benchE);
        BuildHelper.set(level, x0 + 1, y0 + 1, z0 + 2, Blocks.POTTED_FERN.defaultBlockState());
        BuildHelper.set(level, x1 - 1, y0 + 1, z0 + 2, Blocks.POTTED_FERN.defaultBlockState());

        // 8) floating pedestals pre-loaded with all 10 medicines + handcuffs + syringe
        List<ItemStack> goods = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        for (DeferredItem<MedicineItem> med : ModItems.MEDICINES) {
            goods.add(new ItemStack(med.get()));
            prices.add(med.get().getShopPrice());
        }
        goods.add(new ItemStack(ModItems.TITANIUM_HANDCUFFS.get()));
        prices.add(24);
        goods.add(new ItemStack(ModItems.UNIVERSAL_CURE_SYRINGE.get()));
        prices.add(32);
        int[][] slots = {
                {cx - 5, cz - 1}, {cx - 5, cz + 1}, {cx - 5, cz + 3}, {cx - 5, cz + 5},   // west aisle
                {cx + 5, cz - 1}, {cx + 5, cz + 1}, {cx + 5, cz + 3}, {cx + 5, cz + 5},   // east aisle
                {cx - 3, cz + 1}, {cx + 3, cz + 1}, {cx - 3, cz + 4}, {cx + 3, cz + 4}    // centre island
        };
        for (int i = 0; i < slots.length && i < goods.size(); i++) {
            BuildHelper.placePedestal(level, new BlockPos(slots[i][0], y0 + 1, slots[i][1]), goods.get(i), prices.get(i));
        }

        // 9) posters on the outer walls
        BuildHelper.placePoster(level, new BlockPos(cx - 4, y0 + 3, z1 + 1), Direction.SOUTH, ModBlocks.POSTER_LANDSCAPE.get(), true);
        BuildHelper.placePoster(level, new BlockPos(cx + 4, y0 + 3, z1 + 1), Direction.SOUTH, ModBlocks.POSTER_SQUARE.get(), true);
        BuildHelper.placePoster(level, new BlockPos(x0 - 1, y0 + 2, cz - 1), Direction.WEST, ModBlocks.POSTER_BILLBOARD.get(), true);
        BuildHelper.placePoster(level, new BlockPos(x1 + 1, y0 + 2, cz + 1), Direction.EAST, ModBlocks.POSTER_BILLBOARD.get(), true);

        // 10) staff
        BuildHelper.spawnPharmacist(level, new BlockPos(cx, y0 + 1, counterZ - 1), Direction.SOUTH.toYRot());

        level.playSound(null, origin, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, origin, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx + 0.5, y0 + 3.0, cz + 0.5, 150, 6.0, 2.0, 6.0, 0.3);
        if (player != null) {
            player.sendSystemMessage(Component.literal("Dr. Anant's Central Shop is open! Customers queue at the counter.").withStyle(ChatFormatting.GREEN));
        }
    }

    private CentralShopBuilder() {}
}
