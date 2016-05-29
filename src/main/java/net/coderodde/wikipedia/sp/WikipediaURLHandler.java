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

    private static final Pattern WIKIPEDIA_URL_PATTERN = 
            Pattern.compile("^(https://|http://)?..\\.wikipedia.org/wiki/.+$");
    
    private static final String HTTPS_PROTOCOL_PREFIX = "https://";
    private static final String HTTP_PROTOCOL_PREFIX  = "http://";    
    private static final String WIKI_DIR_TOKEN        = "/wiki/";
    private static final String API_SCRITP_DIR_TOKEN  = "/w/api.php";
    
    /**
     * Caches the language identifier such as "en", "fi", "de", and so on.
     */
    private final String languageIdentifier;
    
    /**
     * Caches the textual representation of the URL pointing to the 
     * <a href="https://www.mediawiki.org/wiki/API:Main_page">Wikipedia API</a>.
     */
    private final String apiUrl;
    
    /**
     * Parses {@code wikipediaUrl} and constructs this class.
     * 
     * @param wikipediaUrl the Wikipedia URL to parse.
     */
    public WikipediaURLHandler(String wikipediaUrl) {
        if (!WIKIPEDIA_URL_PATTERN.matcher(wikipediaUrl).matches()) {
            throw new IllegalArgumentException(
                    "[INPUT ERROR] The input URL is not a valid Wikipedia article URL: \"" + wikipediaUrl + "\".");
        }
        
        wikipediaUrl = removeProtocolPrefix(wikipediaUrl);
        
        this.apiUrl = constructAPIURL(wikipediaUrl);
        this.languageIdentifier = extractLanguageIdentifier(wikipediaUrl);
    }
    
    /**
     * Returns the language identifier string (such as "fi", "en", "de", and so 
     * on) specified in this handler.
     * 
     * @return the language identifier string.
     */
    public String getLanguageIdentifier() {
        return this.languageIdentifier;
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
    
    private String removeProtocolPrefix(String url) {
        if (url.startsWith(HTTPS_PROTOCOL_PREFIX)) {
            return url.substring(HTTPS_PROTOCOL_PREFIX.length());
        }
        
        if (url.startsWith(HTTP_PROTOCOL_PREFIX)) {
            return url.substring(HTTP_PROTOCOL_PREFIX.length());
        }
        
        return url;
    }
    
    /**
     * Returns the language identifier, such as "en", "de", "fi", and so on, 
     * extracted from {@code wikipediaUrl}. Expects that the input URL is not
     * prepended with the protocol identifier ("<tt>https://</tt>" or 
     * "<tt>http://</tt>").
     * 
     * @param wikipediaUrl the URL.
     * @return the language identifier.
     */
    private String extractLanguageIdentifier(String wikipediaUrl) {
        return wikipediaUrl.substring(0, 2);
    }
    
    private String constructAPIURL(String wikipediaUrl) {
        return HTTPS_PROTOCOL_PREFIX + 
               wikipediaUrl.substring(0, wikipediaUrl.indexOf(WIKI_DIR_TOKEN)) + 
               API_SCRITP_DIR_TOKEN;
    }
}
