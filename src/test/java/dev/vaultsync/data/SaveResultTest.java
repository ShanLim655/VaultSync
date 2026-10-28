package dev.vaultsync.data;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveResultTest {
    @Test
    void serializationFailureDoesNotCallWriterOrReplaceExistingSnapshot() {
        byte[] existingSnapshot = {1, 2, 3};
        AtomicReference<byte[]> databaseSnapshot = new AtomicReference<>(existingSnapshot);
        AtomicBoolean writerCalled = new AtomicBoolean(false);

        SaveResult result = SaveResult.writeIfSerialized(null, () -> {
            writerCalled.set(true);
            databaseSnapshot.set(new byte[]{9});
            return true;
        });

        assertSame(SaveResult.SERIALIZE_FAILED, result);
        assertFalse(writerCalled.get());
        assertArrayEquals(existingSnapshot, databaseSnapshot.get());
    }

    @Test
    void databaseFailureDoesNotReplaceExistingSnapshot() {
        byte[] existingSnapshot = {1, 2, 3};
        byte[] newSnapshot = {4, 5, 6};
        AtomicReference<byte[]> databaseSnapshot = new AtomicReference<>(existingSnapshot);

        SaveResult result = SaveResult.writeIfSerialized(newSnapshot, () -> {
            boolean databaseWriteSucceeded = false;
            if (databaseWriteSucceeded) databaseSnapshot.set(newSnapshot);
            return databaseWriteSucceeded;
        });

        assertSame(SaveResult.DATABASE_FAILED, result);
        assertArrayEquals(existingSnapshot, databaseSnapshot.get());
    }

    @Test
    void successfulDatabaseWriteIsReported() {
        byte[] existingSnapshot = {1, 2, 3};
        byte[] newSnapshot = {4, 5, 6};
        AtomicReference<byte[]> databaseSnapshot = new AtomicReference<>(existingSnapshot);

        SaveResult result = SaveResult.writeIfSerialized(newSnapshot, () -> {
            databaseSnapshot.set(newSnapshot);
            return true;
        });

        assertSame(SaveResult.SUCCESS, result);
        assertTrue(result.wroteToDatabase());
        assertArrayEquals(newSnapshot, databaseSnapshot.get());
    }
}
