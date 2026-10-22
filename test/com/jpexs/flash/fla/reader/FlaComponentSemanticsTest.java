package com.jpexs.flash.fla.reader;

import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class FlaComponentSemanticsTest {

    @DataProvider(name = "types")
    public Object[][] types() {
        return new Object[][]{{0L, FlaComponentParameter.Type.NUMBER}, {1L, FlaComponentParameter.Type.DOUBLE}, {2L, FlaComponentParameter.Type.STRING}, {3L, FlaComponentParameter.Type.COLOR}, {4L, FlaComponentParameter.Type.BOOLEAN}, {5L, FlaComponentParameter.Type.STRINGS}, {6L, FlaComponentParameter.Type.PERCENT}, {0xffffffffL, FlaComponentParameter.Type.UNKNOWN}};
    }

    @Test(dataProvider = "types")
    public void componentTypeTableIsIndependentOfPersistentAndPropertyTypes(long id, FlaComponentParameter.Type type) {
        FlaRecord r = record("componentParameter", "type", id, "name", "label", "minimum", 0xffffffffL, "maximum", 100L, "maximumLength", 0x80000000L, "variableName", "variable", "displayOrder", 7L);
        FlaComponentParameter p = r.getComponentParameter();
        assertEquals(p.getType(), type);
        assertEquals(p.getTypeId(), id);
        assertEquals(p.getMinimum(), Integer.valueOf(-1));
        assertEquals(p.getMaximum(), Integer.valueOf(100));
        assertEquals(p.getMaximumLength(), Integer.valueOf(Integer.MIN_VALUE));
        assertEquals(p.getVariable(), "variable");
        assertEquals(p.getDisplayOrder(), Long.valueOf(7));
        r.set("maximum", 200L);
        assertEquals(p.getMaximum(), Integer.valueOf(100));
    }

    @DataProvider(name = "selection")
    public Object[][] selection() {
        return new Object[][]{{-1, null}, {0, "first"}, {1, "second"}, {2, null}, {Integer.MAX_VALUE, null}};
    }

    @Test(dataProvider = "selection")
    public void selectedChoicesAreNotClampedOrGuessed(int index, String selected) {
        List<String> choices = new ArrayList<>(Arrays.asList("first", "second"));
        FlaComponentParameter p = record("componentParameter", "type", 5L, "values", choices, "selectedIndex", index).getComponentParameter();
        assertEquals(p.getSelectedChoice(), selected);
        assertEquals(p.getSelectedIndex(), Integer.valueOf(index));
        choices.clear();
        assertEquals(p.getChoices().size(), 2);
    }

    @Test
    public void percentagesHaveAStoredFractionAndIntegersAreNotBooleans() {
        FlaComponentParameter p = record("componentParameter", "type", 6L, "value", -0.125).getComponentParameter();
        assertEquals(p.getDisplayPercent(), Double.valueOf(-12.5));
        assertEquals(p.getValue(), -0.125);
        p = record("componentParameter", "type", 4L, "value", -8).getComponentParameter();
        assertEquals(p.getValue(), -8);
        assertNull(p.getDisplayPercent());
        assertNull(p.getMinimum());
        assertNull(record("persistentValue", "type", 5L).getComponentParameter());
    }
}
