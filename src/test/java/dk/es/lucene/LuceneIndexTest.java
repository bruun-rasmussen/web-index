package dk.es.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.Assert.*;
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
    
  private void readYaml(InputStream is, LuceneIndex index) throws IOException {
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
  
  @Test
  public void testLoadYaml() throws IOException
  {
    LuceneIndex da = LuceneIndex.RAM(new Locale("da"));        
    InputStream is = getClass().getResourceAsStream("sample_da.yaml");
    assertNotNull(is);
    readYaml(is, da);
    is.close();
    
    assertEquals(0, da.search("nevermind", "description").size());
    assertEquals(1, da.search("Rød begyndelse", "description").size());
    assertEquals(3, da.search("Knud Nielsen", "description").size());
  }
}
