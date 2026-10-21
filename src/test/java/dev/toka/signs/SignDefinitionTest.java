package dev.toka.signs;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class SignDefinitionTest {
    @Test public void refreshDefaultsToCreationDefinition() {
        String[] expected = {"title", "code", "info", "action"};
        Main.Definition definition = (player, sign, lines) -> lines;
        assertArrayEquals(expected, definition.prepareRefresh(null, null, expected));
    }
}
