package com.jpexs.flash.fla.reader;

import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class FlaStyleSemanticsTest {

    @DataProvider(name = "procedural")
    public Object[][] procedural() {
        return new Object[][]{
            {0, 1, FlaStrokeStyle.Kind.DASHED, new String[]{}, new int[]{}},
            {0, 0, FlaStrokeStyle.Kind.SOLID, new String[]{}, new int[]{}},
            {5, 0x800a, FlaStrokeStyle.Kind.DASHED, new String[]{"dashLengthTwips", "gapLengthTwips"}, new int[]{5, 10}},
            {0, 0x0092, FlaStrokeStyle.Kind.DOTTED, new String[]{"dotSpacingTwips"}, new int[]{18}},
            {0, 0x8092, FlaStrokeStyle.Kind.DOTTED, new String[]{"dotSpacingTwips"}, new int[]{4114}},
            {0, 3 | 6 << 3 | 2 << 6 | 3 << 8, FlaStrokeStyle.Kind.RAGGED, new String[]{"pattern", "waveHeight", "waveLength"}, new int[]{6, 2, 3}},
            {0, 4 | 3 << 3 | 2 << 5 | 1 << 7, FlaStrokeStyle.Kind.STIPPLE, new String[]{"dotSize", "variation", "density"}, new int[]{3, 2, 1}},
            {0, 5 | 3 << 3 | 2 << 5 | 1 << 7 | 2 << 9 | 3 << 11 | 1 << 13, FlaStrokeStyle.Kind.HATCHED, new String[]{"hatchThickness", "space", "rotate", "jiggle", "curve", "length"}, new int[]{3, 2, 1, 2, 3, 1}},
            {0, 7, FlaStrokeStyle.Kind.UNKNOWN, new String[]{}, new int[]{}},
            {65535, 7, FlaStrokeStyle.Kind.DASHED, new String[]{"dashLengthTwips", "gapLengthTwips"}, new int[]{65535, 7}}
        };
    }

    @Test(dataProvider = "procedural")
    public void packedStrokeParametersHaveTypeSpecificUnits(int first, int second, FlaStrokeStyle.Kind kind, String[] names, int[] values) {
        FlaRecord r = record("strokes", "width", 25, "styleWord1", first, "styleWord2", second, "miterLimitWord", 640, "capStyle", 2);
        FlaStrokeStyle s = r.getStrokeStyle();
        assertNotNull(s);
        assertEquals(s.getKind(), kind);
        assertEquals(s.getWidthPixels(), 1.25);
        assertEquals(s.getMiterLimit(), Double.valueOf(2.5));
        for (int i = 0; i < names.length; i++) {
            assertEquals(s.getParameters().get(names[i]), Integer.valueOf(values[i]));
        }
        assertEquals(s.getFirstWord(), first);
        assertEquals(s.getSecondWord(), second);
        assertEquals(s.hasSharpCorners(), (second & 0x8000) != 0);
        r.set("width", 100);
        assertEquals(s.getWidthTwips(), 25);
        assertEquals(r.getStrokeStyle().getWidthTwips(), 100);
        assertEquals(s.getCapStyleId(), Integer.valueOf(2));
        assertNull(s.getScaleModeId());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void strokeSnapshotCannotBeEdited() {
        record("strokes", "width", 20, "styleWord1", 10, "styleWord2", 5).getStrokeStyle().getParameters().put("dashLengthTwips", 7);
    }

    @Test
    public void incompleteAndInvalidStrokeWordsHaveNoInventedDefaults() {
        assertNull(record("strokes", "styleWord1", 0, "styleWord2", 0).getStrokeStyle());
        assertNull(record("strokes", "width", 1, "styleWord1", -1, "styleWord2", 0).getStrokeStyle());
        assertNull(record("strokes", "width", 1, "styleWord1", 65536, "styleWord2", 0).getStrokeStyle());
    }

    @DataProvider(name = "paints")
    public Object[][] paints() {
        return new Object[][]{{0, FlaPaintStyle.Kind.SOLID}, {16, FlaPaintStyle.Kind.LINEAR_GRADIENT}, {18, FlaPaintStyle.Kind.RADIAL_GRADIENT}, {19, FlaPaintStyle.Kind.RADIAL_GRADIENT}, {17, FlaPaintStyle.Kind.GRADIENT_UNKNOWN}, {32, FlaPaintStyle.Kind.EXTENDED}, {64, FlaPaintStyle.Kind.BITMAP}, {65, FlaPaintStyle.Kind.BITMAP}, {66, FlaPaintStyle.Kind.BITMAP}, {67, FlaPaintStyle.Kind.BITMAP}, {0x70, FlaPaintStyle.Kind.GRADIENT_UNKNOWN}, {0x60, FlaPaintStyle.Kind.EXTENDED}};
    }

    @Test(dataProvider = "paints")
    public void fillBranchPrecedenceAndRawFlagsArePreserved(int flags, FlaPaintStyle.Kind kind) {
        FlaPaintStyle p = record("fills", "packedValue", 0x80402010L, "flags", flags, "secondaryFlags", 201).getPaintStyle();
        assertEquals(p.getKind(), kind);
        assertEquals(p.getFlags(), flags);
        assertEquals(p.getSecondaryFlags(), 201);
        assertEquals(p.getRgbaWord(), 0x80402010L);
        if (kind == FlaPaintStyle.Kind.BITMAP) {
            assertEquals(p.isBitmapClipped(), Boolean.valueOf((flags & 1) != 0));
            assertEquals(p.isBitmapSmoothed(), Boolean.valueOf((flags & 2) == 0));
        } else {
            assertNull(p.isBitmapClipped());
            assertNull(p.isBitmapSmoothed());
        }
        assertNull(p.getFocalRatio());
        assertNull(p.getStops());
    }

    @DataProvider(name = "gradientFlags")
    public Object[][] gradientFlags() {
        return new Object[][]{{0L, FlaPaintStyle.Spread.EXTEND, false}, {1L, FlaPaintStyle.Spread.EXTEND, true}, {4L, FlaPaintStyle.Spread.REFLECT, false}, {9L, FlaPaintStyle.Spread.REPEAT, true}, {13L, FlaPaintStyle.Spread.REFLECT, true}, {0x80000009L, FlaPaintStyle.Spread.REPEAT, true}};
    }

    @Test(dataProvider = "gradientFlags")
    public void gradientFlagsRatiosAndRgbaHaveNativeMeaning(long flags, FlaPaintStyle.Spread spread, boolean linear) {
        List<FlaRecord> stops = new ArrayList<>();
        stops.add(record("stops", "ratio", 128, "colorRgba", 0x80402010L));
        FlaRecord r = record("fills", "packedValue", 0L, "flags", 18, "secondaryFlags", 0, "gradientFlags", flags, "focalPoint", 0x1234ff01, "stops", stops);
        FlaPaintStyle p = r.getPaintStyle();
        assertEquals(p.getSpread(), spread);
        assertEquals(p.isLinearRgb(), Boolean.valueOf(linear));
        assertEquals(p.getGradientFlags(), Long.valueOf(flags));
        assertEquals(p.getStoredFocalPoint(), Integer.valueOf(0x1234ff01));
        assertEquals(p.getFocalRatio(), Double.valueOf(-1));
        FlaPaintStyle.Stop stop = p.getStops().get(0);
        assertEquals(stop.getPosition(), 128 / 255.0);
        assertEquals(stop.getRed(), 16);
        assertEquals(stop.getGreen(), 32);
        assertEquals(stop.getBlue(), 64);
        assertEquals(stop.getAlpha(), 128 / 255.0);
        stops.clear();
        assertEquals(p.getStops().size(), 1);
    }
}
