package lab.dist.wal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lab.dist.domain.AccountCommand;
import lab.dist.domain.OperationType;

public record WalRecord(
        WalEntryType entryType,
        String requestId,
        Instant timestamp,
        OperationType operationType,
        String accountId,
        String targetAccountId,
        BigDecimal amount) {

    private static final String DELIMITER = "\\|";

    public static WalRecord fromCommand(AccountCommand command) {
        return new WalRecord(
                WalEntryType.COMMAND,
                command.requestId(),
                command.timestamp(),
                command.operationType(),
                command.accountId(),
                command.targetAccountId(),
                command.amount());
    }

    public static WalRecord commit(String requestId) {
        return new WalRecord(WalEntryType.COMMIT, requestId, Instant.now(), null, "", "", BigDecimal.ZERO);
    }

    public String serialize() {
        String operation = operationType == null ? "" : operationType.name();
        return String.join("|",
                entryType.name(),
                sanitize(requestId),
                timestamp.toString(),
                operation,
                sanitize(accountId),
                sanitize(targetAccountId),
                amount.toPlainString());
    }

    public static Optional<WalRecord> tryDeserialize(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String[] parts = line.split(DELIMITER, -1);
        if (parts.length != 7) {
            return Optional.empty();
        }

        try {
            WalEntryType entryType = WalEntryType.valueOf(parts[0]);
            String requestId = parts[1];
            Instant timestamp = Instant.parse(parts[2]);
            OperationType operationType = parts[3].isBlank() ? null : OperationType.valueOf(parts[3]);
            String accountId = parts[4];
            String targetAccountId = parts[5];
            BigDecimal amount = new BigDecimal(parts[6]);
            return Optional.of(new WalRecord(entryType, requestId, timestamp, operationType, accountId, targetAccountId, amount));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public AccountCommand toCommand() {
        if (entryType != WalEntryType.COMMAND || operationType == null) {
            throw new IllegalStateException("Only COMMAND records can be converted to commands");
        }
        return new AccountCommand(requestId, timestamp, operationType, accountId, targetAccountId, amount);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("|", "_");
    }
}
