package dk.es.lucene;

import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Identical to {@link SnowballAnalyzer}, but with {@link BruunRasmussenFilter}
 * before the {@link SnowballFilter}
 * 
 * @author    kristoffer
 * @version   $Id$
 */
public class BruunRasmussenAnalyzer extends Analyzer
{
  private final static Logger LOG = LoggerFactory.getLogger(BruunRasmussenAnalyzer.class);

  private final String name;
  private final Set stopSet;
  private final Map brExceptions;

  /** Builds the named analyzer with no stop words. */
  public BruunRasmussenAnalyzer(String name)
  {
    this.name = name;
    this.stopSet = null;
    this.brExceptions = null;

    LOG.info("Initialized " + name);
  }

  /** Builds the named analyzer with the given stop words. */
  public BruunRasmussenAnalyzer(String name, String stopWords[])
  {
    this.name = name;
    this.stopSet = StopFilter.makeStopSet(stopWords);
    this.brExceptions = loadExceptions(name);
    
    LOG.info("Initialized " + name + " (ex: " + Arrays.toString(stopWords) + ")");
  }

  private static Map loadExceptions(String name)
  {
    URL source = BruunRasmussenAnalyzer.class.getResource("br-index-exceptions-" + name + ".txt");
    Properties exceptions = new Properties();
    Map brExceptions = new HashMap(exceptions.size());
    try
    {
      exceptions.load(source.openStream());
      for (Object key : exceptions.keySet())
        brExceptions.put(key.toString(), exceptions.getProperty(key.toString()));
    }
    catch (Exception e)
    {
      LOG.warn("Unable to load 'br-index-exceptions-" + name + ".txt'", e);
    }
    
    return brExceptions;
  }

  public TokenStream tokenStream(String fieldName, Reader reader)
  {
    TokenStream result = new StandardTokenizer(reader);
    result = new StandardFilter(result);
    result = new LowerCaseFilter(result);
    if (stopSet != null)
      result = new StopFilter(result, stopSet);

    result = new BruunRasmussenFilter(result, brExceptions);

    result = new SnowballFilter(result, name);

    return result;
  }
}
