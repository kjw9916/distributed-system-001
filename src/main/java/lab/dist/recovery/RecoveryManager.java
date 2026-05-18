package lab.dist.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.storage.StorageEngine;
import lab.dist.wal.WalEntryType;
import lab.dist.wal.WalRecord;

public final class RecoveryManager {
    private final Path walPath;

    public RecoveryManager(Path walPath) {
        this.walPath = walPath;
    }

    public BankState recover(BankState baseState, StorageEngine storageEngine) throws IOException {
        BankState recovered = baseState.copy();
        if (!Files.exists(walPath)) {
            return recovered;
        }

        List<String> lines = Files.readAllLines(walPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            Optional<WalRecord> maybeRecord = WalRecord.tryDeserialize(line);
            if (maybeRecord.isEmpty()) {
                break;
            }

            WalRecord record = maybeRecord.get();
            if (record.entryType() != WalEntryType.COMMAND) {
                continue;
            }

            AccountCommand command = record.toCommand();
            if (recovered.hasApplied(command.requestId())) {
                continue;
            }

            recovered.apply(command);
        }

        storageEngine.writeSnapshot(recovered);
        return recovered.copy();
    }
}
