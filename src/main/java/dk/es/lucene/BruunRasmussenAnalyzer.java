package dk.es.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.reverse.ReverseStringFilter;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Identical to {@link SnowballAnalyzer}, but with {@link BruunRasmussenFilter}
 * before the {@link SnowballFilter}
 *
 * @author kristoffer
 */
public class BruunRasmussenAnalyzer extends Analyzer {
    private final static Logger LOG = LoggerFactory.getLogger(BruunRasmussenAnalyzer.class);

    private final String name;
    private final CharArraySet stopSet;
    private final Map<String, String> brExceptions;

    /**
     * Builds the named analyzer with no stop words.
     */
    public BruunRasmussenAnalyzer(String name) {
        super(new PerFieldReuseStrategy());
        this.name = name;
        this.stopSet = null;
        this.brExceptions = null;

        LOG.info("Initialized " + name);
    }

    /**
     * Builds the named analyzer with the given stop words.
     */
    public BruunRasmussenAnalyzer(String name, String[] stopWords) {
        super(new PerFieldReuseStrategy());
        this.name = name;
        this.stopSet = StopFilter.makeStopSet(Common.VERSION, stopWords, true);
        this.brExceptions = loadExceptions(name);

        LOG.info("Initialized " + name + " (ex: " + Arrays.toString(stopWords) + ")");
    }

    private static Map<String, String> loadExceptions(String name) {
        URL source = BruunRasmussenAnalyzer.class.getResource("br-index-exceptions-" + name + ".txt");
        Properties exceptions = new Properties();
        Map<String, String> brExceptions = new HashMap<>(exceptions.size());
        try {
            exceptions.load(new InputStreamReader(source.openStream(), StandardCharsets.UTF_8));
            for (Object key : exceptions.keySet()) {
                brExceptions.put(key.toString(), exceptions.getProperty(key.toString()));
            }
        } catch (Exception e) {
            LOG.warn("Unable to load 'br-index-exceptions-" + name + ".txt'", e);
        }

        return brExceptions;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {

        StandardTokenizer src = new StandardTokenizer(Common.VERSION, reader);
        TokenStream result = new StandardFilter(Common.VERSION, src);

        // lower case
        result = new LowerCaseFilter(Common.VERSION, result);


        // synonyms
        // Use lucene's synonym filter instead of the custom one
        result = new BruunRasmussenFilter(result, brExceptions);
//        if (brExceptions != null && !brExceptions.isEmpty()) {
//            SynonymMap.Builder synonymMap = new SynonymMap.Builder(true);
//            for (String key : brExceptions.keySet()) {
//                String val = brExceptions.get(key);
//                synonymMap.add(new CharsRef(key), new CharsRef(val), true);
//            }
//            try {
//                result = new SynonymFilter(result, synonymMap.build(), true);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }


        // stop set
        if (stopSet != null) {
            result = new StopFilter(Common.VERSION, result, stopSet);
        }

        // stemmer
        result = new SnowballFilter(result, name);


        // reverse
        if ("description_rev".equals(fieldName)) {
            result = new ReverseStringFilter(Common.VERSION, result);
        }

        return new TokenStreamComponents(src, result);
    }


}
