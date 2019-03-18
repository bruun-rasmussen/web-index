package dk.es.lucene;

import java.io.IOException;
import java.net.URL;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class SnapshotTest {
    private final static URL SNAPSHOT_DA_YAML = LuceneIndexTest.class.getResource("snapshot_da.yaml");
    private static LuceneIndex DA;

    @BeforeClass
    public static void loadIndex() throws IOException {        
        DA = IndexYaml.readIndex(SNAPSHOT_DA_YAML);
    }
    

    @Test
    public void testSimple() {
        assertEquals(2, DA.search("Rød begyndelse", "description").size());
        assertEquals(92, DA.search("samlerobjekt", "description").size());
        assertEquals(0, DA.search("nevermind", "description").size());
        assertEquals(3, DA.search("Knud Nielsen", "description").size());
        assertEquals(42, DA.search("lysestage", "description").size());
    }
}
