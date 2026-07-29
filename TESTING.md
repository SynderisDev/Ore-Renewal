# Compatibility test report

Tested on a dedicated NeoForge 21.1.233 development server running Minecraft 1.21.1.

## Mods

- Mekanism 10.7.19.85
- Mystical Agriculture 8.0.27
- Cucumber Library 8.0.16

## Upgrade sequence

1. Created and saved an existing world without the external ore mods.
2. Added Mystical Agriculture and Cucumber.
3. Ore Renewal detected `mysticalagriculture:inferium_ore` and
   `mysticalagriculture:prosperity_ore`.
4. Processed 49 existing chunks with 98 successful placements.
5. Added explicit Mekanism safe-feature compatibility.
6. Ore Renewal detected 10 normal Mekanism ore features.
7. Processed 34 existing chunks with 340 feature runs and 308 successful
   placements.
8. Restarted the server with the same mod set. Ore Renewal reported zero
   processed chunks and zero feature runs, confirming completed migrations were
   persisted and not duplicated.

No retrogen exceptions or unprimed-heightmap warnings occurred in the final
Mekanism run.

## Existing-build persistence

Before forcing another migration, the test world received a 1,728-block
underground structure containing:

- a glass shell;
- a mined-out air volume;
- a stone-brick floor;
- a chest containing one diamond;
- a crafting table;
- a diamond block.

The structure was cloned as an exact comparison snapshot. After Mystical
Agriculture retrogen, after Mekanism retrogen, and after a full save/stop/reload,
Minecraft's `execute if blocks ... all` comparison still matched all 1,728
blocks. Individual air, glass, floor, chest, crafting-table, and diamond-block
checks passed, and the chest still contained its diamond.

Minecraft has no natural-versus-player-placed provenance for plain stone,
deepslate, or netherrack. A new ore that explicitly targets one of those blocks
can therefore replace a player-placed instance. This engine limitation is
documented in the README; a world backup remains recommended.
