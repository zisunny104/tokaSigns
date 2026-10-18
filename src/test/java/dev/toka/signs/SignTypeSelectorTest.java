package dev.toka.signs;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignTypeSelectorTest {
    @Test public void parsesAllAndFunctionalAliases(){
        assertNull(SignTypeSelector.parse("all"));
        assertNull(SignTypeSelector.parse("ALL"));
        assertEquals(Integer.valueOf(10),SignTypeSelector.parse("tpr"));
        assertEquals(Integer.valueOf(31),SignTypeSelector.parse("BUY"));
        assertEquals(Integer.valueOf(32),SignTypeSelector.parse("s"));
        assertEquals(Integer.valueOf(41),SignTypeSelector.parse("41"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsUnknownTypes(){SignTypeSelector.parse("not-a-type");}
    @Test(expected=IllegalArgumentException.class)
    public void rejectsNonFunctionalIds(){SignTypeSelector.parse("1");}
}