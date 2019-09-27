package dk.es.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * @author osa
 */
public class LuceneIndexTest
{
  public LuceneIndexTest()
  {
  }

  private static void readYaml(InputStream is, LuceneIndex index) throws IOException {
    Yaml yaml = new Yaml();

    Map<Integer, List<Map<String,String>>> obj = (Map<Integer, List<Map<String,String>>>)yaml.load(is);

    LuceneIndex.Writer w = index.open(true);
    for (Map.Entry<Integer, List<Map<String,String>>> k : obj.entrySet())
    {
      Long itemId = new Long(k.getKey());
      LuceneIndex.Builder b = w.newDocument(itemId);
      for (Map<String,String> o : k.getValue()) {
        for (Map.Entry<String, String> ss : o.entrySet())
          b.textField(ss.getKey(), ss.getValue());
      }
      b.build();
    }
    w.close();
  }

  private LuceneIndex readIndex(String yamlResource) throws IOException {
    String lang = yamlResource.replaceFirst(".*_([a-z]{2})\\.yaml", "$1");

    LuceneIndex idx = LuceneIndex.RAM(new Locale(lang));
    try (InputStream is = getClass().getResourceAsStream(yamlResource))
    {
      readYaml(is, idx);
    }
    return idx;
  }

  private LuceneIndex da;

  @Before
  public void loadIndex() throws IOException {
    da = readIndex("sample_da.yaml");
  }

  @Test
  public void testSimple()
  {
    assertEquals(0, da.search("nevermind", "description").size());
    assertEquals(1, da.search("Rød begyndelse", "description").size());
    assertEquals(3, da.search("Knud Nielsen", "description").size());

    assertEquals(1, da.search("samlerobjekt", "description").size());
  }

  @Test
  public void testGeneralizationTables()
  {
    assertEquals(2, da.search("bord", "description").size());
    assertEquals(1, da.search("sofabord", "description").size());
    assertEquals(1, da.search("spisebord", "description").size());
  }

  @Test
  public void testGeneralizationChairs()
  {
    assertEquals(1, da.search("chaiselongue", "description").size());
    assertEquals(1, da.search("konferencestol", "description").size());

    assertEquals(3, da.search("stol", "description").size());
    assertEquals(2, da.search("kontorstol", "description").size());
  }

  @Test
  public void testPlural()
  {
    // "Et par skamler"
    assertEquals(1, da.search("skamler", "description").size());
    assertEquals(1, da.search("skammel", "description").size());
  }

  @Test
  public void testSynonym()
  {
    // "Bogkasse og vitrine"
    assertEquals(1, da.search("vitrine", "description").size());
    assertEquals(1, da.search("skab", "description").size());
  }
}
