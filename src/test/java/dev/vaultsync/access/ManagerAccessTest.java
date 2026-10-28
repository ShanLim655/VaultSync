package dev.vaultsync.access;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerAccessTest {
    @Test
    void emptyAllowmanagerDeniesAccess() {
        UUID uuid = UUID.randomUUID();
        assertFalse(ManagerAccess.isAllowlisted(uuid, List.of()));
        assertFalse(ManagerAccess.isAllowlisted(uuid, null));
    }

    @Test
    void matchingUuidIsAllowlisted() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertTrue(ManagerAccess.isAllowlisted(uuid, List.of(uuid.toString())));
    }

    @Test
    void allowmanagerMatchingIsCaseInsensitive() {
        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertTrue(ManagerAccess.isAllowlisted(uuid, List.of(uuid.toString().toUpperCase())));
    }

    @Test
    void invalidEntriesAreIgnored() {
        UUID uuid = UUID.randomUUID();
        assertFalse(ManagerAccess.isAllowlisted(uuid, List.of("not-a-uuid", " ", uuid.toString() + "x")));
        assertTrue(ManagerAccess.isAllowlisted(uuid, List.of("bad", "  " + uuid + "  ", "also-bad")));
    }

    @Test
    void parseAllowmanagerSkipsInvalidValues() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> parsed = ManagerAccess.parseAllowmanager(List.of(first.toString(), "nope", second.toString(), ""));
        assertEquals(List.of(first, second), parsed);
    }

    @Test
    void guiAccessRequiresPermissionAndAllowlistedUuid() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        List<String> allowmanager = List.of(uuid.toString());

        assertTrue(ManagerAccess.isAuthorized(uuid, false, true, allowmanager));
        assertTrue(ManagerAccess.isAuthorized(uuid, true, false, allowmanager));
        assertFalse(ManagerAccess.isAuthorized(uuid, false, false, allowmanager));
        assertFalse(ManagerAccess.isAuthorized(uuid, false, true, List.of(UUID.randomUUID().toString())));
    }
}
