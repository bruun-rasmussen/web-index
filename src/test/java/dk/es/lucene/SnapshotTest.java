package dk.es.lucene;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import au.com.bytecode.opencsv.CSVReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class SnapshotTest {
    private final static URL HITS_CSV = LuceneIndexTest.class.getResource("snapshot-hits.csv");
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
    
    @Test
    public void testHits_da() throws IOException {
        CSVReader reader = new CSVReader(new InputStreamReader(HITS_CSV.openStream()));
        String rec[];
        while ((rec = reader.readNext()) != null) {
            if (!"da".equals(rec[0]))
                continue;
            
            String term = rec[1];
            Set<Long> actualHits = DA.search(term, "description");
            int expectedHits = Integer.parseInt(rec[2]);
                        
            assertThat("\"" + term + "\"", actualHits.size(), is(expectedHits));
        }
    }
}
