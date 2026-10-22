package com.jpexs.flash.fla.reader;

import java.io.IOException;
import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class FlaIkSemanticsTest {

    private static List<Double> parameters() {
        return Arrays.asList(1.25, -3.0, 4.0, 0.6, 2.0, -1.0, 5.0, 0.7, 3.0, 2.0, 6.0, 0.8);
    }

    @Test
    public void skinOffsetsReferToAnchorAndControlPointsRatherThanPoses() throws Exception {
        FlaSkinBindings s = FlaSkinBindings.fromArrays(Arrays.asList(1, 0, 7, 0, 255, 4, 8, -1), parameters());
        assertEquals(s.getDeclaredPointCount(), 1);
        FlaSkinBindings.Connection c = s.getPoints().get(0).getConnections().get(0);
        assertEquals(c.getBoneId(), 7);
        assertEquals(c.getCubicFlag(), 255);
        assertTrue(c.hasControlPoints());
        assertEquals(c.getAnchor().getLongitudinalFraction(), 1.25);
        assertEquals(c.getAnchor().getSignedPerpendicularDistance(), -3.0);
        assertEquals(c.getAnchor().getDistanceToSegment(), 4.0);
        assertEquals(c.getAnchor().getWeight(), 0.6);
        assertEquals(c.getControl1().getOffset(), 4);
        assertEquals(c.getControl1().getWeight(), 0.7);
        assertEquals(c.getControl2().getOffset(), 8);
    }

    @Test
    public void straightConnectionsAndMissingControlReferencesAreIndependent() throws Exception {
        FlaSkinBindings s = FlaSkinBindings.fromArrays(Arrays.asList(2, 0, 3, 0, 0, -1, 1, 5, 4, 1, -1, 8, -2), parameters());
        assertFalse(s.getPoints().get(0).getConnections().get(0).hasControlPoints());
        FlaSkinBindings.Connection c = s.getPoints().get(1).getConnections().get(0);
        assertNull(c.getControl1());
        assertNotNull(c.getControl2());
        assertEquals(s.getPoints().get(1).getTerminator(), -2);
    }

    @DataProvider(name = "badIndices")
    public Object[][] badIndices() {
        List<Object[]> result = new ArrayList<>();
        List<Integer> valid = Arrays.asList(1, 0, 7, 0, 1, 4, 8, -1);
        result.add(new Object[]{Collections.emptyList()});
        for (int i = 2; i < valid.size(); i++) {
            result.add(new Object[]{valid.subList(0, i)});
        }
        result.add(new Object[]{Arrays.asList(-1)});
        result.add(new Object[]{Arrays.asList(1, 1, -1)});
        result.add(new Object[]{Arrays.asList(1, 0, 7, 9, 0, -1)});
        result.add(new Object[]{Arrays.asList(1, 0, 7, -1, 0, -1)});
        result.add(new Object[]{Arrays.asList(1, 0, 7, 0, 1, 10, 8, -1)});
        result.add(new Object[]{Arrays.asList(1, 0, 7, 0, 1, 4, 12, -1)});
        result.add(new Object[]{Arrays.asList(1.0, 0.0)});
        return result.toArray(new Object[0][]);
    }

    @Test(dataProvider = "badIndices", expectedExceptions = IOException.class)
    public void malformedSkinArraysDoNotReadUnrelatedData(List<?> indices) throws Exception {
        FlaSkinBindings.fromArrays(indices, parameters());
    }

    @Test
    public void malformedIkInterpretationHasDiagnosticsAndRetainsTheRawValue() {
        List<Integer> bad = Arrays.asList(1, 0, 7, 999, 0, -1);
        FlaPersistentData data = persistent(value("lsd_connection_indices", 1, bad), value("lsd_connections", 4, parameters()), value("_ikTreeXML", 5, "<nodes>"));
        FlaInverseKinematics ik = data.getInverseKinematics();
        assertNotNull(ik);
        assertNull(ik.getSkinBindings());
        assertEquals(ik.getDiagnostics().size(), 2);
        assertEquals(data.get("lsd_connection_indices"), bad);
        assertEquals(data.getString("_ikTreeXML"), "<nodes>");
    }

    @Test
    public void nativeIkTopologyPoseTimingAndAllIndexedWeightsAreResolved() throws Exception {
        FlaInverseKinematics ik = null;
        int views = 0;
        for (FlaRecord r : records(nativeDocument("fla/cs4/0023_inverse_kinematics-cs4-todo"))) {
            if (!"persistentData".equals(r.getClassName())) {
                continue;
            }
            FlaInverseKinematics candidate = r.getPersistentData().getInverseKinematics();
            if (candidate != null) {
                assertTrue(candidate.getDiagnostics().isEmpty(), candidate.getDiagnostics().toString());
                views++;
                if (candidate.getSkinBindings() != null) {
                    ik = candidate;
                }
            }
        }
        assertTrue(views >= 2);
        assertNotNull(ik);
        assertEquals(ik.getTreeName(), "ikTreeName5");
        assertEquals(ik.getPoseStartFrames(), Arrays.asList(0, 9, 19));
        assertEquals(ik.getPoseStates().size(), 3);
        assertEquals(ik.getPoseStates().get(0).getNumber("duration"), Double.valueOf(9));
        assertEquals(ik.getPoseStates().get(1).getNumber("duration"), Double.valueOf(10));
        assertEquals(ik.getRoots().size(), 1);
        assertEquals(ik.getRoots().get(0).getChildren().size(), 4);
        assertFalse(ik.getRoots().get(0).isLocked());
        assertEquals(ik.getRoots().get(0).getChildren().get(3).getChildren().size(), 2);
        assertEquals(ik.getNodeNames().size(), 7);
        assertEquals(ik.getBoneNames().size(), 7);
        assertEquals(ik.getWorldMatrices().size(), 7);
        assertEquals(ik.getWorldMatrices().get(0).getNumber("tx"), Double.valueOf(225));
        assertEquals(ik.getTopology().size(), 7);
        assertEquals(ik.getTopology().get(0), Arrays.asList(1, 2, 3, 4));
        assertEquals(ik.getTopology().get(4), Arrays.asList(5, 6));
        FlaSkinBindings skin = ik.getSkinBindings();
        assertEquals(skin.getDeclaredPointCount(), 51);
        assertEquals(skin.getPoints().size(), 51);
        int links = 0, tuples = 0;
        Set<Integer> offsets = new HashSet<>();
        for (FlaSkinBindings.Point point : skin.getPoints()) {
            double sum = 0;
            for (FlaSkinBindings.Connection c : point.getConnections()) {
                links++;
                assertTrue(c.hasControlPoints());
                assertNotNull(c.getControl1());
                assertNotNull(c.getControl2());
                for (FlaSkinBindings.Parameters p : Arrays.asList(c.getAnchor(), c.getControl1(), c.getControl2())) {
                    tuples++;
                    assertTrue(offsets.add(p.getOffset()));
                }
                sum += c.getAnchor().getWeight();
            }
            assertEquals(sum, 1.0, 0.000002);
        }
        assertEquals(links, 53);
        assertEquals(tuples, 159);
        assertEquals(offsets.size() * 4, ((List<?>) ik.getPersistentData().get("lsd_connections")).size());
        assertEquals(skin.getPoints().get(43).getConnections().size(), 3);
        assertNotNull(ik.getContours());
        assertFalse(ik.getMotionConstraints().isEmpty());
        assertEquals(ik.getMotionConstraints().get(0).get(0).getName(), "motionType");
        assertEquals(ik.getMotionConstraints().get(0).get(0).getValue(), "rotation");
    }

    @Test
    public void persistentValuesUseLastLookupButRetainOrderedDuplicates() {
        FlaPersistentData d = persistent(value("name", 5, "first"), value("name", 5, "last"));
        assertEquals(d.getEntries().size(), 2);
        assertEquals(d.getString("name"), "last");
        assertEquals(d.getEntries().get(0).getValue(), "first");
        assertNull(d.getInverseKinematics());
    }
}
