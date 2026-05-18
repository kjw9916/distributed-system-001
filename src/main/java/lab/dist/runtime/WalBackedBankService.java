package lab.dist.runtime;

import java.io.IOException;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.storage.StorageEngine;
import lab.dist.wal.WalWriter;

public final class WalBackedBankService {
    private final StorageEngine storageEngine;
    private final WalWriter walWriter;

    public WalBackedBankService(StorageEngine storageEngine, WalWriter walWriter) {
        this.storageEngine = storageEngine;
        this.walWriter = walWriter;
    }

    public synchronized void handleCommand(AccountCommand command, FailurePoint failurePoint) throws IOException {
        validateAgainstCurrentState(command);
        walWriter.appendCommand(command);

        if (failurePoint == FailurePoint.AFTER_WAL_APPEND || failurePoint == FailurePoint.BEFORE_DATA_APPLY) {
            throw new SimulatedCrashException("Simulated crash at " + failurePoint);
        }

        storageEngine.persistCommand(command);

        if (failurePoint == FailurePoint.AFTER_DATA_APPLY) {
            throw new SimulatedCrashException("Simulated crash at " + failurePoint);
        }

        walWriter.appendCommit(command.requestId());
    }

    private void validateAgainstCurrentState(AccountCommand command) throws IOException {
        BankState validationState = storageEngine.loadCurrentState();
        validationState.apply(command);
    }
}
