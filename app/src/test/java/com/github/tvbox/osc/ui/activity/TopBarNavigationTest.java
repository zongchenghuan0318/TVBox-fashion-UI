package com.github.tvbox.osc.ui.activity;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TopBarNavigationTest {
    @Test public void searchConnectsToHomeAndLastCategoryWrapsToSettings() {
        assertEquals(4, HomeActivity.nextTopBarPosition(3, 6, true));
        assertEquals(3, HomeActivity.nextTopBarPosition(4, 6, false));
        assertEquals(0, HomeActivity.nextTopBarPosition(9, 6, true));
        assertEquals(9, HomeActivity.nextTopBarPosition(0, 6, false));
    }

    @Test public void focusCyclesBeforeCategoryDataHasLoaded() {
        assertEquals(0, HomeActivity.nextTopBarPosition(3, 0, true));
        assertEquals(3, HomeActivity.nextTopBarPosition(0, 0, false));
    }

    @Test public void bothDirectionsVisitEveryEntryAndReturnToTheStart() {
        for (int categories : new int[]{0, 1, 6, 20}) {
            int count = categories + 4;
            for (boolean right : new boolean[]{true, false}) {
                Set<Integer> visited = new HashSet<>();
                int position = 0;
                for (int i = 0; i < count; i++) {
                    assertTrue(visited.add(position));
                    position = HomeActivity.nextTopBarPosition(position, categories, right);
                }
                assertEquals(count, visited.size());
                assertEquals(0, position);
            }
        }
    }
}
