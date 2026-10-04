# Doctor Anant's Mega Clinic & The Expired Mutant Outbreak
NeoForge **1.21.1** · Java **21** · Mod ID `dranant`

## Build & run
```bash
./gradlew runClient     # launches a dev client with the mod
./gradlew build         # -> build/libs/dranant-1.0.0.jar  (drop into .minecraft/mods with NeoForge 21.1.x)
```
Open the folder in IntelliJ IDEA (File → Open → `build.gradle`) and let Gradle sync.
If Gradle reports the NeoForge or ModDevGradle version is missing, update `neo_version` in
`gradle.properties` / the `net.neoforged.moddev` version in `build.gradle` to the newest 21.1.x / 2.0.x release.

## How your attached files were mapped
| Attached file | Used for | How |
|---|---|---|
| blood_potion.zip | `expired_experimental_serum` | glTF rendered to a 64×64 icon, liquid recoloured dark crimson |
| low_poly_handcuffs.zip | `titanium_handcuffs` | glTF rendered to icon (titanium tint) + cuff cubes on the boss model |
| medical_syringe.zip | `universal_cure_syringe` | glTF rendered to icon, liquid recoloured cure-green |
| male_lab_coat_and_pants.zip | `dr_anant_coat` | glTF rendered to the item icon; canvas texture woven into the 64×32 armor layer |
| hospital_bed.zip | `examination_bed` | 2-block bed model built from the original material colours; icon rendered from glTF |
| structural_i_from_fps_creator_classic.zip | `item_display_pedestal` | block model (base/column/cap) textured with crops of the original stone texture |
| minecraft_mutant_zombie_download_free.zip | `mutant_blood_beast` | Blockbench glTF converted cube-for-cube into `MutantBloodBeastModel` (exact pivots/rotations/UVs), texture blood-tinted, glowing eyes layer |
| clinic_level_1..6.nbt | Clinic Summoners 1-6 | `data/dranant/structure/` (1.21 renamed `structures` → `structure`) |

> Minecraft cannot render raw glTF meshes, so items use `item/generated` icons rendered from your models
> (native 3D extrusion in hand and on pedestals), exactly as requested in Section 9.

### ⚠ Important: clinic_level_1/2/3.nbt are empty
Those three files contain a size but **zero blocks** (the conversion that produced them failed; DataVersion 4903).
The mod detects this and builds a **procedural clinic** for those tiers (and for tiers 7-10, which have no .nbt),
so every summoner works. Re-export them (Structure Block → SAVE in 1.21.1) and drop them into
`src/main/resources/data/dranant/structure/` to use your own builds. Levels 4, 5 and 6 load from your files; the
register, lab bed, both assistants and posters are auto-placed inside the building.

## Gameplay / recording script
1. Creative tab **Dr. Anant's Mega Clinic** has everything.
2. Place **Central Shop Spawner** → 15×7×15 shop with 12 floating pedestals, cash register and pharmacist.
3. Place a **Clinic Summoner (Level N)** → the clinic appears with the Assistant Pharmacist (counter) and Assistant Scientist (lab).
4. Type `#run diamond counter` in chat → HUD (top-right) starts counting. `#stop diamond counter` freezes it,
   `#reset diamond counter` zeroes it (also `/diamondcounter run|stop|reset`).
5. Villagers within 48 blocks queue: 70% Line 1 (medicine, 1-5 💎 into the register), 30% Line 2 (lab bed, 64 💎 drop).
   By default new customer villagers also walk in every 10 s (`config/dranant-common.toml`).
6. Use the **Expired Experimental Serum** on the patient lying on the Examination Bed → lightning → **Mutant Blood Beast**.
7. Fight it below 50% HP → **Titanium Handcuffs** (30 s) → **Universal Cure Syringe** → cured villager showers 128 💎 + rare loot.

Pedestals: right-click to buy. Creative + Shift + right-click with an item = display it; with diamonds = set price to stack size; empty hand = clear.
Right-click the cash register to collect its diamonds; Shift + right-click for queue status.

## Notes
* NeoForge 1.21.1 uses `META-INF/neoforge.mods.toml` (the successor of Forge's `mods.toml`).
* `new ResourceLocation(ns, path)` no longer exists in 1.21 — `ResourceLocation.fromNamespaceAndPath` is used.
* The Minecraft font has no 💎 glyph, so the HUD draws the real diamond item icon instead of the emoji.
* Custom sounds are defined in `sounds.json` as layers of vanilla sound events, so no .ogg files are needed.
* Model credits (CC-BY-4.0) are in `CREDITS.txt` — put them in your video description.
