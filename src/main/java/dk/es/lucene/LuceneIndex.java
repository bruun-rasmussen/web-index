package dk.es.lucene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
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

  protected abstract IndexSearcher getSearcher()
    throws IOException;

  private Hits _search(String queryText, String fieldName)
    throws IOException, ParseException
  {
    queryText = LuceneHelper.normalize(queryText);
    queryText = LuceneHelper.escapeLuceneQuery(queryText).toLowerCase(m_loc);
    LOG.debug("query: \"{}\"", queryText);
    QueryParser parser = new QueryParser(fieldName, m_analyzer);
    parser.setDefaultOperator(QueryParser.AND_OPERATOR);
    Query query = parser.parse(queryText);

    Hits hits = getSearcher().search(query);
    LOG.info("\"{}\"[{}]: {} hit(s)", queryText, m_loc.getLanguage(), hits.length());
    return hits;
  }

  public Set<Long> search(String queryText, String fieldName) {
    Set<Long> baseIds = new HashSet();
    try
    {
      // Perform free-text query:
      Hits hits = _search(queryText, fieldName);

      // Collect item ids:
      for (int i = 0; i < hits.length(); i++)
      {
        Document doc = hits.doc(i);
        baseIds.add(new Long(doc.get(ITEM_ID)));
      }
    }
    catch (ParseException ex)
    {
      LOG.info(ex.getMessage());
    }
    catch (IOException ex)
    {
      LOG.error("Failed to execute query", ex);
    }
    return baseIds;
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

  private final static String ITEM_ID = "item_id";

  public void deleteItem(Long itemId) throws IOException {
    IndexReader reader = getReader();
    int deleteCount = reader.deleteDocuments(new Term(ITEM_ID, itemId.toString()));
    if (deleteCount > 0)
      LOG.info("removed item {} from {} index", itemId, getLanguageName());
    reader.close();
  }

  public Writer open(boolean create) throws IOException {
    IndexWriter writer = getWriter(create);
    return new Writer(writer);
  }

  public class Writer {
    private final IndexWriter writer;
    private int written = 0;

    private Writer(IndexWriter writer) {
      this.writer = writer;
    }

    public Builder newDocument(final Long itemId) {
      return new Builder() {
        private final Document doc = new Document();

        public Builder textField(String fieldName, String value)
        {
          if (StringUtils.isNotEmpty(value))
            doc.add(new Field(fieldName, LuceneHelper.normalize(value), Field.Store.NO, Field.Index.ANALYZED));
          return this;
        }

        public void build() throws IOException {
          doc.add(new Field(ITEM_ID, itemId.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
          writer.addDocument(doc);
          LOG.debug("added item {} ({})", itemId, m_loc.getLanguage());
          ++written;
        }
      };
    }

    public void close() throws IOException {
      if (written > 1) {
        writer.optimize();
        LOG.info("Index optimized");
      }

      writer.commit();
      writer.close();
    }
  }

  public static interface Builder
  {
    Builder textField(String fieldName, String value);
    void build() throws IOException;
  }

}
