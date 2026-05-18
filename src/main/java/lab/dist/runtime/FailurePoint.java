package lab.dist.runtime;

public enum FailurePoint {
    NONE,
    AFTER_WAL_APPEND,
    BEFORE_DATA_APPLY,
    AFTER_DATA_APPLY;
}
