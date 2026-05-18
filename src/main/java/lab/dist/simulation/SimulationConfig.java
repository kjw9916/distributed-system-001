package lab.dist.simulation;

import java.nio.file.Path;
import lab.dist.runtime.FailurePoint;

public record SimulationConfig(
        Path workspace,
        int users,
        int requests,
        int crashAt,
        FailurePoint failurePoint,
        boolean recoverAfterCrash,
        long seed) {

    public SimulationConfig {
        if (users <= 0) {
            throw new IllegalArgumentException("users must be positive");
        }
        if (requests <= 0) {
            throw new IllegalArgumentException("requests must be positive");
        }
    }
}
