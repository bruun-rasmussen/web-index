package dk.es.lucene;

import java.io.Reader;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
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
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.*;

/**
 * Identical to {@link SnowballAnalyzer}, but with {@link BruunRasmussenFilter}
 * before the {@link SnowballFilter}
 *
 * @author    kristoffer
 */
class BruunRasmussenAnalyzer extends Analyzer
{
  private final static Logger LOG = LoggerFactory.getLogger(BruunRasmussenAnalyzer.class);

  private final Locale loc;
  private final Set stopSet;
  private final Map exceptions;

  /** Builds the named analyzer with the given stop words. */
  BruunRasmussenAnalyzer(Locale loc, String stopWords[], Map exceptions)
  {
    this.loc = loc;
    this.stopSet = stopWords == null ? null : StopFilter.makeStopSet(stopWords);
    this.exceptions = exceptions;

    LOG.info("{} initialized, ex: {}", loc.getLanguage(), Arrays.toString(stopWords));
  }

  public TokenStream tokenStream(String fieldName, Reader reader)
  {
    TokenStream result = new StandardTokenizer(reader);
    result = new StandardFilter(result);
    result = new LowerCaseFilter(result);
    if (stopSet != null)
      result = new StopFilter(result, stopSet);

    // Synonym replacement before (and/)or after snowball stemming?
    // Replace before, and the exception list needs to include all word-endings,
    // replace after, and it must be aware of awkward stem-forms.
    // TODO: consider doing both! (in separate files)
    if (exceptions != null)
      result = new BruunRasmussenFilter(result, exceptions);
    result = new SnowballFilter(result, stemmerFor(loc));

    return result;
  }

  private static SnowballProgram stemmerFor(Locale loc) {
    String lang = loc.getLanguage();
    return "da".equals(lang) ? new DanishStemmer() :
           "sv".equals(lang) ? new SwedishStemmer() :
           "en".equals(lang) ? new EnglishStemmer() : null;
  }
}
