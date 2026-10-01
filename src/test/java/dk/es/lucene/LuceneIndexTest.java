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
  private static void readYaml(InputStream is, LuceneIndex index) throws IOException {
    Yaml yaml = new Yaml();

    Map<Integer, List<Map<String,String>>> obj = (Map<Integer, List<Map<String,String>>>)yaml.load(is);

    LuceneIndex.Writer w = index.create();
    for (Map.Entry<Integer, List<Map<String,String>>> k : obj.entrySet())
    {
      Long itemId = Long.valueOf(k.getKey());
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
  public void testDelete() throws IOException
  {
    assertEquals(1, da.search("Rød begyndelse", "description").size());
    da.deleteItem(300692980L);
    assertEquals(0, da.search("Rød begyndelse", "description").size());

    // Do it again to test for itempotency:
    da.deleteItem(300692980L);
    assertEquals(0, da.search("Rød begyndelse", "description").size());
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

    assertEquals(4, da.search("stol", "description").size());
    assertEquals(2, da.search("kontorstol", "description").size());
    assertEquals(2, da.search("lænestol", "description").size());
  }

  @Test
  public void testGeneralizationRifle()
  {
    assertEquals(1, da.search("kavallerikarabin", "description").size());
    assertEquals(1, da.search("karabin", "description").size());
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

  @Test
  public void testLettersDigitsSplit()
  {
    // Indexed as 'bm 1', found as either:
    assertEquals(1, da.search("bm1", "description").size());
    assertEquals(1, da.search("BM 1", "description").size());
    // Indexed as 'FJ66', likewise:
    assertEquals(1, da.search("fj66", "description").size());
    assertEquals(1, da.search("fj 66", "description").size());

    assertEquals(1, da.search("1908/102A", "auctionCatalogNumber").size());
  }

  @Test
  public void testNormalize()
  {
    assertEquals("bm 1 FJ 45 PK 22 ch 26", LuceneHelper.normalize("bm1 FJ45 PK22 ch-26"));
    assertEquals("h2o 1907/806A 3x4", LuceneHelper.normalize("h2o 1907/806A 3x4"));
    assertEquals("ø 20 cm", LuceneHelper.normalize("ø20 cm"));
  }

  @Test
  public void testNormalizeAccents()
  {
    assertEquals("Cafe Muller Højdejusterbar", LuceneHelper.normalize("Café Müller Højdejustérbar"));
    assertEquals("EN 3", LuceneHelper.normalize("ÉÑ3"));
    // Æ, Ø and Å are kept, but Swedish/German ö is not:
    assertEquals("Æble Øre Ångstrom", LuceneHelper.normalize("Æble Øre Ångström"));
  }

  @Test
  public void testNormalizeDesignerModels()
  {
    assertEquals("pk 22 PK 22 ch 26 CH 15 pk 22 Pk 22", LuceneHelper.normalize("pk-22 PK/22 ch.26 CH  15 pk - 22 Pk22"));
    assertEquals("bm 1 BM 1 bm 1 Bm 1", LuceneHelper.normalize("bm-1 BM/1 bm.1 Bm  1"));
    assertEquals("PK 22", LuceneHelper.normalize("PK 22"));
    // Only at the start of a word:
    assertEquals("APK 15", LuceneHelper.normalize("APK 15"));
    // Not when followed by letters:
    assertEquals("pk22a", LuceneHelper.normalize("pk22a"));
    assertEquals("PK 22-23", LuceneHelper.normalize("PK22-23"));
  }

  @Test
  public void testNormalizeLettersDigits()
  {
    assertEquals("v 1825 æ 5 e 5", LuceneHelper.normalize("v1825 æ5 é5"));
    assertEquals("(FJ 45) bm 1.", LuceneHelper.normalize("(FJ45) bm1."));
    // Left alone when letters and digits are mixed, or digits come first:
    assertEquals("h2o a1b2 66FJ 3x4", LuceneHelper.normalize("h2o a1b2 66FJ 3x4"));
    assertEquals("1907/806A M/1821/23", LuceneHelper.normalize("1907/806A M/1821/23"));
  }

  @Test
  public void testEscapeLuceneQuery()
  {
    assertEquals("a\\+b \\(c\\) x\\:y\\* \\-foo a\\\\b", LuceneHelper.escapeLuceneQuery("a+b (c) x:y* -foo a\\b"));
    // Quotes and slashes pass through:
    assertEquals("\"Morgen Sang\" 1907/806", LuceneHelper.escapeLuceneQuery("\"Morgen Sang\" 1907/806"));
  }

  @Test
  public void testSearchNormalized()
  {
    // Indexed as 'PK 54':
    assertEquals(1, da.search("PK54", "description").size());
    assertEquals(1, da.search("pk-54", "description").size());
    assertEquals(1, da.search("pk/54", "description").size());
    // Indexed as 'bm 1':
    assertEquals(1, da.search("bm-1", "description").size());
    assertEquals(1, da.search("BM/1", "description").size());
    // Indexed as 'v1825':
    assertEquals(1, da.search("v 1825", "description").size());
    // Indexed as 'Højdejustérbar':
    assertEquals(1, da.search("højdejusterbar", "description").size());
    assertEquals(1, da.search("højdejustérbar", "description").size());
  }
}
