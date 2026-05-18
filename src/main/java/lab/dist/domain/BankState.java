package lab.dist.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class BankState {
    private final Map<String, BigDecimal> balances = new LinkedHashMap<>();
    private final Set<String> appliedRequestIds = new LinkedHashSet<>();

    public Map<String, BigDecimal> balancesView() {
        return Collections.unmodifiableMap(balances);
    }

    public Set<String> appliedRequestIdsView() {
        return Collections.unmodifiableSet(appliedRequestIds);
    }

    public boolean hasApplied(String requestId) {
        return appliedRequestIds.contains(requestId);
    }

    public BigDecimal balanceOf(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public void setBalance(String accountId, BigDecimal amount) {
        balances.put(accountId, amount);
    }

    public void markApplied(String requestId) {
        appliedRequestIds.add(requestId);
    }

    public void apply(AccountCommand command) {
        if (hasApplied(command.requestId())) {
            return;
        }

        switch (command.operationType()) {
            case DEPOSIT -> balances.put(
                    command.accountId(),
                    balanceOf(command.accountId()).add(command.amount()));
            case WITHDRAW -> {
                BigDecimal current = balanceOf(command.accountId());
                if (current.compareTo(command.amount()) < 0) {
                    throw new IllegalStateException("Insufficient funds for " + command.accountId());
                }
                balances.put(command.accountId(), current.subtract(command.amount()));
            }
            case TRANSFER -> {
                BigDecimal current = balanceOf(command.accountId());
                if (current.compareTo(command.amount()) < 0) {
                    throw new IllegalStateException("Insufficient funds for " + command.accountId());
                }
                balances.put(command.accountId(), current.subtract(command.amount()));
                balances.put(
                        command.targetAccountId(),
                        balanceOf(command.targetAccountId()).add(command.amount()));
            }
        }

        markApplied(command.requestId());
    }

    public BankState copy() {
        BankState copy = new BankState();
        copy.balances.putAll(this.balances);
        copy.appliedRequestIds.addAll(this.appliedRequestIds);
        return copy;
    }
}
