package dk.es.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
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
import org.apache.lucene.store.RAMDirectory;

/**
 * @author     TietoEnator Consulting
 * @since      28. januar 2004
 */
public abstract class LuceneIndex
{
  protected final static Logger LOG = LoggerFactory.getLogger(LuceneIndex.class);

  private final Locale m_loc;
  protected final Analyzer m_analyzer;

  public static LuceneIndex RAM(Locale loc) {
    final RAMDirectory dir = new RAMDirectory();

    return new LuceneIndex(loc) {
      protected IndexWriter getWriter(boolean create) throws IOException {
        return new IndexWriter(dir, m_analyzer, create, IndexWriter.MaxFieldLength.LIMITED);
      }
      protected IndexReader getReader() throws IOException {
        return IndexReader.open(dir);
      }
      protected IndexSearcher getSearcher() throws IOException {
        return new IndexSearcher(dir);
      }
    };
  }

  public static LuceneIndex DISK(Locale loc, File indexDir) {
    final String path = indexDir.getAbsolutePath();
    LOG.info("creating index in {}", path);
    indexDir.mkdirs();

    return new LuceneIndex(loc) {
      protected IndexWriter getWriter(boolean create) throws IOException {
        return new IndexWriter(path, m_analyzer, create, IndexWriter.MaxFieldLength.LIMITED);
      }
      protected IndexReader getReader() throws IOException {
        return IndexReader.open(path);
      }
      protected IndexSearcher getSearcher() throws IOException {
        return new IndexSearcher(path);
      }
    };
  }

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

  protected abstract IndexWriter getWriter(boolean create)
    throws IOException;

  protected abstract IndexReader getReader()
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
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      String stopWords[] = parseWords(cl.getResource("lucene/stopwords_" + m_loc.getLanguage() + ".txt"));
      Map exceptions = loadExceptions(cl.getResource("lucene/exceptions_" + m_loc.getLanguage() + ".txt"));

      return new BruunRasmussenAnalyzer(m_loc, stopWords, exceptions);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }


  private static Map loadExceptions(URL source)
    throws IOException
  {
    if (source == null)
      return null;

    Properties exceptions = new Properties();
    InputStream is = source.openStream();
    try {
      exceptions.load(is);
      return exceptions;
    }
    finally {
      is.close();
    }
  }


  private static String[] parseWords(URL source)
    throws IOException
  {
    if (source == null)
        return null;

    BufferedReader rdr = new BufferedReader(new InputStreamReader(source.openStream(), "iso-8859-1"));
    try {
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
    finally {
      rdr.close();
    }
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
