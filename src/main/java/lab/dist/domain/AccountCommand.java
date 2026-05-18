package lab.dist.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record AccountCommand(
        String requestId,
        Instant timestamp,
        OperationType operationType,
        String accountId,
        String targetAccountId,
        BigDecimal amount) {

    public AccountCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");

        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (operationType == OperationType.TRANSFER) {
            if (targetAccountId == null || targetAccountId.isBlank()) {
                throw new IllegalArgumentException("targetAccountId is required for transfers");
            }
            if (accountId.equals(targetAccountId)) {
                throw new IllegalArgumentException("source and target account must be different");
            }
        }
    }
}
