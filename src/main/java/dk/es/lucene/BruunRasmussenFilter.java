package dk.es.lucene;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * Filter to fix special cases. Initially to fix special plural exceptions so
 * searches make some sense. In general all our index is reduced to singular
 * expressions, and the general rules for that have lots of exceptions
 * 
 * @author    kristoffer
 * @version   $Id$
 */
public class BruunRasmussenFilter extends TokenFilter
{
  private final static Logger LOG = LoggerFactory.getLogger(BruunRasmussenFilter.class);

  private final Map exceptions;

  public BruunRasmussenFilter(TokenStream input, Map brExceptions)
  {
    super(input);

    exceptions = brExceptions;
  }

  @Override
  public Token next(Token reusableToken)
      throws IOException
  {
    Token nextToken = input.next(reusableToken);

    if (nextToken != null && exceptions != null)
    {
      String term = nextToken.term().toLowerCase();
      String replacement = (String)exceptions.get(term);
      if (replacement != null)
      {
        nextToken.setTermBuffer(replacement);
        LOG.debug("Changed '" + term + "' to '" + replacement + "'");
      }
    }

    return nextToken;
  }
}
