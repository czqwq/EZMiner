# Minesweeper special mode — marker drift/residue + auto-reveal

Scope: `chain/execution/MinesweeperModeHandler`, `chain/execution/LootGamesMinesweeperBridge`,
`client/render/MinerRenderer`. Reference sources: `tmp/LootGames-master/src/main/java/ru/timeconqueror/lootgames/…`.

## 1. Symptom

In special → minesweeper mode EZMiner auto-flags bombs and draws an orange-red wireframe box on
each flagged cell. Two artefacts were reported:

1. **Residue (残留)** — boxes stay on screen after the mine they describe is no longer there
   (level completed, flag removed, game reset).
2. **Drift (漂移)** — after a level is completed the boxes are no longer on their mines: they sit
   exactly **one cell north-west** of the previous board's cells.

## 2. Root cause

### 2.1 The board moves when a level is completed

`BoardLootGame.getBoardOrigin()` (LootGames `api/minigame/BoardLootGame.java:79-82`) centres the
*current* board inside the *allocated* area:

```java
int offset = getAllocatedBoardSize() - getCurrentBoardSize();
return getMasterPos().mutable().move(1, 0, 1).move(offset / 2, 0, offset / 2).immutable();
```

`getAllocatedBoardSize()` is the stage-4 size (`GameMineSweeper.getAllocatedBoardSize()`), and the
default stage radii are `2,3,4,5` (`common/config/ConfigMS.java:28-31`) → board sizes `5,7,9,11`,
allocated `11`. So `offset/2` is `3,2,1,0` and **the origin moves one block in −X/−Z per level**.
The engine agrees that world position is derived from the origin
(`convertToBlockPos(Pos2i)` = `getBoardOrigin().offset(x, 0, y)`, `BoardLootGame.java:75-77`).

A position that was board cell `(cx, cz)` in level *N* is cell `(cx+1, cz+1)` in level *N+1* —
i.e. a stale marker is exactly one cell off, which is the reported "drift".

### 2.2 The old clear rule could not see the level transition

On a win, `GameMineSweeper.StageWaiting.onLevelSuccessfullyFinished()`
(`minigame/minesweeper/GameMineSweeper.java:110-127`) calls
`board.resetBoard(stageSnapshot.getBoardSize(), …)`, which nulls the board **and immediately stores
the new size** (`MSBoard.resetBoard`, `MSBoard.java:87-92`), then `currentLevel++`.

EZMiner's clear rule was:

```java
boolean gameActive = isGameActiveCached(player);       // 500 ms TTL around bridge.isAnyGameActive
if (wasGameActive && !gameActive && !detectedBombs.isEmpty()) { reset(); send PacketMinesweeperClear; }
```

`isAnyGameActive` requires `game.isBoardGenerated()` → `board != null`, so the level transition is
only visible in the **short window between `resetBoard()` and the player's next click** (which
re-generates the board). The 500 ms TTL cache samples that window at most every 500 ms, so a
transition that happens between two samples is invisible: `gameActive` is `true` before and after,
`wasGameActive` never observes a `false`, and **no clear is ever sent**. The level-*loss* path
(`StageDetonating → StageExploding → StageWaiting`) leaves `StageWaiting` for seconds and was
therefore always caught — the drift only ever showed up on wins.

### 2.3 The state itself could not tell "still valid" from "stale"

Two more consequences of storing a bare world position per flag:

* the auto-flag dedup set (`detectedBombs`, keyed `"dim:x:y:z"`) survived the level transition and
  then **blocked re-flagging the real bomb** that had drifted into that world position;
* `resendMarks` on a fresh key press re-sent the whole stale list without any re-check;
* a flag the player right-clicked away (`FLAG → QUESTION_MARK → NO_MARK`) left its box behind.

### 2.4 Client-side residue (secondary)

All render paths share one `SpaceCalculator` and one VBO/EBO (`MinerRenderer.renderCache`) and
decide whether to rebuild from per-path version counters. The special-mode branches only reset
their own counter when a *preview founder* happened to be running, so switching minesweeper ↔
sudoku while holding the chain key could keep the previous path's geometry and draw it with the
new path's colour/indices (Sudoku fills rendered as orange minesweeper markers, and vice versa)
until the version happened to change.

## 3. Fix

| File | Change |
| --- | --- |
| `LootGamesMinesweeperBridge` | `snapshotBoards(World)` returns one `BoardView` per generated `StageWaiting` board (master key, live game/board/stage, size, origin). `invalidateStaleMarks(views, marks)` drops any `Mark` whose board is gone, whose size/origin changed (level transition), or whose cell is no longer a hidden, FLAG-marked bomb. `flagNearestBomb` re-checks the live size/origin before it writes (`isGeometryCurrent`) and appends a `Mark` (world position **plus** board cell + geometry). |
| `MinesweeperModeHandler` | Owns `List<Mark>`; **re-validates every tick** against the 500 ms board snapshot (and re-syncs the client with Clear + resend when something was dropped) and clears everything when no board is waiting. A key re-press (`resendMarks`) only sets a flag: the packet handler may run on the netty IO thread, so the republish happens in the next tick with a **fresh** snapshot and the same validation. The 500 ms TTL for the expensive TE scan is kept. |
| `MinerRenderer` | New `lastRenderKind` + `noteRenderKind()`: entering minesweeper/sudoku/preview invalidates the other paths' version counters, so a path switch always re-uploads its own geometry. `lastIndexCount` is deliberately untouched so a frozen preview survives. |

Because validation runs per tick (not per probe), the stale boxes are gone within one tick of the
board changing, and the probe cadence (`minesweeperProbeCooldownSeconds`, default 5 s) no longer
delays the cleanup.

## 4. Feature — flag every bomb first, then open the board (two phases)

Phase 1 — **flagging only**: `LootGamesMinesweeperBridge.flagNearestBomb` flags exactly **one**
still-unflagged bomb per probe (the probe cadence is `minesweeperProbeCooldownSeconds`), and reveals
nothing. The board therefore stays exactly as the player left it while the flags appear one by one.

Phase 2 — **one opening pass**: as soon as `areAllBombsFlagged(view)` holds, the handler calls
`revealAllSafeCells`, which walks the whole board, re-checks the **live** state of every cell and
calls the game's own `StageWaiting.revealField(EntityPlayerMP, Pos2i)` for each cell that is hidden
and not a bomb. Revealing an `EMPTY` cell still makes LootGames cascade into its own neighbours
(`GameMineSweeper.revealField` → `revealAllNeighbours(..., true)`, `GameMineSweeper.java:326-404`),
and cells a cascade already opened are skipped by the later iterations. That pass completes the
level (every non-bomb is open, every bomb is still hidden), which resets the board for the next deal.

`areAllBombsFlagged` counts, on the live board, the bombs that carry a `FLAG` and compares them
with `MSBoard.getBombCount()` — a mis-flag on a safe cell does not count because the cell type is
checked first, and a partially flagged board never qualifies. Flags the player placed by hand count
too, so the opening pass fires as soon as the last bomb is covered by anyone. A `QUESTION_MARK` on a
real bomb is treated as *unresolved*: EZMiner cycles it to `FLAG` (`Mark#getNext` goes
QUESTION_MARK → NO_MARK → FLAG, so two cycles are applied), otherwise one question mark could stall
the phase forever.

**Why not "open the neighbours of each flagged bomb":** LootGames only cascades out of an `EMPTY`
cell, and `EMPTY` means *zero adjacent bombs* — so an `EMPTY` cell can never be a neighbour of a
bomb. Opening only a flagged bomb's eight neighbours therefore left every blank pocket the first
cascade did not reach dark forever ("独立揭不了的格子"). Opening the whole board at flag time would
fix that but turns the mode into a one-shot solver, which is not the design: the flags are supposed
to accumulate first, and the safe side opens at the end.

Safety details:

* bombs are **never** revealed (that would detonate the board) — `getType(cell) == BOMB` is checked
  on the live board for every cell, so an accidental game over is impossible;
* the opening pass runs only while `areAllBombsFlagged` is true, so a board that still has hidden
  unflagged bombs is never touched;
* the reveal stops cell-by-cell once `MSBoard.isGenerated()` turns false — a reveal can complete the
  level, and LootGames then nulls the board inside the same call stack;
* marks are deliberately ignored: a flag/question mark the server knows to be on a safe cell must
  not keep that cell dark. Marks on real bombs are never touched because bombs are skipped;
* `revealField` is looked up in its own `try/catch`, so a LootGames build without that method logs a
  warning and only loses the auto-reveal; the auto-flagging half keeps working.

There is deliberately **no new config switch** — the existing auto-flag has none either, and the
mutation-scope question (which boards may be touched at all) is still the deferred V02 product
decision (`docs/review/agent-teams/fix-report.md` §V02). Ask if an `enableMinesweeperAutoReveal`
row (or a "solve everything vs. only open around the flag" switch) is wanted; wiring one costs a
server-config field + packet + GUI row + two locales.

## 5. Follow-up bug — the board became permanently uninteractable

Reported as: *"过关后进入下一关,明明还没开盘就能触发自动解题,导致玩家再也无法互动该棋盘"*
(level completed → next level → auto-solve runs while the board still looks unopened → the board
can never be clicked again; releasing and re-pressing the chain key does not help).

### 5.1 Mechanism

The client only ever learns the board from a **full** state transfer:

* `MSMasterRenderer` draws the "unopened" grid whenever `game.cIsGenerated` is false
  (`client/render/tile/MSMasterRenderer.java:41-47`);
* `cIsGenerated` is written in exactly one place — `GameMineSweeper.readNBT(nbt, SYNC)`
  (`minigame/minesweeper/GameMineSweeper.java:206-230`) — and that runs for the tile-entity update
  packet (`SyncableTile.saveAndSync()` → `markBlockForUpdate` → `getDescriptionPacket`) and for
  `SPMSGenBoard` (`GameMineSweeper.generateBoard`).

Those two arrive over **different channels** (vanilla tile-entity update vs. LootGames' FML packet),
so their order is not guaranteed. With EZMiner auto-solving, a level ends immediately after the deal,
so the level-end sync ("is_generated = false") and the *next* deal's `SPMSGenBoard`
("is_generated = true") are only a moment apart — if the level-end sync lands last, the client is
left with `cIsGenerated = false` (unopened grid) while the **server's board is generated**.
From then on:

* the player's clicks reach the server, but `StageWaiting.onClick` takes the *already generated*
  branch, so `generateBoard` — the only place that sends `SPMSGenBoard` — is never called again;
* the client keeps drawing the unopened grid, so the reveals look like "no reaction";
* the desync therefore never heals on its own: **permanently uninteractable**.

### 5.2 Fix

| Where | Change |
| --- | --- |
| `LootGamesMinesweeperBridge.syncClient` | Pushes the authoritative state once through `LootGame.saveAndSync()` (reflectively). Harmless when the client was in sync, and it repairs it from either direction when it was not. |
| `LootGamesMinesweeperBridge.boardSignature` | Deterministic signature (master key + size + origin, sorted) of the board set in a snapshot, so "a different board than last tick" is cheap to detect. |
| `MinesweeperModeHandler.tick` | On a signature change with a board present (fresh deal / completed level / new attempt / new game): wait `DEAL_SETTLE_MS = 500 ms` before probing and re-push the state for the next `CLIENT_RESYNC_TICKS = 3` ticks. After a successful probe the state is pushed again — but only while the board is **still generated**, so EZMiner never emits a "not generated" sync of its own around a win. |

Net effect: EZMiner no longer acts in the same packet window as the deal (it waits half a second and
re-syncs first), and if the client is ever left out of sync it is repaired instead of staying broken.

## 6. How to verify in game

1. Enter a LootGames minesweeper board, switch EZMiner to special → minesweeper, hold the chain key.
2. **Flagging phase:** exactly one new flag appears per cooldown, and **no cell opens** while the
   board still has unflagged bombs. The flag counter on the LootGames HUD must go down by one per
   flag, and the board must otherwise look exactly as the player left it.
3. **Opening pass:** when the last bomb is flagged (by EZMiner or by hand), the safe side must open
   in one go — no blank pocket and no numbered cell left dark, no unrevealed "island" anywhere on
   the board — and the level completes.
4. The orange boxes of the finished level must disappear immediately instead of sitting one cell
   north-west on the new board, and the new level's bombs must be flagged normally.
5. Walk through **all** levels in a row (deal → flag phase → opening pass → next deal …): every deal
   must open the board on the client. If a board is ever rendered as unopened, EZMiner's next action
   must restore it within about a second — the board must never stay in the "unopened but
   uninteractable" state even after releasing and re-pressing the chain key.
6. Right-click a flagged cell twice (FLAG → QUESTION_MARK → NO_MARK): its box must disappear on the
   next tick. Placing the flags by hand must also trigger the opening pass once the board is full.
7. Release and re-press the chain key after a level transition: no old box may come back.
8. While holding the key, switch minesweeper → sudoku → minesweeper: each mode must draw only its
   own boxes (orange mines / green fills), never the other mode's.
