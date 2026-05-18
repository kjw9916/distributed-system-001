package lab.dist.storage;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.recovery.RecoveryManager;

public final class FileStorageEngine implements StorageEngine {
    private static final String VERSION_LINE = "VERSION|1";
    private static final String ACCOUNT_PREFIX = "ACCOUNT|";
    private static final String APPLIED_PREFIX = "APPLIED|";

    private final Path snapshotPath;
    private BankState state;

    public FileStorageEngine(Path snapshotPath) throws IOException {
        this.snapshotPath = snapshotPath;
        this.state = loadCurrentState();
    }

    @Override
    public Path snapshotPath() {
        return snapshotPath;
    }

    @Override
    public BankState loadCurrentState() throws IOException {
        BankState loaded = new BankState();
        if (!Files.exists(snapshotPath)) {
            this.state = loaded;
            return loaded.copy();
        }

        for (String line : Files.readAllLines(snapshotPath, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank() || line.equals(VERSION_LINE)) {
                continue;
            }
            if (line.startsWith(ACCOUNT_PREFIX)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length == 3) {
                    loaded.setBalance(parts[1], new BigDecimal(parts[2]));
                }
            } else if (line.startsWith(APPLIED_PREFIX)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    loaded.markApplied(parts[1]);
                }
            }
        }

        this.state = loaded;
        return loaded.copy();
    }

    @Override
    public void persistCommand(AccountCommand command) throws IOException {
        state.apply(command);
        writeSnapshot(state);
    }

    @Override
    public void writeSnapshot(BankState newState) throws IOException {
        Path parent = snapshotPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = snapshotPath.resolveSibling(snapshotPath.getFileName() + ".tmp");
        List<String> lines = new ArrayList<>();
        lines.add(VERSION_LINE);
        newState.balancesView().forEach((accountId, balance) -> lines.add(ACCOUNT_PREFIX + accountId + "|" + balance.toPlainString()));
        newState.appliedRequestIdsView().forEach(requestId -> lines.add(APPLIED_PREFIX + requestId));

        Files.write(tempFile, lines, StandardCharsets.UTF_8);
        Files.move(tempFile, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        this.state = newState.copy();
    }

    @Override
    public BankState recover(RecoveryManager recoveryManager) throws IOException {
        BankState recovered = recoveryManager.recover(loadCurrentState(), this);
        this.state = recovered.copy();
        return recovered.copy();
    }

    public BankState currentState() {
        return state.copy();
    }
}
