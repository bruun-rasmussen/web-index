package dk.es.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.DanishStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.SwedishStemmer;

/**
 * @author     TietoEnator Consulting
 * @since      28. januar 2004
 */
public class LuceneIndex
{
  private final static Logger LOG = LoggerFactory.getLogger(LuceneIndex.class);

  private final static Version VER = Version.LUCENE_36;

  private final Locale loc;
  private final Directory dir;
  private final Analyzer indexAnalyzer;
  private final Analyzer queryAnalyzer;

  public static LuceneIndex RAM(Locale loc) {
    return new LuceneIndex(loc, new RAMDirectory());
  }

  public static LuceneIndex DISK(Locale loc, File indexDir) throws IOException {
    String path = indexDir.getAbsolutePath();
    LOG.info("creating index in {}", path);
    indexDir.mkdirs();
    Directory dir = FSDirectory.open(indexDir);

    return new LuceneIndex(loc, dir);
  }

  protected LuceneIndex(Locale loc, Directory dir)
  {
    this.loc = loc;
    this.dir = dir;
    this.indexAnalyzer = analyzer(loc, true);
    this.queryAnalyzer = analyzer(loc, false);
  }

  public Locale getLocale()
  {
    return loc;
  }

  private Query _query(String queryText, String fieldName)
    throws IOException, ParseException
  {
    QueryParser parser = new QueryParser(VER, fieldName, queryAnalyzer);
    parser.setDefaultOperator(QueryParser.AND_OPERATOR);
    // Sometimes throws StringIndexOutOfBoundsException from
    // inside org.tartarus.snowball.ext.DanishStemmer.stem():
    return parser.parse(queryText);
  }

  public Set<Long> search(String queryText, String fieldName) {
    queryText = LuceneHelper.normalize(queryText);
    queryText = LuceneHelper.escapeLuceneQuery(queryText).toLowerCase(loc);

    try {
      LOG.debug("query: \"{}\"[{}_{}]", queryText, fieldName, loc.getLanguage());
      Query query = _query(queryText, fieldName);

      // Perform free-text query:
      IndexSearcher searcher = new IndexSearcher(IndexReader.open(dir));
      TopFieldDocs hits = searcher.search(query, 1000, Sort.RELEVANCE);
      LOG.info("\"{}\"[{}_{}]: {} hit(s)", queryText, fieldName, loc.getLanguage(), hits.totalHits);

      Set<Long> baseIds = new HashSet();
      // Collect item ids:
      for (ScoreDoc sc : hits.scoreDocs) {
        Document doc = searcher.doc(sc.doc);
        baseIds.add(Long.valueOf(doc.get(ITEM_ID)));
      }
      return baseIds;
    }
    catch (Exception ex) {
      LOG.error("\"{}\"[{}_{}] - failed to execute query", queryText, fieldName, loc.getLanguage(), ex);
      return Collections.emptySet();
    }
  }

  private static Analyzer analyzer(final Locale loc, boolean generalize)
  {
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      String stopWords[] = parseWords(cl.getResource("lucene/stopwords_" + loc.getLanguage() + ".txt"));

      final Set stopSet = stopWords == null ? null : StopFilter.makeStopSet(VER, stopWords);
      final Map exceptions = loadMap(cl.getResource("lucene/exceptions_" + loc.getLanguage() + ".txt"));
      final Map generalizations = generalize ? loadMap(cl.getResource("lucene/generalizations_" + loc.getLanguage() + ".txt")) : null;

      return new Analyzer() {
        public TokenStream tokenStream(String fieldName, Reader reader)
        {
          TokenStream result = new StandardTokenizer(VER, reader);
          result = new StandardFilter(VER, result);
          result = new LowerCaseFilter(VER, result);

          if (stopSet != null)
            result = new StopFilter(VER, result, stopSet);

          // Synonym replacement before (and/)or after snowball stemming?
          // Replace before, and the exception list needs to include all word-endings,
          // replace after, and it must be aware of awkward stem-forms.
          // TODO: consider doing both! (in separate files)
          if (exceptions != null)
            result = new SubstitutionFilter(result, exceptions);
          result = new SnowballFilter(result, stemmerFor(loc));
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
      return (String[])result.toArray(new String[0]);
    }
  }

  private final static String ITEM_ID = "item_id";

  public void deleteItem(Long itemId) throws IOException {
    try (IndexWriter writer = new IndexWriter(dir, appenderCfg())) {
      writer.deleteDocuments(new Term(ITEM_ID, itemId.toString()));
      LOG.debug("item {} gone", itemId);
    }
  }

  public Writer create() throws IOException {
    return new Writer(new IndexWriter(dir, creatorCfg()));
  }

  public Writer open() throws IOException {
    return new Writer(new IndexWriter(dir, appenderCfg()));
  }

  /**
   * @deprecated   Use create() or open() instead
   */
  public Writer open(boolean create) throws IOException {
    return create ? create() : open();
  }

  private IndexWriterConfig creatorCfg() {
    return new IndexWriterConfig(VER, indexAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE);
  }

  private IndexWriterConfig appenderCfg() {
    return new IndexWriterConfig(VER, indexAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.APPEND);
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

    public Locale getLocale() {
      return loc;
    }
  }

  public static interface Builder
  {
    Builder textField(String fieldName, String value);
    void build() throws IOException;
  }

  private static class SubstitutionFilter extends TokenFilter
  {
    private final Map<String,String> substitutions;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public SubstitutionFilter(TokenStream input, Map<String,String> substitutions)
    {
      super(input);
      this.substitutions = substitutions;
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (!input.incrementToken())
        return false;

      String term = new String(termAtt.buffer(), 0, termAtt.length()).toLowerCase();
      String replacement = substitutions.get(term);
      if (replacement != null) {
        char rep[] = replacement.toCharArray();
        termAtt.copyBuffer(rep, 0, rep.length);
        LOG.debug("Changed '{}' to '{}'", term, replacement);
      }

      return true;
    }
  }

  private static class AliasFilter extends TokenFilter
  {
    private final Map<String,String> aliases;
    private String alias;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public AliasFilter(TokenStream input, Map<String,String> aliases)
    {
      super(input);
      this.aliases = aliases;
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (alias != null) {
        LOG.debug("inserting alias '{}'", alias);

        char sub[] = alias.toCharArray();
        termAtt.copyBuffer(sub, 0, sub.length);

        alias = aliases.get(alias);
        return true;
      }

      if (!input.incrementToken())
        return false;

      String term = new String(termAtt.buffer(), 0, termAtt.length()).toLowerCase();
      alias = aliases.get(term);

      return true;
    }
  }
}
