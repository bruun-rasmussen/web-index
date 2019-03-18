package dk.es.lucene;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class LuceneIndexTest {

    private final static URL SAMPLE_DA_YAML = LuceneIndexTest.class.getResource("sample_da.yaml");
    private LuceneIndex da;

    @Before
    public void loadIndex() throws IOException {        
        da = IndexYaml.readIndex(SAMPLE_DA_YAML);
    }

    @Test
    public void testSimple() {
        assertEquals(1, da.search("Rød begyndelse", "description").size());
        assertEquals(2, da.search("samlerobjekt", "description").size());
        assertEquals(0, da.search("nevermind", "description").size());
        assertEquals(3, da.search("Knud Nielsen", "description").size());
    }


    //"Peter Hansen" NOT maleri


    @Test
    public void testComposite() {
        assertEquals(2, da.search("stol", "description").size());
        assertEquals(1, da.search("konferencestole stol", "description").size());
        assertEquals(2, da.search("kontorstol", "description").size());
        assertEquals(2, da.search("kontor", "description").size());
    }

    @Test
    public void testPlural() {
        // "Et par skamler"
        assertEquals(1, da.search("skamler", "description").size());
        assertEquals(1, da.search("skammel", "description").size());
    }

    @Test
    public void testSynonym() {
        // "Bogkasse og vitrine"
        assertEquals(1, da.search("skab", "description").size());
        assertEquals(1, da.search("vitrine", "description").size());
        assertEquals(1, da.search("lysestage", "description").size());
        assertEquals(2, da.search("bog", "description").size()); // bøger and Bogkasse, with prefix results
        assertEquals(2, da.search("bøger", "description").size()); // bøger->bog and Bogkasse, with prefix results
    }
}
