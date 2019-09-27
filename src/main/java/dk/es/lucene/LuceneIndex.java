package dk.es.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
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
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.DanishStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.SwedishStemmer;

/**
 * @author     TietoEnator Consulting
 * @since      28. januar 2004
 */
public abstract class LuceneIndex
{
  protected final static Logger LOG = LoggerFactory.getLogger(LuceneIndex.class);

  private final Locale loc;
  protected final Analyzer indexAnalyzer;
  protected final Analyzer queryAnalyzer;

  public static LuceneIndex RAM(Locale loc, String srcEncoding) {
    final RAMDirectory dir = new RAMDirectory();

    return new LuceneIndex(loc, srcEncoding) {
      protected IndexWriter getWriter(boolean create) throws IOException {
        return new IndexWriter(dir, indexAnalyzer, create, IndexWriter.MaxFieldLength.LIMITED);
      }
      protected IndexReader getReader() throws IOException {
        return IndexReader.open(dir);
      }
      protected IndexSearcher getSearcher() throws IOException {
        return new IndexSearcher(dir);
      }
    };
  }

  public static LuceneIndex DISK(Locale loc, File indexDir, String srcEncoding) {
    final String path = indexDir.getAbsolutePath();
    LOG.info("creating index in {}", path);
    indexDir.mkdirs();

    return new LuceneIndex(loc, srcEncoding) {
      protected IndexWriter getWriter(boolean create) throws IOException {
        return new IndexWriter(path, indexAnalyzer, create, IndexWriter.MaxFieldLength.LIMITED);
      }
      protected IndexReader getReader() throws IOException {
        return IndexReader.open(path);
      }
      protected IndexSearcher getSearcher() throws IOException {
        return new IndexSearcher(path);
      }
    };
  }

  protected LuceneIndex(Locale loc, String srcEncoding)
  {
    this.loc = loc;
    this.indexAnalyzer = analyzer(loc, true, srcEncoding);
    this.queryAnalyzer = analyzer(loc, false, srcEncoding);
  }

  public Locale getLocale()
  {
    return loc;
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
    queryText = LuceneHelper.escapeLuceneQuery(queryText).toLowerCase(loc);
    LOG.debug("query: \"{}\"[{}]", queryText, loc.getLanguage());
    QueryParser parser = new QueryParser(fieldName, queryAnalyzer);
    parser.setDefaultOperator(QueryParser.AND_OPERATOR);
    Query query = parser.parse(queryText);

    Hits hits = getSearcher().search(query);
    LOG.info("\"{}\"[{}]: {} hit(s)", queryText, loc.getLanguage(), hits.length());
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

  private static Analyzer analyzer(Locale loc, boolean generalize, String srcEncoding)
  {
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      String stopWords[] = parseWords(cl.getResource("lucene/stopwords_" + loc.getLanguage() + ".txt"));

      final Set stopSet = stopWords == null ? null : StopFilter.makeStopSet(stopWords);
      final Map exceptions = loadMap(cl.getResource("lucene/exceptions_" + loc.getLanguage() + ".txt"));
      final Map generalizations = generalize ? loadMap(cl.getResource("lucene/generalizations_" + loc.getLanguage() + ".txt")) : null;
      final SnowballProgram stemmer = stemmerFor(loc);

      return new Analyzer() {
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
            result = new SubstitutionFilter(result, exceptions);
          result = new SnowballFilter(result, stemmer);
          if (generalizations != null)
            result = new AliasFilter(result, generalizations);

          return result;
        }
      };
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static SnowballProgram stemmerFor(Locale loc) {
    String lang = loc.getLanguage();
    return "da".equals(lang) ? new DanishStemmer() :
           "sv".equals(lang) ? new SwedishStemmer() :
           "en".equals(lang) ? new EnglishStemmer() : null;
  }

  private static Map loadMap(URL source)
    throws IOException
  {
    if (source == null)
      return null;

    Properties exceptions = new Properties();
    try (Reader is = new InputStreamReader(source.openStream(), "UTF-8")) {
      exceptions.load(is);
      LOG.info("{} loaded ({} terms)", source, exceptions.size());
    }
    return exceptions;
  }


  private static String[] parseWords(URL source)
    throws IOException
  {
    if (source == null)
        return null;

    try (BufferedReader rdr = new BufferedReader(new InputStreamReader(source.openStream(), "UTF-8")))
    {
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
      LOG.info("{} loaded ({} terms)", source, result.size());
      return (String[])result.toArray(new String[result.size()]);
    }
  }

  private final static String ITEM_ID = "item_id";

  public void deleteItem(Long itemId) throws IOException {
    IndexReader reader = getReader();
    int deleteCount = reader.deleteDocuments(new Term(ITEM_ID, itemId.toString()));
    if (deleteCount > 0)
      LOG.info("removed item {}({}) from index", itemId, loc.getLanguage());
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
          LOG.debug("added item {}[{}]", itemId, loc.getLanguage());
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

  private static class SubstitutionFilter extends TokenFilter
  {
    private final Map substitutions;

    public SubstitutionFilter(TokenStream input, Map substitutions)
    {
      super(input);
      this.substitutions = substitutions;
    }

    @Override
    public Token next(Token reusableToken)
        throws IOException
    {
      Token nextToken = input.next(reusableToken);

      if (nextToken != null && substitutions != null)
      {
        String term = nextToken.term().toLowerCase();
        String replacement = (String)substitutions.get(term);
        if (replacement != null)
        {
          nextToken.setTermBuffer(replacement);
          LOG.debug("Changed '{}' to '{}'", term, replacement);
        }
      }
      return nextToken;
    }
  }

  private static class AliasFilter extends TokenFilter
  {
    private final Map aliases;
    private String alias;

    public AliasFilter(TokenStream input, Map aliases)
    {
      super(input);
      this.aliases = aliases;
    }

    @Override
    public Token next(Token reusableToken)
        throws IOException
    {
      if (alias != null) {
        LOG.debug("inserting alias '{}'", alias);
        Token t = _fixedToken(alias);
        alias = (String)aliases.get(alias);
        return t;
      }

      Token next = input.next(reusableToken);
      if (next == null)
        return null;

      String term = next.term().toLowerCase();
      alias = (String)aliases.get(term);

      return next;
    }
  }

  private static Token _fixedToken(String word) {
    char chars[] = word.toCharArray();
    return new Token(chars, 0, chars.length, 0, chars.length);
  }

}
