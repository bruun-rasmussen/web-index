package dk.es.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Filter to fix special cases. Initially to fix special plural exceptions so
 * searches make some sense. In general all our index is reduced to singular
 * expressions, and the general rules for that have lots of exceptions
 *
 * @author kristoffer
 */
public final class BruunRasmussenFilter extends TokenFilter {

    private final static Logger LOG = LoggerFactory.getLogger(BruunRasmussenFilter.class);

    private final Map<String, String> exceptions;

    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);

    BruunRasmussenFilter(TokenStream input, Map<String, String> brExceptions) {
        super(input);

        exceptions = brExceptions;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        if (exceptions != null) {
            String term = new String(termAttribute.buffer(), 0, termAttribute.length()).toLowerCase();
            String replacement = exceptions.get(term);
            if (replacement != null) {
                termAttribute.setEmpty().append(replacement);
                LOG.debug("Changed '" + term + "' to '" + replacement + "'");
            }
        }

        return true;
    }
}
