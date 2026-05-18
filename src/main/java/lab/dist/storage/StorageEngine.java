package lab.dist.storage;

import java.io.IOException;
import java.nio.file.Path;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.BankState;
import lab.dist.recovery.RecoveryManager;

public interface StorageEngine {
    Path snapshotPath();

    BankState loadCurrentState() throws IOException;

    void persistCommand(AccountCommand command) throws IOException;

    void writeSnapshot(BankState state) throws IOException;

    BankState recover(RecoveryManager recoveryManager) throws IOException;
}
