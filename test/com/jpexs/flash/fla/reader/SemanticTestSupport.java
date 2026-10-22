package com.jpexs.flash.fla.reader;

import java.nio.file.*;
import java.util.*;

final class SemanticTestSupport {

    static FlaRecord record(String name, Object... fields) {
        FlaRecord r = new FlaRecord(name, 1, 0, 0, 0, new BinaryData(new byte[0]));
        for (int i = 0; i < fields.length; i += 2) {
            r.set((String) fields[i], fields[i + 1]);
        }
        return r;
    }

    static BinaryFlaDocument nativeDocument(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), relative);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (Iterator<Path> it = files.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                Path f = it.next();
                streams.put(root.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        return new BinaryFlaReader(RecordTestSupport.ANSI).readStreams(streams);
    }

    static List<FlaRecord> records(BinaryFlaDocument doc) {
        List<FlaRecord> result = new ArrayList<>();
        Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
        for (FlaStream stream : doc.getStreams().values()) {
            visit(stream.getRoot(), seen, result);
        }
        return result;
    }

    private static void visit(Object v, Set<FlaRecord> seen, List<FlaRecord> result) {
        if (v instanceof FlaRecord) {
            FlaRecord r = (FlaRecord) v;
            if (!seen.add(r)) {
                return;
            }
            result.add(r);
            for (Object child : r.getFields().values()) {
                visit(child, seen, result);
            }
        } else if (v instanceof List) {
            for (Object child : (List<?>) v) {
                visit(child, seen, result);
            }
        }
    }

    static FlaRecord value(String name, int type, Object value) {
        return record("persistentValue", "name", name, "type", type, value instanceof List ? "values" : "value", value);
    }

    static FlaPersistentData persistent(FlaRecord... entries) {
        return record("persistentData", "entries", Arrays.asList(entries)).getPersistentData();
    }
}
