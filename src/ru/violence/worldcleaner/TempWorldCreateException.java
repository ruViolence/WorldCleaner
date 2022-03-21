package ru.violence.worldcleaner;

public class TempWorldCreateException extends Exception {
    private final Reason reason;

    public TempWorldCreateException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return this.reason;
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public enum Reason {
        REAL_WORLD_NOT_LOADED
    }
}
