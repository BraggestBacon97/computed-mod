package dev.propulsionteam.computed.lua.fs;

/** Raised for user-visible filesystem failures; surfaces to Lua as a endpoint error. */
public final class ComputerFsException extends RuntimeException {
    public ComputerFsException(String message) {
        super(message);
    }
}
