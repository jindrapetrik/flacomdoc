package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ComponentReaderTest {

    private static final byte[] MOVIE = {'F', 'W', 'S', 7, 0, (byte) 255, 0, 1};

    @DataProvider(name = "legacy")
    public Object[][] legacy() {
        Object[][] cases = new Object[12][2];
        int index = 0;
        for (int descriptor = 15; descriptor <= 17; descriptor++) {
            for (int movie = 0; movie <= 3; movie++) {
                cases[index++] = new Object[]{descriptor, movie};
            }
        }
        return cases;
    }

    @Test(dataProvider = "legacy")
    public void readsLegacyMoviesAndRevisionSpecificDescriptorTail(int descriptor, int movie) throws Exception {
        ByteArrayOutputStream out = descriptorPrefix(descriptor);
        string(out, "Legacy component");
        movie(out, movie);
        movie(out, 3);
        if (descriptor >= 16) {
            u32(out, 0xFEDCBA98L);
        }
        if (descriptor == 17) {
            u32(out, 123);
            string(out, "source.as");
            u32(out, 23);
        }
        u16(out, 0xBEEF);
        MfcArchive archive = archive(out);
        FlaRecord record = archive.readObject();
        assertTrue(record.isComplete());
        assertEquals(record.get("legacyComponentName"), "Legacy component");
        List<FlaRecord> movies = records(record, "legacyMovies");
        assertEquals(movies.size(), 2);
        checkMovie(movies.get(0), movie);
        checkMovie(movies.get(1), 3);
        if (descriptor >= 16) {
            assertEquals(record.get("legacyDescriptionType"), 0xFEDCBA98L);
        }
        if (descriptor == 17) {
            assertEquals(record.get("legacyComponentTooltip"), "source.as");
            assertEquals(record.get("legacyComponentIconId"), 23L);
        }
        assertEquals(archive.readUI16(), 0xBEEF);
        assertEquals(archive.getRemaining(), 0);
        assertEquals(archive.getObjects().size(), 1); // Inline movies do not allocate MFC object IDs.
    }

    @DataProvider(name = "modern")
    public Object[][] modern() {
        Object[][] cases = new Object[12][2];
        int index = 0;
        for (int component = 5; component <= 7; component++) {
            for (int movie = 0; movie <= 3; movie++) {
                cases[index++] = new Object[]{component, movie};
            }
        }
        return cases;
    }

    @Test(dataProvider = "modern")
    public void readsAllThreeModernMovieSlotsIncludingNonemptyPayloads(int component, int movie) throws Exception {
        ByteArrayOutputStream out = descriptorPrefix(18);
        out.write(component);
        u32(out, 11);
        string(out, "component");
        u32(out, 12);
        u32(out, 13);
        u32(out, 14);
        string(out, "description");
        for (int i = 0; i < 3; i++) {
            movie(out, (movie + i) % 4);
        }
        string(out, "extension");
        out.write(2);
        u32(out, 0);
        u32(out, 99);
        string(out, "extension description");
        u32(out, 1);
        string(out, "key");
        string(out, "value");
        u32(out, 0);
        if (component >= 6) {
            u32(out, 15);
        }
        if (component >= 7) {
            u32(out, 16);
            string(out, "tail");
            u32(out, 17);
            u32(out, 18);
        }
        u16(out, 0xBEEF);
        MfcArchive archive = archive(out);
        FlaRecord record = archive.readObject();
        FlaRecord metadata = (FlaRecord) record.get("component");
        assertTrue(metadata.isComplete());
        List<FlaRecord> movies = records(metadata, "movies");
        assertEquals(movies.size(), 3);
        for (int i = 0; i < 3; i++) {
            checkMovie(movies.get(i), (movie + i) % 4);
        }
        assertEquals(((FlaRecord) metadata.get("properties")).get("parametersAreNew"), 99L);
        assertEquals(records(metadata, "tasks").get(0).get("description"), "key");
        assertEquals(records(metadata, "tasks").get(0).get("script"), "value");
        assertEquals(archive.readUI16(), 0xBEEF);
        assertEquals(archive.getRemaining(), 0);
    }

    @Test
    public void movieLengthIsBoundedAndIncompleteEntriesRemainInTheModel() throws Exception {
        ByteArrayOutputStream out = descriptorPrefix(17);
        string(out, "component");
        out.write(2);
        string(out, "bad movie");
        u32(out, 0xFFFFFFFFL);
        MfcArchive archive = archive(out);
        try {
            archive.readObject();
            fail("Expected an invalid byte length");
        } catch (UnsupportedFlaRecordException ex) {
            fail("An excessive payload length is malformed, not unsupported");
        } catch (IOException expected) {
            FlaRecord record = archive.getObjects().get(0);
            FlaRecord movie = records(record, "legacyMovies").get(0);
            assertEquals(movie.get("byteCount"), 0xFFFFFFFFL);
            assertFalse(movie.isComplete());
            assertFalse(record.isComplete());
        }
    }

    @Test
    public void futureMovieRevisionsStopBeforeUnprovenFields() throws Exception {
        ByteArrayOutputStream out = descriptorPrefix(17);
        string(out, "component");
        out.write(4);
        out.write(new byte[]{1, 2, 3, 4});
        MfcArchive archive = archive(out);
        try {
            archive.readObject();
            fail("Expected an unsupported revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord movie = records(archive.getObjects().get(0), "legacyMovies").get(0);
            assertEquals(movie.get("revision"), 4);
            assertEquals(archive.getRemaining(), 4);
            assertFalse(movie.isComplete());
        }
    }

    @DataProvider(name = "layers")
    public Object[][] layers() {
        return new Object[][]{{12, 0, 0}, {12, 5, 1}, {12, 6, 2}, {13, 0, 0}, {13, 0, 1}, {13, 0, 2}};
    }

    @Test(dataProvider = "layers")
    public void readsLayerAnimationValuesOnlyForTheCorrectType(int revision, int type, int animationType) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        newClass(out, "CPicLayer");
        out.write(1);
        out.write(0);
        u16(out, 0);
        u32(out, 0);
        u32(out, 0); // CPicObj base.
        out.write(revision);
        string(out, "layer");
        out.write(new byte[]{1, 2, 3});
        for (int i = 0; i < 4; i++) {
            u32(out, i);
        }
        out.write(type);
        u16(out, 0);
        out.write(0);
        out.write(0);
        if (revision >= 13) {
            out.write(animationType);
        }
        if (animationType == 2) {
            u32(out, 2);
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
        }
        u16(out, 0xBEEF);
        MfcArchive archive = archive(out);
        FlaRecord record = archive.readObject();
        assertEquals(record.get("type"), type); // Preserve the original type, including R12's old encoding.
        assertEquals(record.get("animationType"), animationType);
        if (animationType == 2) {
            assertEquals(record.get("animationValues"), Arrays.asList(0x80000000L, 0xFFFFFFFFL));
        } else {
            assertNull(record.get("animationValues"));
        }
        assertEquals(archive.readUI16(), 0xBEEF);
        assertEquals(archive.getRemaining(), 0);
    }

    private void checkMovie(FlaRecord movie, int revision) {
        assertEquals(movie.get("revision"), revision);
        assertTrue(movie.isComplete());
        if (revision == 0) {
            assertNull(movie.get("data"));
            assertEquals(movie.getConsumedData().size(), 1);
        } else {
            assertEquals(movie.get("name"), revision == 3 ? "\u010Desky movie" : "movie");
            assertEquals(movie.get("byteCount"), (long) MOVIE.length);
            assertEquals(((BinaryData) movie.get("data")).toByteArray(), MOVIE);
        }
        if (revision >= 2) {
            assertEquals(movie.get("reserved"), 0x87654321L);
        }
    }

    ByteArrayOutputStream descriptorPrefix(int revision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        newClass(out, "CDocumentPage");
        out.write(revision);
        out.write(6);
        out.write("Page 1".getBytes(StandardCharsets.US_ASCII));
        string(out, "Scene");
        u32(out, 42);
        out.write(0);
        string(out, "legacy");
        u32(out, 0);
        u32(out, 0);
        u32(out, 0); // Library metadata R0 and parent ID.
        u32(out, 456);
        string(out, "");
        string(out, "");
        out.write(0);
        u32(out, 0);
        u32(out, 789); // Empty properties and metadata.
        out.write(0);
        u32(out, 0);
        string(out, "");
        string(out, ""); // Linkage R0.
        return out;
    }

    private void movie(ByteArrayOutputStream out, int revision) throws IOException {
        out.write(revision);
        if (revision >= 1) {
            if (revision == 3) {
                byte[] name = "\u010Desky movie".getBytes(StandardCharsets.UTF_16LE);
                out.write(255);
                u16(out, 0xFFFE);
                out.write(name.length / 2);
                out.write(name);
            } else {
                string(out, "movie");
            }
            u32(out, MOVIE.length);
            out.write(MOVIE);
        }
        if (revision >= 2) {
            u32(out, 0x87654321L);
        }
    }

    private MfcArchive archive(ByteArrayOutputStream bytes) {
        return new MfcArchive(new BinaryData(bytes.toByteArray()), "test", new FlaReadOptions(), DefaultFlaDecoders.create(), 0);
    }

    @SuppressWarnings("unchecked")
    private List<FlaRecord> records(FlaRecord record, String field) {
        return (List<FlaRecord>) record.get(field);
    }

    private void string(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.length());
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void newClass(ByteArrayOutputStream out, String name) throws IOException {
        u16(out, 0xFFFF);
        u16(out, 1);
        u16(out, name.length());
        out.write(name.getBytes(StandardCharsets.US_ASCII));
    }

    private void u16(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }

    private void u32(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 4; i++) {
            out.write((int) (value >> (8 * i)));
        }
    }
}
