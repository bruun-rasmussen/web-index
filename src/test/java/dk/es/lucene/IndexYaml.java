package dk.es.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class IndexYaml {
    private IndexYaml() {}

    private static void readYaml(InputStream is, LuceneIndex index) throws IOException {
        Yaml yaml = new Yaml();

        Map<Integer, List<Map<String, String>>> obj = (Map<Integer, List<Map<String, String>>>) yaml.load(is);

        LuceneIndex.Writer w = index.open(true);
        for (Map.Entry<Integer, List<Map<String, String>>> k : obj.entrySet()) {
            Long itemId = new Long(k.getKey());
            LuceneIndex.Builder b = w.newDocument(itemId);
            for (Map<String, String> o : k.getValue()) {
                for (Map.Entry<String, String> ss : o.entrySet())
                    b.textField(ss.getKey(), ss.getValue());
            }
            b.build();
        }
        w.close();
    }

    public static LuceneIndex readIndex(URL url) throws IOException {
        String name = url.getPath();
        String lang = name.replaceFirst(".*_([a-z]{2})\\.yaml", "$1");

        LuceneIndex idx = LuceneIndex.RAM(new Locale(lang));
        try (InputStream is = url.openStream()) {
            readYaml(is, idx);
        }
        return idx;
    }
    
}
