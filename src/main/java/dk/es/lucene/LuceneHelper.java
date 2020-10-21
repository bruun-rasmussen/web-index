package dk.es.lucene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author     TietoEnator Consulting
 * @since      6. april 2004
 */
class LuceneHelper
{
  private final static Logger LOG = LoggerFactory.getLogger(LuceneHelper.class);

  private LuceneHelper() { }


  /**
   * TODO: move to database and make locale-dependent:
   */
  private final static RegexpReplacement STANDARD_ITEM_REPLACEMENTS[] =
  {
    // Standardize Poul Kjærholm models and Wegner chairs:
    // 'pk-22', 'ch 15', 'ch26' -> 'pk_22', 'ch_15', 'ch_26'
    new RegexpReplacement("\\b([Pp][Kk]|[Cc][Hh])[-/. ]*([0-9]+)\\b", "$1 $2"),
  };

  private final static String ACCENTED_CHARS = "ÁÀÄÂáàäâÉÈËÊéèëêÍÌÏÎíìïîÓÒÖÔóòöôÚÙÜÛúùüûÝýÑñ";
  private final static String REPLACED_CHARS = "AAAAaaaaEEEEeeeeIIIIiiiiOOOOooooUUUUuuuuYyNn";

  // '"'
  private final static char LUCENE_QUERY_SPECIAL_CHARS[] =
      {'+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']', '^', '~', '*', '?', ':', '\\'};
  private final static char LUCENE_QUERY_ESCAPE_CHAR = '\\';

  public static String escapeLuceneQuery(String query)
  {
    return escapeString(query, LUCENE_QUERY_SPECIAL_CHARS, LUCENE_QUERY_ESCAPE_CHAR);
  }

  /**
   * TODO: Check to see if commons contains a suitable replacement:
   */
  private static String escapeString(String text, char specials[], char escapeChar)
  {
    char src[] = text.toCharArray();
    StringBuilder res = new StringBuilder(src.length);
    for (int i = 0; i < src.length; i++)
    {
      for (int j = 0; j < specials.length; j++)
        if (src[i] == specials[j])
        {
          res.append(escapeChar);
          break;
        }
      res.append(src[i]);
    }
    return res.toString();
  }

  private static class RegexpReplacement
  {
    private final Pattern m_pattern;
    private final String m_replacement;

    public RegexpReplacement(String regex, String replacement)
    {
      m_pattern = Pattern.compile(regex);
      m_replacement = replacement;
    }

    public static String applyReplacements(String text, RegexpReplacement replacements[])
    {
      for (RegexpReplacement r : replacements)
        text = r.apply(text);

      return text;
    }

    public String apply(String text)
    {
      StringBuffer result = new StringBuffer();
      Matcher matcher = m_pattern.matcher(text);

      while (matcher.find())
      {
        LOG.debug("replacing '{}'", matcher.group());
        matcher.appendReplacement(result, m_replacement);
      }
      matcher.appendTail(result);

      return result.toString();
    }
  }

  public static String normalize(String text)
  {
    return
        RegexpReplacement.applyReplacements(StringUtils.replaceChars(text, ACCENTED_CHARS, REPLACED_CHARS), STANDARD_ITEM_REPLACEMENTS);
  }

  public final static void main(String args[])
  {
    String text = "En PK 22, to pk-31'ere, en pK33. Én ch     345 ög en APK 15";

    System.out.println(normalize(text));
  }
}
