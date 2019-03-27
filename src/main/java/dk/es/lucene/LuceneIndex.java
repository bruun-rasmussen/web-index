package dk.es.lucene;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author TietoEnator Consulting
 * @since 28. januar 2004
 */
public abstract class LuceneIndex {

    protected final static Logger LOG = LoggerFactory.getLogger(LuceneIndex.class);

    protected final Locale loc;
    protected final Analyzer analyzer;

    public static LuceneIndex RAM(Locale loc) {
        final RAMDirectory dir = new RAMDirectory();
        return getIndex(loc, dir);
    }

    public static LuceneIndex DISK(Locale loc, File indexDir) {
        final String path = indexDir.getAbsolutePath();
        LOG.info("creating index in {}", path);
        indexDir.mkdirs();
        try {
            Directory dir = FSDirectory.open(new File(path));
            return getIndex(loc, dir);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create DISK index: " + e.getMessage(), e);
        }
    }

    private static LuceneIndex getIndex(final Locale loc, final Directory dir) {
        return new LuceneIndex(loc) {
            protected IndexWriter getWriter(boolean create) throws IOException {
                IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_44, analyzer);
                if (create) {
                    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                } else {
                    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                }
                return new IndexWriter(dir, config);
            }

            protected IndexReader getReader() throws IOException {
                return DirectoryReader.open(dir);
            }

            protected IndexSearcher getSearcher() throws IOException {
                return new IndexSearcher(getReader());
            }
        };
    }

    protected LuceneIndex(Locale loc) {
        this.loc = loc;
        analyzer = createAnalyzer();
    }

    public Locale getLocale() {
        return loc;
    }

    public String getLanguageName() {
        return getLocale().getDisplayLanguage(Locale.ENGLISH);
    }

    protected abstract IndexWriter getWriter(boolean create) throws IOException;

    protected abstract IndexReader getReader() throws IOException;

    protected abstract IndexSearcher getSearcher() throws IOException;

    private TopDocs _search(String queryText, String fieldName) throws IOException, ParseException {
        queryText = LuceneHelper.normalize(queryText);
        queryText = LuceneHelper.escapeLuceneQuery(queryText).toLowerCase(loc);
        LOG.debug("query: \"{}\"", queryText);

        BooleanQuery query = new BooleanQuery();

        // straight query
        query.add(getQuery(queryText, fieldName), BooleanClause.Occur.SHOULD);

        // reverse query
        if ("description".equals(fieldName)) {
            query.add(getQuery(queryText, "description_rev"), BooleanClause.Occur.SHOULD);
        }

        TopDocs hits = getSearcher().search(query, 100); // TODO max records

        LOG.info("\"{}\"[{}] query:{}, {} hit(s)", queryText, loc.getLanguage(), query, hits.totalHits);
        return hits;
    }

    private BooleanQuery getQuery(String queryText, String fieldName) {
        List<String> parts = LuceneHelper.tokenizeString(analyzer, fieldName, queryText);
        BooleanQuery query = new BooleanQuery();
        for (String part : parts) {
            if (fieldName.endsWith("_rev")) {
                if(parts.size() == 1) {
                    query.add(new PrefixQuery(new Term(fieldName, part)), BooleanClause.Occur.MUST);
                }
            } else {
                BooleanQuery partQuery = new BooleanQuery();
                if(parts.size() == 1){
                    partQuery.add(new PrefixQuery(new Term(fieldName, part)), BooleanClause.Occur.SHOULD);
                }
//                partQuery.add(new BoostedQuery(new TermQuery(new Term(fieldName, part)), new ConstValueSource(2f)), BooleanClause.Occur.SHOULD);
                partQuery.add(new TermQuery(new Term(fieldName, part)), BooleanClause.Occur.SHOULD);
                query.add(partQuery, BooleanClause.Occur.MUST);
            }

        }
        return query;
    }

    public Set<Long> search(String queryText, String fieldName) {
        Set<Long> baseIds = new HashSet<>();
        try {
            // Perform free-text query:
            TopDocs hits = _search(queryText, fieldName);

            IndexReader reader = getReader();

            // Collect item ids:
            for (ScoreDoc scoreDoc : hits.scoreDocs) {
                Document doc = reader.document(scoreDoc.doc);
                baseIds.add(new Long(doc.get(ITEM_ID)));

            }
        } catch (ParseException ex) {
            LOG.info(ex.getMessage());
        } catch (IOException ex) {
            LOG.error("Failed to execute query", ex);
        }
        return baseIds;
    }

    private Analyzer createAnalyzer() {
        String shortLang = loc.getLanguage();
        String longLang = getLanguageName();

        URL stopWordsUrl = LuceneIndex.class.getResource("stop_" + shortLang + ".txt");
        if (stopWordsUrl != null) {
            try {
                String[] stopWords = parseWords(stopWordsUrl);
                return new BruunRasmussenAnalyzer(longLang, stopWords);
            } catch (IOException ex) {
                LOG.error(stopWordsUrl + ": failed loading stop-words", ex);
            }
        }

        return new BruunRasmussenAnalyzer(longLang);
    }

    private static String[] parseWords(URL source) throws IOException {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(source.openStream(), StandardCharsets.UTF_8));
        ArrayList<String> result = new ArrayList<>();
        String line;
        while ((line = rdr.readLine()) != null) {
            int barPos = line.indexOf('|');
            if (barPos > 0) {
                line = line.substring(0, barPos - 1);
            }
            line = line.trim();
            if (line.length() > 0) {
                result.add(line);
            }
        }
        return result.toArray(new String[0]);
    }

    private final static String ITEM_ID = "item_id";

    public void deleteItem(Long itemId) throws IOException {
        IndexWriter writer = getWriter(false);
        writer.deleteDocuments(new Term(ITEM_ID, itemId.toString()));
        writer.close();
    }

    public Writer open(boolean create) throws IOException {
        IndexWriter writer = getWriter(create);
        return new Writer(writer);
    }

    public class Writer {
        private final IndexWriter writer;

        private Writer(IndexWriter writer) {
            this.writer = writer;
        }

        public Builder newDocument(final Long itemId) {
            return new Builder() {
                private final Document doc = new Document();

                public Builder textField(String fieldName, String value) {
                    if (StringUtils.isNotEmpty(value)) {
                        doc.add(new TextField(fieldName, LuceneHelper.normalize(value), Field.Store.NO));
                        if (fieldName.equals("description")) {
                            doc.add(new TextField("description_rev", LuceneHelper.normalize(value), Field.Store.NO));
                        }
                    }
                    return this;
                }

                public void build() throws IOException {
                    doc.add(new StringField(ITEM_ID, itemId.toString(), Field.Store.YES));
                    writer.addDocument(doc);
                    LOG.debug("added item {} ({})", itemId, loc.getLanguage());
                }
            };
        }

        public void close() throws IOException {
            writer.commit();
            writer.close();
        }
    }

    public interface Builder {
        Builder textField(String fieldName, String value);

        void build() throws IOException;
    }

}
