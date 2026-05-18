package lab.dist.simulation;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.domain.OperationType;
import lab.dist.recovery.RecoveryManager;
import lab.dist.runtime.FailurePoint;
import lab.dist.runtime.SimulatedCrashException;
import lab.dist.runtime.WalBackedBankService;
import lab.dist.storage.FileStorageEngine;
import lab.dist.wal.WalWriter;

public final class SimulationRunner {
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final String FOCUS_ACCOUNT_ID = "user-02";

    public void run(SimulationConfig config) throws IOException {
        Files.createDirectories(config.workspace());
        Path snapshotPath = config.workspace().resolve("accounts.db");
        Path walPath = config.workspace().resolve("wal.log");

        FileStorageEngine storage = new FileStorageEngine(snapshotPath);
        seedAccounts(storage, config.users());

        WalBackedBankService service = new WalBackedBankService(storage, new WalWriter(walPath));
        Random random = new Random(config.seed());
        boolean crashed = false;

        for (int i = 1; i <= config.requests(); i++) {
            AccountCommand command = nextCommand(random, config.users());
            FailurePoint point = i == config.crashAt() ? config.failurePoint() : FailurePoint.NONE;

            try {
                service.handleCommand(command, point);
                System.out.println("[apply] " + describe(command));
            } catch (SimulatedCrashException ex) {
                crashed = true;
                System.out.println("[crash] " + ex.getMessage() + " while processing " + describe(command));
                break;
            }
        }

        printState("state-before-recovery", storage.currentState());

        if (crashed && config.recoverAfterCrash()) {
            System.out.println("[recover] restarting runtime and replaying WAL");
            FileStorageEngine recoveredStorage = new FileStorageEngine(snapshotPath);
            RecoveryManager recoveryManager = new RecoveryManager(walPath);
            BankState recovered = recoveredStorage.recover(recoveryManager);
            printState("state-after-recovery", recovered);
        }
    }

    public void recover(Path workspace) throws IOException {
        Path snapshotPath = workspace.resolve("accounts.db");
        Path walPath = workspace.resolve("wal.log");
        FileStorageEngine storage = new FileStorageEngine(snapshotPath);
        RecoveryManager recoveryManager = new RecoveryManager(walPath);
        BankState recovered = storage.recover(recoveryManager);
        printState("manual-recovery", recovered);
    }

    public void runDemo(Path root) throws IOException {
        List<DemoScenario> scenarios = List.of(
                new DemoScenario(
                        "AFTER_WAL_APPEND",
                        root.resolve("after-wal"),
                        FailurePoint.AFTER_WAL_APPEND,
                        "user-02 gets +200 after the WAL write, so recovery should increase the balance.",
                        List.of(
                                command("demo-aw-1", OperationType.DEPOSIT, "user-01", "", 50),
                                command("demo-aw-2", OperationType.WITHDRAW, "user-03", "", 40),
                                command("demo-aw-3", OperationType.TRANSFER, "user-04", "user-01", 30),
                                command("demo-aw-4", OperationType.DEPOSIT, "user-02", "", 20),
                                command("demo-aw-5", OperationType.DEPOSIT, "user-02", "", 200))),
                new DemoScenario(
                        "BEFORE_DATA_APPLY",
                        root.resolve("before-data"),
                        FailurePoint.BEFORE_DATA_APPLY,
                        "user-02 loses 180 only after recovery because the crash happens before snapshot persistence.",
                        List.of(
                                command("demo-bd-1", OperationType.DEPOSIT, "user-01", "", 50),
                                command("demo-bd-2", OperationType.DEPOSIT, "user-02", "", 40),
                                command("demo-bd-3", OperationType.TRANSFER, "user-01", "user-04", 30),
                                command("demo-bd-4", OperationType.DEPOSIT, "user-03", "", 10),
                                command("demo-bd-5", OperationType.WITHDRAW, "user-02", "", 180))),
                new DemoScenario(
                        "AFTER_DATA_APPLY",
                        root.resolve("after-data"),
                        FailurePoint.AFTER_DATA_APPLY,
                        "user-02 already received +90 before the crash, so recovery should not apply it a second time.",
                        List.of(
                                command("demo-ad-1", OperationType.DEPOSIT, "user-04", "", 25),
                                command("demo-ad-2", OperationType.WITHDRAW, "user-01", "", 15),
                                command("demo-ad-3", OperationType.TRANSFER, "user-03", "user-04", 35),
                                command("demo-ad-4", OperationType.DEPOSIT, "user-01", "", 10),
                                command("demo-ad-5", OperationType.DEPOSIT, "user-02", "", 90))));

        for (DemoScenario scenario : scenarios) {
            resetWorkspace(scenario.workspace());
            System.out.println();
            System.out.println("=== scenario: " + scenario.name() + " ===");
            System.out.println("[explain] " + scenario.explanation());
            runScriptedScenario(scenario);
        }
    }

    private void runScriptedScenario(DemoScenario scenario) throws IOException {
        Files.createDirectories(scenario.workspace());
        Path snapshotPath = scenario.workspace().resolve("accounts.db");
        Path walPath = scenario.workspace().resolve("wal.log");

        FileStorageEngine storage = new FileStorageEngine(snapshotPath);
        seedAccounts(storage, 4);

        BankState initialState = storage.currentState();
        printState("state-initial", initialState);

        WalBackedBankService service = new WalBackedBankService(storage, new WalWriter(walPath));
        boolean crashed = false;

        for (int i = 0; i < scenario.commands().size(); i++) {
            AccountCommand command = scenario.commands().get(i);
            FailurePoint point = i == scenario.commands().size() - 1 ? scenario.failurePoint() : FailurePoint.NONE;

            try {
                service.handleCommand(command, point);
                System.out.println("[apply] " + describe(command));
            } catch (SimulatedCrashException ex) {
                crashed = true;
                System.out.println("[crash] " + ex.getMessage() + " while processing " + describe(command));
                break;
            }
        }

        BankState beforeRecovery = storage.currentState();
        printState("state-before-recovery", beforeRecovery);
        printFocusDelta("before-recovery", initialState, beforeRecovery);

        if (crashed) {
            System.out.println("[recover] restarting runtime and replaying WAL");
            FileStorageEngine recoveredStorage = new FileStorageEngine(snapshotPath);
            RecoveryManager recoveryManager = new RecoveryManager(walPath);
            BankState recovered = recoveredStorage.recover(recoveryManager);
            printState("state-after-recovery", recovered);
            printFocusDelta("after-recovery", initialState, recovered);
        }
    }

    private void resetWorkspace(Path workspace) throws IOException {
        if (!Files.exists(workspace)) {
            return;
        }

        List<Path> paths = Files.walk(workspace)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private void seedAccounts(FileStorageEngine storage, int users) throws IOException {
        if (!storage.currentState().balancesView().isEmpty()) {
            return;
        }

        BankState initial = new BankState();
        for (int i = 1; i <= users; i++) {
            initial.setBalance(userId(i), INITIAL_BALANCE);
        }
        storage.writeSnapshot(initial);
    }

    private AccountCommand command(String requestId, OperationType operationType, String accountId, String targetAccountId, int amount) {
        return new AccountCommand(
                requestId,
                Instant.now(),
                operationType,
                accountId,
                targetAccountId,
                BigDecimal.valueOf(amount));
    }

    private AccountCommand nextCommand(Random random, int users) {
        int kind = random.nextInt(3);
        int source = 1 + random.nextInt(users);
        BigDecimal amount = new BigDecimal(10 + random.nextInt(90));
        String requestId = UUID.randomUUID().toString();

        return switch (kind) {
            case 0 -> new AccountCommand(requestId, Instant.now(), OperationType.DEPOSIT, userId(source), "", amount);
            case 1 -> new AccountCommand(requestId, Instant.now(), OperationType.WITHDRAW, userId(source), "", amount);
            default -> {
                int target = source;
                while (target == source) {
                    target = 1 + random.nextInt(users);
                }
                yield new AccountCommand(requestId, Instant.now(), OperationType.TRANSFER, userId(source), userId(target), amount);
            }
        };
    }

    private String userId(int index) {
        return "user-" + String.format("%02d", index);
    }

    private String describe(AccountCommand command) {
        return command.operationType()
                + " requestId=" + command.requestId()
                + " account=" + command.accountId()
                + (command.targetAccountId() == null || command.targetAccountId().isBlank()
                ? ""
                : " target=" + command.targetAccountId())
                + " amount=" + command.amount().toPlainString();
    }

    private void printState(String label, BankState state) {
        System.out.println("[" + label + "] balances");
        state.balancesView().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> System.out.println("  " + entry.getKey() + " = " + entry.getValue().toPlainString()));
        System.out.println("[" + label + "] appliedRequestCount = " + state.appliedRequestIdsView().size());
    }

    private void printFocusDelta(String label, BankState initialState, BankState currentState) {
        BigDecimal initial = initialState.balanceOf(FOCUS_ACCOUNT_ID);
        BigDecimal current = currentState.balanceOf(FOCUS_ACCOUNT_ID);
        BigDecimal delta = current.subtract(initial);
        String sign = delta.signum() >= 0 ? "+" : "";
        System.out.println("[focus-" + label + "] " + FOCUS_ACCOUNT_ID
                + " started at " + initial.toPlainString()
                + ", now " + current.toPlainString()
                + ", delta " + sign + delta.toPlainString());
    }

    public static SimulationConfig fromArgs(String[] args) {
        Path workspace = Path.of("runs", "default");
        int users = 4;
        int requests = 20;
        int crashAt = -1;
        FailurePoint failurePoint = FailurePoint.NONE;
        boolean recover = true;
        long seed = 42L;

        List<String> list = new ArrayList<>(List.of(args));
        for (int i = 0; i < list.size(); i++) {
            String token = list.get(i);
            if (!token.startsWith("--") || i + 1 >= list.size()) {
                continue;
            }
            String value = list.get(i + 1);
            switch (token) {
                case "--workspace" -> workspace = Path.of(value);
                case "--users" -> users = Integer.parseInt(value);
                case "--requests" -> requests = Integer.parseInt(value);
                case "--crashAt" -> crashAt = Integer.parseInt(value);
                case "--failure" -> failurePoint = FailurePoint.valueOf(value);
                case "--recover" -> recover = Boolean.parseBoolean(value);
                case "--seed" -> seed = Long.parseLong(value);
                default -> {
                }
            }
            i++;
        }

        return new SimulationConfig(workspace, users, requests, crashAt, failurePoint, recover, seed);
    }

    private record DemoScenario(
            String name,
            Path workspace,
            FailurePoint failurePoint,
            String explanation,
            List<AccountCommand> commands) {
    }
}
