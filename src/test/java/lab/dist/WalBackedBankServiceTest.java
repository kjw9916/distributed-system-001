package lab.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.domain.OperationType;
import lab.dist.recovery.RecoveryManager;
import lab.dist.runtime.FailurePoint;
import lab.dist.runtime.SimulatedCrashException;
import lab.dist.runtime.WalBackedBankService;
import lab.dist.storage.FileStorageEngine;
import lab.dist.wal.WalWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalBackedBankServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void recoversCrashAfterDataApplyWithoutDoubleApplying() throws IOException {
        Path snapshot = tempDir.resolve("accounts.db");
        Path wal = tempDir.resolve("wal.log");
        FileStorageEngine storage = new FileStorageEngine(snapshot);
        BankState initial = new BankState();
        initial.setBalance("user-01", new BigDecimal("100.00"));
        storage.writeSnapshot(initial);

        WalBackedBankService service = new WalBackedBankService(storage, new WalWriter(wal));
        AccountCommand command = new AccountCommand(
                "req-1",
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                OperationType.DEPOSIT,
                "user-01",
                "",
                new BigDecimal("30.00"));

        assertThrows(SimulatedCrashException.class, () -> service.handleCommand(command, FailurePoint.AFTER_DATA_APPLY));

        FileStorageEngine restartedStorage = new FileStorageEngine(snapshot);
        BankState recovered = restartedStorage.recover(new RecoveryManager(wal));
        assertEquals(new BigDecimal("130.00"), recovered.balanceOf("user-01"));
        assertEquals(1, recovered.appliedRequestIdsView().size());
    }

    @Test
    void serializesConcurrentRequestsConsistently() throws Exception {
        Path snapshot = tempDir.resolve("accounts.db");
        Path wal = tempDir.resolve("wal.log");
        FileStorageEngine storage = new FileStorageEngine(snapshot);
        BankState initial = new BankState();
        initial.setBalance("user-01", new BigDecimal("1000.00"));
        initial.setBalance("user-02", new BigDecimal("1000.00"));
        storage.writeSnapshot(initial);

        WalBackedBankService service = new WalBackedBankService(storage, new WalWriter(wal));
        int tasks = 20;
        CountDownLatch latch = new CountDownLatch(tasks);
        List<Future<?>> futures = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < tasks; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        service.handleCommand(new AccountCommand(
                                "req-" + index,
                                java.time.Instant.now(),
                                OperationType.TRANSFER,
                                "user-01",
                                "user-02",
                                new BigDecimal("10.00")), FailurePoint.NONE);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "all tasks should finish");
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        BankState state = storage.loadCurrentState();
        assertEquals(new BigDecimal("800.00"), state.balanceOf("user-01"));
        assertEquals(new BigDecimal("1200.00"), state.balanceOf("user-02"));
        assertEquals(20, state.appliedRequestIdsView().size());
    }
}
