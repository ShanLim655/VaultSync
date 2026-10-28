package dev.vaultsync.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagerGuiPaginationTest {
    @Test
    void playerCountsProduceEnoughPages() {
        assertEquals(1, ManagerGui.pageCount(0));
        assertEquals(1, ManagerGui.pageCount(45));
        assertEquals(2, ManagerGui.pageCount(46));
        assertEquals(3, ManagerGui.pageCount(91));
    }

    @Test
    void requestedPageIsClampedToAvailablePages() {
        assertEquals(0, ManagerGui.clampPage(-1, 46));
        assertEquals(1, ManagerGui.clampPage(1, 46));
        assertEquals(1, ManagerGui.clampPage(9, 46));
        assertEquals(0, ManagerGui.clampPage(9, 0));
    }
}
