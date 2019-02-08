package dk.es.lucene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

/**
 * @author     TietoEnator Consulting
 * @since      28. januar 2004
 * @version    $Id$
 */
public abstract class LuceneIndex
{
  protected final static Logger LOG = LoggerFactory.getLogger(LuceneIndex.class);

  protected final Locale m_loc;
  protected final Analyzer m_analyzer;

  protected LuceneIndex(Locale loc)
  {
    m_loc = loc;
    m_analyzer = createAnalyzer();    
  }

  public Locale getLocale()
  {
    return m_loc;
  }

  public String getLanguageName()
  {
    String shortLang = getLocale().getLanguage();
    return
        "da".equals(shortLang) ? "Danish" :
        "en".equals(shortLang) ? "English" :
        "sv".equals(shortLang) ? "Swedish" : "Unknown";
  }

  public abstract IndexWriter getWriter(boolean create)
    throws IOException;

  public abstract IndexReader getReader()
    throws IOException;

  public abstract IndexSearcher getSearcher()
    throws IOException;

  public Hits search(String queryText, String fieldName)
    throws IOException, ParseException
  {
    queryText = LuceneHelper.normalize(queryText);
    queryText = LuceneHelper.escapeLuceneQuery(queryText).toLowerCase(m_loc);
    LOG.debug("query: " + queryText);
    QueryParser parser = new QueryParser(fieldName, m_analyzer);
    parser.setDefaultOperator(QueryParser.AND_OPERATOR);
    Query query = parser.parse(queryText);
    return getSearcher().search(query);
  }

  private Analyzer createAnalyzer()
  {
    String shortLang = m_loc.getLanguage();
    String longLang = getLanguageName();

    URL stopWordsUrl = LuceneIndex.class.getResource("stop_" + shortLang + ".txt");
    if (stopWordsUrl != null)
      try
      {
        String stopWords[] = parseWords(stopWordsUrl);
        return new BruunRasmussenAnalyzer(longLang, stopWords);
      }
      catch (IOException ex)
      {
        LOG.error(stopWordsUrl + ": failed loading stop-words", ex);
      }

    return new BruunRasmussenAnalyzer(longLang);
  }

  private static String[] parseWords(URL source)
    throws IOException
  {
    BufferedReader rdr = new BufferedReader(new InputStreamReader(source.openStream(), "iso-8859-1"));
    ArrayList result = new ArrayList();
    String line;
    while ((line = rdr.readLine()) != null)
    {
      int barPos = line.indexOf('|');
      if (barPos > 0)
        line = line.substring(0, barPos - 1);
      line = line.trim();
      if (line.length() > 0)
        result.add(line);
    }
    return (String[])result.toArray(new String[result.size()]);
  }
}
