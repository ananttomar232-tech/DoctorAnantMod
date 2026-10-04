package com.dranant.clinic;

import com.dranant.DoctorAnantMod;
import com.dranant.block.ExaminationBedBlock;
import com.dranant.block.PosterBlock;
import com.dranant.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds clinic tiers 1-10.
 * <ol>
 *   <li>Loads data/dranant/structure/clinic_level_N.nbt via the StructureTemplateManager, clears the volume and places it.
 *       The register, examination bed, both assistants and posters are then auto-installed inside the building.</li>
 *   <li>If the .nbt is missing or contains no blocks (clinic_level_1..3 as attached are empty, levels 7..10 do not exist)
 *       a procedural clinic scaled to the tier is generated instead, so every summoner always works.</li>
 * </ol>
 */
public final class ClinicBuilder {
    private static final Set<String> GROUND_BLOCKS = Set.of("minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
            "minecraft:podzol", "minecraft:stone", "minecraft:gravel", "minecraft:sand", "minecraft:mud", "minecraft:rooted_dirt",
            "minecraft:dirt_path", "minecraft:mycelium", "minecraft:deepslate", "minecraft:andesite", "minecraft:diorite", "minecraft:granite");

    private static final BlockState[] ACCENTS = {
            Blocks.RED_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(),
            Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState(), Blocks.CYAN_CONCRETE.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.BLUE_CONCRETE.defaultBlockState(), Blocks.PURPLE_CONCRETE.defaultBlockState(),
            Blocks.MAGENTA_CONCRETE.defaultBlockState()};

    /** Info read straight from the raw .nbt file. */
    private record StructureInfo(int sizeX, int sizeY, int sizeZ, int blockCount, int groundLayer) {}

    public static void summonClinic(ServerLevel level, BlockPos pos, int tier, @Nullable Player player) {
        ResourceLocation templateId = DoctorAnantMod.id("clinic_level_" + tier);
        StructureInfo info = readInfo(level, tier);
        boolean placed = false;
        if (info != null && info.blockCount() > 0) {
            Optional<StructureTemplate> template = level.getStructureManager().get(templateId);
            if (template.isPresent()) {
                placeTemplate(level, pos, template.get(), info);
                placed = true;
            }
        }
        if (!placed) {
            buildProcedural(level, pos, tier);
            if (info != null && info.blockCount() == 0) {
                DoctorAnantMod.LOGGER.warn("clinic_level_{}.nbt contains no blocks (size {}x{}x{}); used the procedural clinic instead.",
                        tier, info.sizeX(), info.sizeY(), info.sizeZ());
            }
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2F, 0.9F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 3.0, pos.getZ() + 0.5, 200, 8.0, 3.0, 8.0, 0.3);
        if (player != null) {
            player.sendSystemMessage(Component.literal("Dr. Anant's Clinic - Level " + tier + " is ready! "
                    + (placed ? "(structure clinic_level_" + tier + ".nbt)" : "(procedural layout)")).withStyle(ChatFormatting.GREEN));
        }
    }

    // ================================================================== NBT structure path
    @Nullable
    private static StructureInfo readInfo(ServerLevel level, int tier) {
        ResourceLocation file = DoctorAnantMod.id("structure/clinic_level_" + tier + ".nbt");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag size = tag.getList("size", Tag.TAG_INT);
            int sx = size.size() == 3 ? size.getInt(0) : 0;
            int sy = size.size() == 3 ? size.getInt(1) : 0;
            int sz = size.size() == 3 ? size.getInt(2) : 0;
            ListTag palette = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
                    : tag.contains("palettes", Tag.TAG_LIST) && !tag.getList("palettes", Tag.TAG_LIST).isEmpty()
                    ? tag.getList("palettes", Tag.TAG_LIST).getList(0) : new ListTag();
            ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            int[] groundPerLayer = new int[Math.max(1, sy)];
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag b = blocks.getCompound(i);
                int state = b.getInt("state");
                ListTag p = b.getList("pos", Tag.TAG_INT);
                if (p.size() != 3 || state < 0 || state >= palette.size()) continue;
                int y = p.getInt(1);
                if (y >= 0 && y < groundPerLayer.length && GROUND_BLOCKS.contains(palette.getCompound(state).getString("Name"))) {
                    groundPerLayer[y]++;
                }
            }
            int area = Math.max(1, sx * sz);
            int ground = -1;
            for (int y = 0; y < groundPerLayer.length; y++) {
                if (groundPerLayer[y] >= area * 0.3) ground = y;
            }
            return new StructureInfo(sx, sy, sz, blocks.size(), ground);
        } catch (Exception e) {
            DoctorAnantMod.LOGGER.error("Could not read clinic_level_{}.nbt", tier, e);
            return null;
        }
    }

    private static void placeTemplate(ServerLevel level, BlockPos pos, StructureTemplate template, StructureInfo info) {
        Vec3i size = template.getSize();
        int baseY = info.groundLayer() >= 0 ? pos.getY() - 1 - info.groundLayer() : pos.getY() - 1;
        BlockPos origin = new BlockPos(pos.getX() - size.getX() / 2, baseY, pos.getZ() - size.getZ() / 2);
        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        if (max.getY() >= pos.getY()) {
            BuildHelper.clear(level, new BlockPos(origin.getX(), pos.getY(), origin.getZ()), max);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        installFacilities(level, origin, max, pos);
    }

    /** Finds indoor floor space inside a placed structure and installs counter, register, lab bed, assistants and posters. */
    private static void installFacilities(ServerLevel level, BlockPos min, BlockPos max, BlockPos summoner) {
        Set<Long> floor = new HashSet<>();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = min.getX() + 1; x < max.getX(); x++) {
            for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                for (int y = Math.max(min.getY(), summoner.getY() - 2); y < max.getY() - 1; y++) {
                    m.set(x, y, z);
                    if (isIndoorFloor(level, m, max.getY())) {
                        BlockPos p = m.immutable();
                        floor.add(p.asLong());
                        candidates.add(p);
                    }
                }
            }
        }
        double cx = (min.getX() + max.getX()) / 2.0, cz = (min.getZ() + max.getZ()) / 2.0;
        int lowestY = candidates.stream().mapToInt(BlockPos::getY).min().orElse(summoner.getY());
        candidates.sort(Comparator.comparingDouble(p -> Math.hypot(p.getX() - cx, p.getZ() - cz) + (p.getY() - lowestY) * 3.0));

        BlockPos registerPos = null;
        Direction registerFacing = Direction.SOUTH;
        for (BlockPos c : candidates) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (run(floor, c, d, 6) >= 5 && floor.contains(c.relative(d.getOpposite()).asLong())) {
                    registerPos = c;
                    registerFacing = d;
                    break;
                }
            }
            if (registerPos != null) break;
        }
        if (registerPos == null) {
            // no usable interior: build a small outdoor reception in front of the summoner
            registerPos = summoner;
            registerFacing = Direction.SOUTH;
            BuildHelper.clear(level, summoner.offset(-2, 0, -3), summoner.offset(8, 3, 8));
        }
        BuildHelper.placeRegister(level, registerPos, registerFacing);
        BlockPos pharmacistPos = registerPos.relative(registerFacing.getOpposite());
        BuildHelper.spawnPharmacist(level, pharmacistPos, registerFacing.toYRot());

        // lab bed: needs foot + head on floor and a free queue run behind the foot
        Set<Long> reserved = new HashSet<>();
        for (int i = -1; i <= 7; i++) reserved.add(registerPos.relative(registerFacing, i).asLong());
        BlockPos bedFoot = null;
        Direction bedFacing = Direction.NORTH;
        for (BlockPos c : candidates) {
            double dist = Math.sqrt(c.distSqr(registerPos));
            if (dist < 5 || dist > 22 || reserved.contains(c.asLong())) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos head = c.relative(d);
                if (!floor.contains(head.asLong()) || reserved.contains(head.asLong())) continue;
                if (run(floor, c, d.getOpposite(), 4) >= 4 && floor.contains(head.relative(d.getClockWise()).asLong())) {
                    bedFoot = c;
                    bedFacing = d;
                    break;
                }
            }
            if (bedFoot != null) break;
        }
        if (bedFoot == null) {
            bedFoot = registerPos.relative(registerFacing.getClockWise(), 6);
            bedFacing = registerFacing.getOpposite();
            BuildHelper.set(level, bedFoot.below(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
            BuildHelper.set(level, bedFoot.relative(bedFacing).below(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        ExaminationBedBlock.placeBed(level, bedFoot, bedFacing);
        BlockPos head = bedFoot.relative(bedFacing);
        BuildHelper.spawnScientist(level, head.relative(bedFacing.getClockWise()), bedFacing.getCounterClockWise().toYRot());

        placeOutsidePosters(level, min, max, summoner.getY());
    }

    private static boolean isIndoorFloor(ServerLevel level, BlockPos p, int maxY) {
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) return false;
        if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return false;
        for (int y = p.getY() + 2; y <= Math.min(maxY, p.getY() + 12); y++) {
            if (!level.getBlockState(new BlockPos(p.getX(), y, p.getZ())).isAir()) return true;
        }
        return false;
    }

    private static int run(Set<Long> floor, BlockPos start, Direction d, int max) {
        int n = 0;
        BlockPos p = start;
        for (int i = 0; i < max; i++) {
            p = p.relative(d);
            if (!floor.contains(p.asLong())) break;
            n++;
        }
        return n;
    }

    /** Walks inward from each side of the bounding box to the outer wall and hangs one poster per side. */
    private static void placeOutsidePosters(ServerLevel level, BlockPos min, BlockPos max, int groundY) {
        PosterBlock[] posters = {ModBlocks.POSTER_LANDSCAPE.get(), ModBlocks.POSTER_SQUARE.get(), ModBlocks.POSTER_BILLBOARD.get(), ModBlocks.POSTER_LANDSCAPE.get()};
        Direction[] sides = {Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH};
        for (int s = 0; s < sides.length; s++) {
            Direction out = sides[s];
            PosterBlock poster = posters[s];
            Direction along = out.getClockWise();
            int sideMid = out.getAxis() == Direction.Axis.Z ? (min.getX() + max.getX()) / 2 : (min.getZ() + max.getZ()) / 2;
            int[] offsets = {0, -3, 3, -6, 6, -9, 9};
            placed:
            for (int off : offsets) {
                for (int dy = 1; dy <= 3; dy++) {
                    int y = groundY + dy;
                    BlockPos start;
                    if (out == Direction.SOUTH) start = new BlockPos(sideMid + off, y, max.getZ() + 1);
                    else if (out == Direction.NORTH) start = new BlockPos(sideMid + off, y, min.getZ() - 1);
                    else if (out == Direction.EAST) start = new BlockPos(max.getX() + 1, y, sideMid + off);
                    else start = new BlockPos(min.getX() - 1, y, sideMid + off);
                    BlockPos cell = start;
                    int depth = (out.getAxis() == Direction.Axis.Z ? max.getZ() - min.getZ() : max.getX() - min.getX()) / 2;
                    for (int i = 0; i < depth; i++) {
                        BlockPos next = cell.relative(out.getOpposite());
                        if (level.getBlockState(next).isSolidRender(level, next)) {
                            if (BuildHelper.placePoster(level, cell, out, poster, false)) break placed;
                            break;
                        }
                        if (!level.getBlockState(next).isAir()) break;
                        cell = next;
                    }
                }
            }
        }
    }

    // ================================================================== procedural fallback
    /** A complete clinic generated in code, scaled by tier (17x17 at tier 1 up to 35x35 at tier 10). */
    public static void buildProcedural(ServerLevel level, BlockPos pos, int tier) {
        int w = 15 + tier * 2;
        int d = 15 + tier * 2;
        int h = 6 + tier / 3;
        int x0 = pos.getX() - w / 2, z0 = pos.getZ() - d / 2;
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        int y0 = pos.getY() - 1;
        int yTop = y0 + h;
        BlockState accent = ACCENTS[(tier - 1) % ACCENTS.length];
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        BuildHelper.clear(level, new BlockPos(x0 - 1, y0, z0 - 1), new BlockPos(x1 + 1, yTop + 1, z1 + 1));

        int xWall = x0 + (w * 2) / 3; // experimental ward = east third
        // floor
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                BlockState f;
                if (x == x0 || x == x1 || z == z0 || z == z1) f = quartz;
                else if (x > xWall) f = ((x + z) & 1) == 0 ? Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState() : white;
                else f = ((x + z) & 1) == 0 ? accent : white;
                BuildHelper.set(level, x, y0, z, f);
            }
        // outer walls
        for (int y = y0 + 1; y < yTop; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean ex = x == x0 || x == x1, ez = z == z0 || z == z1;
                    if (!ex && !ez) continue;
                    boolean pil = (ex && ez) || (ez && (x - x0) % 4 == 0) || (ex && (z - z0) % 4 == 0);
                    BlockState s = pil ? pillar : y == y0 + 1 ? accent : (y == y0 + 2 || y == y0 + 3) ? glass : white;
                    BuildHelper.set(level, x, y, z, s);
                }
        // roof + lights
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                boolean light = !border && (x - x0) % 4 == 2 && (z - z0) % 4 == 2;
                BuildHelper.set(level, x, yTop, z, border ? accent : light ? Blocks.SEA_LANTERN.defaultBlockState() : quartz);
            }
        // main entrance (south)
        int doorX = x0 + (xWall - x0) / 2;
        for (int x = doorX - 1; x <= doorX + 1; x++)
            for (int y = y0 + 1; y <= y0 + 3; y++) BuildHelper.set(level, x, y, z1, air);
        // glass partition for the experimental ward, with a doorway near the south end
        for (int z = z0 + 1; z < z1; z++)
            for (int y = y0 + 1; y < yTop; y++) {
                boolean doorway = z >= z1 - 4 && z <= z1 - 3 && y <= y0 + 3;
                BuildHelper.set(level, xWall, y, z, doorway ? air : y == y0 + 1 ? quartz : glass);
            }

        // reception counter + register (customers queue southward)
        int counterZ = z0 + 4;
        for (int x = doorX - 3; x <= doorX + 3; x++) {
            BuildHelper.set(level, x, y0 + 1, counterZ, (x == doorX - 3 || x == doorX + 3) ? pillar : Blocks.POLISHED_DIORITE.defaultBlockState());
        }
        BlockPos registerPos = new BlockPos(doorX, y0 + 2, counterZ);
        BuildHelper.placeRegister(level, registerPos, Direction.SOUTH);
        BuildHelper.set(level, doorX - 2, y0 + 2, counterZ, Blocks.BREWING_STAND.defaultBlockState());
        BuildHelper.set(level, doorX + 2, y0 + 2, counterZ, Blocks.POTTED_RED_TULIP.defaultBlockState());
        for (int x = doorX - 4; x <= doorX + 4; x++) {
            if (x <= x0 || x >= xWall) continue;
            BuildHelper.set(level, x, y0 + 1, z0 + 1, Blocks.BOOKSHELF.defaultBlockState());
            BuildHelper.set(level, x, y0 + 2, z0 + 1, Blocks.BOOKSHELF.defaultBlockState());
        }
        BuildHelper.spawnPharmacist(level, new BlockPos(doorX, y0 + 1, counterZ - 2), Direction.SOUTH.toYRot());

        // waiting area benches along the west wall
        BlockState bench = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        for (int z = counterZ + 3; z < z1 - 1; z += 2) BuildHelper.set(level, x0 + 1, y0 + 1, z, bench);
        BuildHelper.set(level, x0 + 1, y0 + 1, z0 + 1, Blocks.POTTED_FERN.defaultBlockState());
        BuildHelper.set(level, xWall - 1, y0 + 1, z1 - 1, Blocks.POTTED_FERN.defaultBlockState());

        // experimental ward: main exam bed (head against the north wall, Line 2 queues southward)
        int wardX = xWall + (x1 - xWall) / 2;
        BlockPos bedFoot = new BlockPos(wardX, y0 + 1, z0 + 2);
        ExaminationBedBlock.placeBed(level, bedFoot, Direction.NORTH);
        BuildHelper.spawnScientist(level, new BlockPos(wardX + 2, y0 + 1, z0 + 2), Direction.WEST.toYRot());
        BuildHelper.set(level, wardX - 2, y0 + 1, z0 + 1, Blocks.BREWING_STAND.defaultBlockState());
        BuildHelper.set(level, wardX + 2, y0 + 1, z0 + 1, Blocks.CAULDRON.defaultBlockState());
        // extra decorative beds along the ward's east wall for higher tiers
        int extraBeds = (tier - 1) / 3;
        for (int i = 0; i < extraBeds; i++) {
            int bz = z0 + 6 + i * 3;
            if (bz + 1 >= z1 - 4) break;
            ExaminationBedBlock.placeBed(level, new BlockPos(x1 - 2, y0 + 1, bz), Direction.EAST);
        }

        // posters outside
        BuildHelper.placePoster(level, new BlockPos(doorX - 4, y0 + 3, z1 + 1), Direction.SOUTH, ModBlocks.POSTER_LANDSCAPE.get(), true);
        BuildHelper.placePoster(level, new BlockPos(doorX + 3, y0 + 3, z1 + 1), Direction.SOUTH, ModBlocks.POSTER_SQUARE.get(), true);
        BuildHelper.placePoster(level, new BlockPos(x0 - 1, y0 + 2, (z0 + z1) / 2), Direction.WEST, ModBlocks.POSTER_BILLBOARD.get(), true);
        BuildHelper.placePoster(level, new BlockPos(x1 + 1, y0 + 2, (z0 + z1) / 2), Direction.EAST, ModBlocks.POSTER_BILLBOARD.get(), true);
        if (tier >= 5) {
            BuildHelper.placePoster(level, new BlockPos(doorX, y0 + 3, z0 - 1), Direction.NORTH, ModBlocks.POSTER_LANDSCAPE.get(), true);
        }
    }

    private ClinicBuilder() {}
}
