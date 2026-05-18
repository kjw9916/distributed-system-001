package lab.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.domain.OperationType;
import lab.dist.recovery.RecoveryManager;
import lab.dist.storage.FileStorageEngine;
import lab.dist.wal.WalEntryType;
import lab.dist.wal.WalRecord;
import lab.dist.wal.WalWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void recoversCommandRecordedOnlyInWal() throws IOException {
        Path snapshot = tempDir.resolve("accounts.db");
        Path wal = tempDir.resolve("wal.log");
        FileStorageEngine storage = new FileStorageEngine(snapshot);

        BankState initial = new BankState();
        initial.setBalance("user-01", new BigDecimal("100.00"));
        storage.writeSnapshot(initial);

        WalWriter writer = new WalWriter(wal);
        writer.appendCommand(new AccountCommand(
                "req-1",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                OperationType.DEPOSIT,
                "user-01",
                "",
                new BigDecimal("25.00")));

        BankState recovered = storage.recover(new RecoveryManager(wal));
        assertEquals(new BigDecimal("125.00"), recovered.balanceOf("user-01"));
        assertEquals(1, recovered.appliedRequestIdsView().size());
    }

    @Test
    void ignoresCorruptedWalTail() throws IOException {
        Path snapshot = tempDir.resolve("accounts.db");
        Path wal = tempDir.resolve("wal.log");
        FileStorageEngine storage = new FileStorageEngine(snapshot);

        BankState initial = new BankState();
        initial.setBalance("user-01", new BigDecimal("100.00"));
        storage.writeSnapshot(initial);

        Files.writeString(wal, String.join(System.lineSeparator(),
                new WalRecord(
                        WalEntryType.COMMAND,
                        "req-1",
                        java.time.Instant.parse("2026-01-01T00:00:00Z"),
                        OperationType.DEPOSIT,
                        "user-01",
                        "",
                        new BigDecimal("20.00")).serialize(),
                "BROKEN|TAIL"), StandardCharsets.UTF_8);

        BankState recovered = storage.recover(new RecoveryManager(wal));
        assertEquals(new BigDecimal("120.00"), recovered.balanceOf("user-01"));
    }
}
