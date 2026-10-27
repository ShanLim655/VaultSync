package dev.vaultsync.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class VaultSyncGuiHolderTest {
    @Test
    void playersViewHasNoTarget() {
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.PLAYERS, null, null);
        assertSame(GuiView.PLAYERS, holder.view());
        assertNull(holder.targetUuid());
        assertNull(holder.targetName());
    }

    @Test
    void confirmRestoreRetainsTargetForConfirmationGate() {
        UUID target = UUID.randomUUID();
        VaultSyncGuiHolder holder = new VaultSyncGuiHolder(GuiView.CONFIRM_RESTORE, target, "Steve");
        assertSame(GuiView.CONFIRM_RESTORE, holder.view());
        assertEquals(target, holder.targetUuid());
        assertEquals("Steve", holder.targetName());
    }

    @Test
    void actionsViewIsDistinctFromConfirmRestore() {
        UUID target = UUID.randomUUID();
        VaultSyncGuiHolder actions = new VaultSyncGuiHolder(GuiView.ACTIONS, target, "Alex");
        VaultSyncGuiHolder confirm = new VaultSyncGuiHolder(GuiView.CONFIRM_RESTORE, target, "Alex");
        assertSame(GuiView.ACTIONS, actions.view());
        assertSame(GuiView.CONFIRM_RESTORE, confirm.view());
    }
}
