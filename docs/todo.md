# TODO — Future Optimizations

## Step 3: Direct Chunk Array Manipulation (HIGH RISK)

> **Status**: Planned, not implemented.  
> **Expected gain**: 50-100x per-block vs individual `World.setBlock` for the actual
> array-write step.  
> **Risk level**: HIGH — must correctly maintain block refcounts, height map, and light
> state. Incorrect implementation causes ghost chunks, light glitches, or crashes.

### Approach

Instead of going through `World.setBlock → Chunk.func_150807_a` (which triggers
`onBlockPreDestroy`, `breakBlock`, light propagation, tile entity cleanup, and
6 neighbor notifications), directly manipulate the `ExtendedBlockStorage` internal
arrays:

```java
// 1. Get Chunk reference once per chunk
Chunk chunk = world.getChunkFromChunkCoords(cx, cz);

// 2. Get sub-chunk
ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[y >> 4];
if (ebs == null) return false; // sub-chunk is entirely air

// 3. Direct array write
byte[] lsb = ebs.getBlockLSBArray();
int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
lsb[index] = 0; // set to air (block ID 0)

// 4. Clear metadata
ebs.getMetadataArray().set(x, y & 15, z, 0);

// 5. Clear MSB if needed
NibbleArray msb = ebs.getBlockMSBArray();
if (msb != null) msb.set(x, y & 15, z, 0);

// 6. Decrement refcount (needs @Accessor mixin)
ebs.blockRefCount--;

// 7. Mark chunk dirty
chunk.isModified = true;
```

### Things that need handling

1. **heightMap update**: After setting blocks to air, `Chunk.heightMap` must be updated
   for each affected X/Z column. The height map stores the highest non-air block Y
   for each column (indexed `x | z << 4`). If the block being removed was the highest
   block in its column, need to scan downward from the old height to find the new one.

2. **blockRefCount / tickRefCount**: `ExtendedBlockStorage.blockRefCount` tracks total
   non-air blocks per sub-chunk. Must decrement when setting to air.
   `tickRefCount` tracks blocks needing random ticks — if the removed block had
   `getTickRandomly() == true`, must also decrement `tickRefCount`.
   Both fields are package-private — need `@Accessor` mixin.

3. **Batch light recalculation**: After the entire batch, trigger one
   `world.updateLightByType(EnumSkyBlock.Block, ...)` or
   `world.func_147451_t(x, y, z)` (checkLight) for the affected region
   instead of per-block light updates.

4. **Drop collection**: Still need to call `block.harvestBlock()` and
   `block.dropXpOnBlockBreak()` for each block — these can't be batched since
   different blocks may have different fortune levels, silk touch, etc.

5. **Single batch-chunk packet**: Instead of 100+ individual `S23PacketBlockChange`
   packets, send one `S21PacketChunkData` per modified chunk at the end of the
   batch. This reduces network overhead dramatically.

6. **BreakEvent**: Fire a single `BlockEvent.BreakEvent` for the entire operation
   at the start (with the first block's coordinates as representative), or skip
   entirely for non-TE ore blocks (consistent with the Step 2 optimization).

7. **TileEntity safety**: Only apply this path to blocks where
   `!block.hasTileEntity(meta)` — same guard as the Step 2 fast path.

### Hodgepodge interaction notes

- `MixinChunk` hooks `func_150812_a` (setTileEntity) — not affected since we bypass
  tile entity management.
- `MixinWorld_PreventChunkLoading` wraps neighbor-change methods — not affected since
  we skip neighbor notifications entirely.
- `MixinWorldServer_PendingTickIndex` rewrites tick scheduling — not affected since
  `World.setBlock` is not called.
- `MixinWorld_CoFH_TE_Cache` hooks `setTileEntity` — not affected.
- **No known Hodgepodge conflicts** with direct `ExtendedBlockStorage` array writes.

### Implementation plan

1. Create `@Accessor` mixin for `ExtendedBlockStorage.blockRefCount` and
   `ExtendedBlockStorage.tickRefCount` (or use reflection).
2. Create a utility class `FastBlockWriter` that wraps the direct array manipulation.
3. Create a `@Accessor` interface for `Chunk.heightMap` and `Chunk.heightMapMinimum`.
4. In `BaseOperator.processBatchWithBatchedExhaustion`, after the batch completes:
   - For each modified chunk, send `S21PacketChunkData`.
   - Run one pass of `world.updateLightByType` for the affected region.
5. Extensive testing in singleplayer and multiplayer with various ore types.

### Why this is deferred

The Step 1 (BFS byte-array read) and Step 2 (fast harvest mixin) optimizations
already provide a ~5-10x overall speedup with low risk. Direct array manipulation
adds another ~2x but requires careful handling of several internal MC invariants.
It is best implemented after the first two steps are proven stable in production.
