package dev.vaultsync.data;

import java.util.function.BooleanSupplier;

public enum SaveResult {
    SERIALIZE_FAILED,
    DATABASE_FAILED,
    SUCCESS;

    public boolean wroteToDatabase() {
        return this == SUCCESS;
    }

    public static SaveResult fromSerialization(boolean succeeded) {
        return succeeded ? SUCCESS : SERIALIZE_FAILED;
    }

    public static SaveResult fromDatabaseWrite(boolean succeeded) {
        return succeeded ? SUCCESS : DATABASE_FAILED;
    }

    public static SaveResult writeIfSerialized(byte[] payload, BooleanSupplier databaseWrite) {
        if (payload == null) return SERIALIZE_FAILED;
        return fromDatabaseWrite(databaseWrite.getAsBoolean());
    }
}
