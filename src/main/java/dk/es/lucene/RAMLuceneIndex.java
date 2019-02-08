package dk.es.lucene;

import java.io.IOException;
import java.util.Locale;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;

/**
 * @author     TietoEnator Consulting
 * @since      18. februar 2004
 * @version    $Id$
 */
public class RAMLuceneIndex extends LuceneIndex
{
  private final RAMDirectory m_dir;

  public RAMLuceneIndex(Locale loc)
  {
    super(loc);

    // Create an in-memory index:
    m_dir = new RAMDirectory();
  }

  public IndexWriter getWriter(boolean create)
    throws IOException
  {
    return new IndexWriter(m_dir, m_analyzer, create, IndexWriter.MaxFieldLength.LIMITED);
  }

  public IndexReader getReader()
    throws IOException
  {
    return IndexReader.open(m_dir);
  }

  public IndexSearcher getSearcher()
    throws IOException
  {
    return new IndexSearcher(m_dir);
  }
}
