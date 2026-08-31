# Compatibility test report

## Automated headless verification

The repository has three automated test layers, none of which launches a
Minecraft client:

- `./gradlew test` runs the fast JVM unit tests.
- `./gradlew runGameTestServer` starts NeoForge's dedicated GameTest server,
  loads Ore Renewal against real registries and chunks, runs all required game
  tests, and exits non-zero if a required test fails.
- `./gradlew lifecycleGameTest` starts a sequence of dedicated GameTest server
  processes. Each scenario reuses its saved world while changing the loaded mod
  set between restarts.

GitHub Actions runs all three layers on Java 21 for every pull request and for
pushes to `main` and `fix/**` branches. The GameTest sources live in the
separate `gameTest` source set and are not included in the release JAR.

Every lifecycle JVM must write its own phase-completion marker after its
assertions and a synchronous world save. Gradle deletes that marker before the
phase and fails if the exact marker is absent afterward. This prevents
Minecraft's zero-test, zero-exit-code behavior from producing a false-green CI
run. The lifecycle invocation is serialized to keep several memory-heavy
dedicated servers from contending on one GitHub runner.

The initial server tests verify that Ore Renewal loads on a physical dedicated
server without a client, discovers real vanilla ore placed features in biome
generation settings, resolves their generation step in a real chunk, and that
the chunk-load handler respects NeoForge's new-chunk flag when applying the
exclusion sentinel. Each GameTest starts with a fresh world directory under
`build/run-gametest`.

The lifecycle suite covers these install orders:

| Scenario | Real server phases | Core assertions |
| --- | --- | --- |
| Fresh world | Ore Renewal -> fixture A -> new post-A chunk -> fixture B -> restart | Existing chunks receive A; the new chunk is stamped at the current revision immediately; both cohorts later receive B without duplicating A; counts persist across restart. |
| Existing world | Vanilla -> Ore Renewal -> fixture A -> restart | A legacy chunk with no attachment receives A once after the later feature revision and is unchanged on restart. |
| Established modded world | Vanilla -> fixture A -> fixture B -> Ore Renewal -> restart | Pre-A chunks receive A+B; A-era chunks preserve A and receive B; A+B-era chunks preserve both; all cohorts remain unchanged on restart. |

Fixture A and B are independent low-code data-pack mods. Fixture A uses the
standard `ORE` generator and fixture B uses `SCATTERED_ORE`. Each targets a
different artificial substrate confined to the safe interior of the test
chunk, so neighboring feature runs cannot contaminate either historical
presence checks or marker-count assertions.
The substrates occupy separate fixed-height strata. Their test-only biome tags
cover normal overworld biomes plus a void fallback, and
every phase asserts that the fixture is present in the actual candidate biome
at the placement height. The test fixture omits the redundant placement-time
biome predicate after making that assertion, keeping synthetic placement
independent of registry-holder identity. Each phase also re-forces the complete
3x3 cohort before migration so accelerated server ticks cannot unload a needed
neighbor. The harness re-primes
world-generation heightmaps after installing its artificial ore strata so the
standard generators exercise those targets rather than rejecting positions
above the intentionally empty GameTest terrain.
The phases run in separate JVMs against the same on-disk world,
so profile SavedData, chunk attachments, new-chunk exclusion, historical
presence checks, and restart idempotency are exercised through real persistence.
All lifecycle worlds and diagnostics are kept under `build/run-lifecycle`.

Focused JVM tests also cover profile serialization and feature re-addition,
tick throttling, queue ordering and physical deduplication, next-tick retry
barriers, fairness behind healthy work, and the rule that a failed feature
batch must not commit a chunk revision. Retried batches provide at-least-once
execution: an earlier successful feature can run again if a later feature in
the same batch fails, so opted-in custom generators should be idempotent.

The fresh-world lifecycle keeps glass, mined air, a crafting table, and a chest
with a diamond unchanged through both migrations and a restart. This validates
the standard-generator preservation path without claiming that Minecraft can
distinguish natural stone from player-placed stone.

Release candidates have an additional manual gate described in
[`RELEASING.md`](RELEASING.md). CI never publishes a release, and only the exact
JAR manually playtested and approved by the repository owner is eligible for
publishing.

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
