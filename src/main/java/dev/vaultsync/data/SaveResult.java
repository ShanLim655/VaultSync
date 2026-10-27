package dev.vaultsync.data;

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
}
