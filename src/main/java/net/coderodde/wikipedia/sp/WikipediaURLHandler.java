package net.coderodde.wikipedia.sp;

import java.util.regex.Pattern;

/**
 * This class is responsible for parsing the input Wikipedia URL and extracting
 * API URL from it and the language identifier.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 29, 2016)
 */
public class WikipediaURLHandler {

    public static final Pattern WIKIPEDIA_URL_PATTERN = 
            Pattern.compile("^(https://|http://)?..+\\.wikipedia.org/wiki/.+$");

    public static final String HTTPS_PROTOCOL_PREFIX = "https://";
    public static final String HTTP_PROTOCOL_PREFIX  = "http://";    
    public static final String WIKI_DIR_TOKEN        = "/wiki/";
    public static final String API_SCRIPT_DIR_TOKEN  = "/w/api.php";

    /**
     * Caches the basic Wikipedia article URL. For example, the basic URL of
     * <tt>https://en.wikipedia.org/wiki/Disc_jockey</tt> is
     * <tt>en.wikipedia.org</tt>.
     */
    private final String basicUrl;

    /**
     * Caches the textual representation of the URL pointing to the 
     * <a href="https://www.mediawiki.org/wiki/API:Main_page">Wikipedia API</a>.
     */
    private final String apiUrl;

    /**
     * Caches the title of the article. For example, for the Wikipedia article
     * <tt>https://en.wikipedia.org/wiki/Disc_jockey</tt>, the title is
     * <tt>Disc_jockey</tt>.
     */
    private final String title;

    /**
     * Parses {@code wikipediaUrl} and constructs this class.
     * 
     * @param wikipediaUrl the Wikipedia URL to parse.
     */
    public WikipediaURLHandler(String wikipediaUrl) {
        if (!WIKIPEDIA_URL_PATTERN.matcher(wikipediaUrl).matches()) {
            throw new IllegalArgumentException(
                    "[INPUT ERROR] The input URL is not a valid Wikipedia " + 
                    "article URL: \"" + wikipediaUrl + "\".");
        }

        wikipediaUrl  = removeProtocolPrefix(wikipediaUrl);
        this.apiUrl   = constructAPIURL(wikipediaUrl);
        this.basicUrl = 
                wikipediaUrl.substring(0, wikipediaUrl.indexOf(WIKI_DIR_TOKEN));
        this.title    = 
                wikipediaUrl.substring(wikipediaUrl
                                       .indexOf(WIKI_DIR_TOKEN) + 
                                       WIKI_DIR_TOKEN.length());
    }

    /**
     * Returns the textual representation of the URL to the Wikipedia API of 
     * particular language.
     * 
     * @return the textual representation of the Wikipedia API URL.
     */
    public String getAPIURL() {
        return this.apiUrl;
    }

    public String getBasicURL() {
        return this.basicUrl;
    }

    public String getTitle() {
        return this.title;
    }

    private String removeProtocolPrefix(String url) {
        if (url.startsWith(HTTPS_PROTOCOL_PREFIX)) {
            return url.substring(HTTPS_PROTOCOL_PREFIX.length());
        }

        if (url.startsWith(HTTP_PROTOCOL_PREFIX)) {
            return url.substring(HTTP_PROTOCOL_PREFIX.length());
        }

        return url;
    }

    private String constructAPIURL(String wikipediaUrl) {
        return HTTPS_PROTOCOL_PREFIX + 
               wikipediaUrl.substring(0, wikipediaUrl.indexOf(WIKI_DIR_TOKEN)) + 
               API_SCRIPT_DIR_TOKEN;
    }
}
