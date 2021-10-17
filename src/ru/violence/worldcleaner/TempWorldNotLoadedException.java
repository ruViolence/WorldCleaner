package ru.violence.worldcleaner;

public class TempWorldNotLoadedException extends Exception {
    public static final TempWorldNotLoadedException INSTANCE = new TempWorldNotLoadedException();

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
