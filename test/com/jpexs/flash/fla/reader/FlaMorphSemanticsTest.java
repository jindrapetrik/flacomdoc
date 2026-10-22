package com.jpexs.flash.fla.reader;

import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class FlaMorphSemanticsTest {

    @Test
    public void edgeAndCubicCoordinatesUseDifferentBinaryScales() {
        FlaRecord e = record("edge", "startX", 1530880, "startY", -256);
        assertEquals(e.getEdgePoint("start").getXPixels(), 299.0);
        assertEquals(e.getEdgePoint("start").getYPixels(), -0.05);
        assertEquals(e.getEdgePoint("start").getFractionalBits(), 8);
        assertNull(e.getEdgePoint("control"));
        assertNull(e.getEdgePoint("anything"));
        FlaCubic c = record("auxiliary", "points", Arrays.asList(0, 0, 0, 200, 200, 200, 200, 0), "flags", 0).getCubic();
        assertEquals(c.getPoints().get(3).getXPixels(), 10.0);
        assertEquals(c.getPoints().get(3).getFractionalBits(), 0);
        assertEquals(c.evaluatePixels(0), new double[]{0, 0});
        assertEquals(c.evaluatePixels(0.5), new double[]{5, 7.5});
        assertEquals(c.evaluatePixels(1), new double[]{10, 0});
        assertEquals(c.getBoundaryEnd(), c.getPoints().get(3));
        assertNull(c.getApproximation());
    }

    @DataProvider(name = "badFractions")
    public Object[][] badFractions() {
        return new Object[][]{{-0.01}, {1.01}, {Double.NaN}, {Double.POSITIVE_INFINITY}};
    }

    @Test(dataProvider = "badFractions", expectedExceptions = IllegalArgumentException.class)
    public void cubicRejectsInvalidParameters(double t) {
        record("auxiliary", "points", Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0)).getCubic().evaluatePixels(t);
    }

    @Test
    public void cubicBoundaryMetadataHasExplicitFlagsAndDefaults() {
        FlaRecord r = record("auxiliary", "points", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), "flags", 3, "boundaryStartPointTwips", Arrays.asList(90, -40));
        FlaCubic c = r.getCubic();
        assertEquals(c.getBoundaryStart().getXPixels(), 4.5);
        assertNull(c.getBoundaryEnd());
        r.set("points", Arrays.asList(0, 0));
        assertNull(r.getCubic());
        assertEquals(c.getPoints().get(0).getRawX(), 1);
    }

    @Test
    public void morphStylePairsAreZeroBasedAndDoNotUseEdgeStyleNumbering() {
        FlaRecord curve = record("CMorphCurve", "controlPointA", Arrays.asList(200, 100), "controlPointB", Arrays.asList(400, 300), "anchorPointA", Arrays.asList(600, 500), "anchorPointB", Arrays.asList(1000, 700), "lineFlags", 0x80000000L);
        FlaRecord segment = record("CMorphSegment", "strokeIndex1", -1, "strokeIndex2", 0, "fillIndex1", 1, "fillIndex2", 2, "startPointA", Arrays.asList(0, 100), "startPointB", Arrays.asList(200, 300), "curves", Arrays.asList(curve, null, curve));
        FlaRecord r = record("CPicMorphShape", "segments", Arrays.asList(segment, null, segment), "hints", Collections.emptyList(), "fills", Collections.emptyList(), "strokes", Collections.emptyList());
        FlaMorphShape m = r.getMorphShape();
        assertEquals(m.getSegments().size(), 3);
        assertNull(m.getSegments().get(1));
        FlaMorphShape.Segment s = m.getSegments().get(0);
        assertEquals(s.getStrokeIndexA(), Integer.valueOf(-1));
        assertEquals(s.getStrokeIndexB(), Integer.valueOf(0));
        assertEquals(s.interpolateStartPixels(0.5), new double[]{5, 10});
        assertNull(s.getCurves().get(1));
        FlaMorphShape.Curve c = s.getCurves().get(0);
        assertTrue(c.isLine());
        assertEquals(c.getLineFlags(), Long.valueOf(0x80000000L));
        assertEquals(c.interpolateControlPixels(0.5), new double[]{15, 10});
        assertEquals(c.interpolateAnchorPixels(1), new double[]{50, 35});
        curve.set("lineFlags", 0L);
        assertTrue(c.isLine());
        assertFalse(r.getMorphShape().getSegments().get(0).getCurves().get(0).isLine());
        assertNull(record("CPicShape").getMorphShape());
    }

    @Test
    public void nativeCubicControlNetAndApproximationMatchIndependentXflCoordinates() throws Exception {
        int count = 0;
        for (FlaRecord r : records(nativeDocument("fla/cs4/0004_cubics"))) {
            FlaCubic c = r.getCubic();
            if (c == null) {
                continue;
            }
            if (count++ == 0) {
                assertEquals(c.getPoints().get(0).getXPixels(), 149.5);
                assertEquals(c.getPoints().get(3).getXPixels(), 229.5);
                assertEquals(c.getPoints().get(1).getYPixels(), 199.5);
                assertEquals(c.getPoints().get(2).getYPixels(), 109.5);
                assertEquals(c.getApproximation().get(1).getPoint().getRawY(), 3878);
                assertTrue(c.getApproximation().get(1).isControl());
                assertFalse(c.getApproximation().get(0).isControl());
                assertEquals(c.getApproximation().get(6).getControlFlag(), 1);
            }
        }
        assertEquals(count, 5);
    }

    @Test
    public void nativeMorphUsesWholeTwipsAndCorrespondingEndpointStyles() throws Exception {
        int count = 0;
        for (FlaRecord r : records(nativeDocument("fla/cs4/0016_shapetween"))) {
            FlaMorphShape m = r.getMorphShape();
            if (m == null) {
                continue;
            }
            if (count++ == 0) {
                FlaMorphShape.Segment segment = m.getSegments().get(0);
                assertEquals(segment.getStartA().getRawX(), 1000);
                assertEquals(segment.getStartA().getXPixels(), 50.0);
                assertEquals(segment.getStartB().getRawY(), 600);
                assertEquals(segment.getStartB().getYPixels(), 30.0);
                assertEquals(segment.getStrokeIndexA(), Integer.valueOf(0));
                assertEquals(segment.getFillIndexA(), Integer.valueOf(-1));
            }
        }
        assertTrue(count > 0);
    }
}
