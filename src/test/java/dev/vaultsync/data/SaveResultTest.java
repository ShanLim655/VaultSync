package dev.vaultsync.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveResultTest {
    @Test
    void serializeFailureMustNotWriteToDatabase() {
        SaveResult result = SaveResult.fromSerialization(false);
        assertSame(SaveResult.SERIALIZE_FAILED, result);
        assertFalse(result.wroteToDatabase());
    }

    @Test
    void databaseFailureMustNotCountAsWritten() {
        SaveResult result = SaveResult.fromDatabaseWrite(false);
        assertSame(SaveResult.DATABASE_FAILED, result);
        assertFalse(result.wroteToDatabase());
    }

    @Test
    void successfulDatabaseWriteIsReported() {
        SaveResult result = SaveResult.fromDatabaseWrite(true);
        assertSame(SaveResult.SUCCESS, result);
        assertTrue(result.wroteToDatabase());
    }
}
