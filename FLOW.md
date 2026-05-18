# Stage 1 Flow Guide

Read the project in this order:

1. `src/main/java/lab/dist/app/App.java`
2. `src/main/java/lab/dist/simulation/SimulationRunner.java`
3. `src/main/java/lab/dist/runtime/WalBackedBankService.java`
4. `src/main/java/lab/dist/wal/WalWriter.java`
5. `src/main/java/lab/dist/storage/FileStorageEngine.java`
6. `src/main/java/lab/dist/recovery/RecoveryManager.java`
7. `src/main/java/lab/dist/domain/BankState.java`

## One Request

Example: `user-02` receives a deposit of `65`.

1. `App.main(...)` chooses `demo`, `simulate`, or `recover`.
2. `SimulationRunner.run(...)` creates an `AccountCommand`.
3. `WalBackedBankService.handleCommand(...)` validates the command against the current state.
4. `WalWriter.appendCommand(...)` writes the command to `wal.log`.
5. If a crash is injected before snapshot persistence, the process stops here.
6. `FileStorageEngine.persistCommand(...)` applies the command to memory and rewrites `accounts.db`.
7. `WalWriter.appendCommit(...)` appends the commit marker.

## Recovery

1. `SimulationRunner.recover(...)` or restart logic creates a `RecoveryManager`.
2. `RecoveryManager.recover(...)` reads `wal.log` from top to bottom.
3. Each valid `COMMAND` record becomes an `AccountCommand`.
4. `BankState.apply(...)` replays only commands whose `requestId` is not already in the snapshot.
5. The recovered state is written back through `FileStorageEngine.writeSnapshot(...)`.

## Failure Points

- `AFTER_WAL_APPEND`: crash right after the command is flushed to `wal.log`
- `BEFORE_DATA_APPLY`: crash after the WAL append and validation, before `accounts.db` is rewritten
- `AFTER_DATA_APPLY`: crash after `accounts.db` is rewritten, before the commit marker is appended

## Files On Disk

- `wal.log`: append-only request history
- `accounts.db`: latest account balances and the set of applied request ids
