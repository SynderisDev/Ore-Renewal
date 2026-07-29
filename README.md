# Ore Renewal

Ore Renewal is a server-side NeoForge mod for Minecraft 1.21.1. It detects newly added ore-generation placed features after a modpack update and applies only those features when an older chunk is loaded.

## How it stays safe

- The first launch in each world/dimension records a baseline and does not generate anything.
- Later launches compare the current underground-ore feature IDs with that baseline.
- Every addition becomes a persistent world migration.
- New chunks are marked at generation time and never receive duplicate retro-generation.
- Existing chunks remember the last migration they completed.
- Work is throttled to one chunk per tick by default.

By default the mod recognizes standard `ORE` and `SCATTERED_ORE` configured features in every generation step. Checking every step is important because vanilla-style Nether ores use `UNDERGROUND_DECORATION`, not `UNDERGROUND_ORES`. Each feature retains its normal placement modifiers, biome filter, height, rarity, dimensions, target blocks, and biome restrictions.

## Existing-build safety

Ore Renewal never reruns the chunk generator, terrain noise, surfaces, carvers, structures, or biome decoration. It invokes only the newly added ore feature. Standard ore target rules replace only blocks declared replaceable by that ore, so existing air, mined tunnels, containers, machines, block entities, structures made from other materials, biomes, and terrain changes remain untouched.

Minecraft does not record whether an ordinary stone, deepslate, or netherrack block was placed by a player. If a build uses a block that the new ore explicitly declares replaceable, that block is indistinguishable from natural terrain. Keep a world backup for this edge case.

Nonstandard features from the `UNDERGROUND_ORES` step are disabled by default because their behavior cannot be proven safe. A pack author can opt in with `include_nonstandard_underground_features`, after auditing those features.

Retrogen waits until the target chunk and all eight neighboring chunks are already loaded. Ore veins can cross a chunk border, so this prevents the feature from generating or modifying an otherwise-unloaded boundary chunk.

Mekanism 10.7.x is explicitly recognized as a safe custom ore generator. Its normal ore features use target predicates equivalent to standard ore replacement. Mekanism's separate `_retrogen` features are not run, preventing the two systems from duplicating each other.

## Installing

1. Back up the world.
2. Put `ore_renewal-1.0.0.jar` in the server's `mods` folder.
3. Start the world once to establish the baseline.
4. Stop the server, add the new ore-producing mod or datapack, and start it again.
5. Existing chunks are processed as they load.

No client-side installation is required.

## If the new ores were added at the same time

The first scan cannot know which features existed before Ore Renewal was installed. In that case, run:

```text
/ore_renewal apply namespace:placed_feature_id
```

Command suggestions list the detected underground-ore placed features. This creates an explicit migration for that feature. Use `/ore_renewal status` to see queue and revision information.

## Configuration

The server config is `config/ore_renewal-server.toml`.

- `enabled`: pauses or resumes processing without losing history.
- `chunks_per_tick`: processing throttle; default `1`.
- `only_when_tick_has_time`: avoids adding work to an already busy tick.
- `include_nonstandard_underground_features`: opt in to custom features that are not standard ore generators.
- `log_every_n_chunks`: progress log interval; `0` disables progress lines.

## Limitations

- Detection is based on placed-feature IDs. Changing an existing ore feature in place does not count as adding a new ore.
- Minecraft cannot distinguish naturally generated stone from player-placed stone. Standard ore target rules may replace player-placed stone, so always keep a world backup.
- A custom nonstandard ore generator outside the `UNDERGROUND_ORES` step cannot be identified generically and is ignored.
- A chunk at the edge of the currently loaded area waits until its neighboring chunks are loaded; this is intentional boundary protection.
