# Distributed System Lab

Stage 1 is a single-process Java prototype that demonstrates write-ahead logging, crash recovery, and a simple user simulation on top of a file-backed bank ledger.

## Goals

- Append each write request to the WAL before mutating the data file
- Recover from crashes by replaying WAL records that were not reflected in the latest snapshot
- Simulate multiple users sending random requests and inject failures at controlled points

## Project Layout

- `accounts.db`: snapshot file with balances and applied request ids
- `wal.log`: append-only write-ahead log
- `src/main/java/lab/dist/app`: entrypoint
- `src/main/java/lab/dist/simulation`: scenario runner and fake users
- `src/main/java/lab/dist/runtime`: request lifecycle and crash injection
- `src/main/java/lab/dist/wal`: append-only log format and writer
- `src/main/java/lab/dist/storage`: `accounts.db` loading and persistence
- `src/main/java/lab/dist/recovery`: WAL replay on restart
- `src/main/java/lab/dist/domain`: account commands and in-memory state
- `FLOW.md`: read-this-first walkthrough of the runtime flow

## Commands

Examples assume a JDK is available and the Gradle wrapper can download its distribution.

```powershell
.\gradlew.bat run --args="demo"
.\gradlew.bat run --args="simulate --workspace C:\distributed-system\runs\demo1 --users 4 --requests 20 --crashAt 7 --failure AFTER_WAL_APPEND --recover true"
.\gradlew.bat run --args="recover --workspace C:\distributed-system\runs\demo1"
.\gradlew.bat test
```

## Failure Points

- `NONE`: no crash injection
- `AFTER_WAL_APPEND`: crash right after the WAL has been flushed
- `BEFORE_DATA_APPLY`: crash after logging but before the data snapshot changes
- `AFTER_DATA_APPLY`: crash after the data snapshot changes but before the WAL commit marker

## Notes

- The snapshot stores both account balances and applied request ids so recovery remains idempotent.
- Recovery ignores malformed WAL tail records to model a partially written final log line.
- `demo` resets each scenario workspace before running so repeated demos start from a clean state.
- This stage stays in a single process on purpose. Later stages can split storage and execution into multiple nodes.
