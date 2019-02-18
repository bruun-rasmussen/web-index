package dk.es.lucene;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
/**
 * @author     TietoEnator Consulting
 * @since      18. februar 2004
 */
public class FileLuceneIndex extends LuceneIndex
{
  private final String m_path;

  private FileLuceneIndex(Locale loc)
  {
    super(loc);
    
    // Create an index called 'index' in a work directory
    // (java.io.tmpdir is not a good choice, since some systems are configured
    // to clean it automatically)
//  m_path = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "index_" + m_loc.getLanguage();

//  File indexDir = new File(System.getProperty("java.io.tmpdir"), "index_" + m_loc.getLanguage());
    File indexDir = new File(System.getProperty("dk.es.work.path"), "index_" + m_loc.getLanguage());
    indexDir.mkdirs();
    m_path = indexDir.getAbsolutePath();

    LOG.info("creating index in " + m_path);
  }

  public IndexWriter getWriter(boolean create)
    throws IOException
  {
    return new IndexWriter(m_path, m_analyzer, create, IndexWriter.MaxFieldLength.LIMITED);
  }

  public IndexReader getReader()
    throws IOException
  {
    return IndexReader.open(m_path);
  }

  public IndexSearcher getSearcher()
    throws IOException
  {
    return new IndexSearcher(m_path);
  }
}
