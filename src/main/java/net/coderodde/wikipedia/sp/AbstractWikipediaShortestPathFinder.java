package net.coderodde.wikipedia.sp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;

/**
 * This interface defines the API for the shortest path algorithms working on 
 * the Wikipedia article graph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 29, 2016)
 */
public abstract class AbstractWikipediaShortestPathFinder {
    
    private static final Map<Character, String> ENCODING_MAP = new HashMap<>();

    private static final String BACKWARD_REQUEST_URL = 
            "?action=query" +
            "&list=backlinks" +
            "&bltitle=%s" + 
            "&bllimit=max" + 
            "&format=json";
            
    private static final String FORWARD_REQUEST_URL = 
            "?action=query" +
            "&titles=%s" + 
            "&prop=links" + 
            "&pllimit=max" + 
            "&format=json"; 
    
    static {
        ENCODING_MAP.put(' ', "_");
        ENCODING_MAP.put('"', "%22");
        ENCODING_MAP.put(';', "%3B");
        ENCODING_MAP.put('<', "%3C");
        ENCODING_MAP.put('>', "%3E");

        ENCODING_MAP.put('?', "%3F");
        ENCODING_MAP.put('[', "%5B");
        ENCODING_MAP.put(']', "%5D");
        ENCODING_MAP.put('{', "%7B");
        ENCODING_MAP.put('|', "%7C");

        ENCODING_MAP.put('}', "%7D");
        ENCODING_MAP.put('?', "%3F");
    }
    
    public abstract List<String> search(String sourceTitle, 
                                        String targetTitle,
                                        String apiUrlText,
                                        PrintStream out);
        
    /**
     * Constructs the shortest path.
     * 
     * @param touchNode the article where the two search frontiers "meet" each
     * o                other.
     * @param PARENTSA the parent map of the forward search.
     * @param PARENTSB the parent map of the backward search.
     * @return a shortest path.
     */
    protected static List<String> tracebackPath(String touchNode, 
                                                Map<String, String> PARENTSA, 
                                                Map<String, String> PARENTSB) {
        List<String> path = new ArrayList<>();
        String node = touchNode;

        while (node != null) {
            path.add(node);
            node = PARENTSA.get(node);
        }

        Collections.reverse(path);
        node = PARENTSB.get(touchNode);

        while (node != null) {
            path.add(node);
            node = PARENTSB.get(node);
        }

        return path;
    }
    
    /**
     * Implements the neighbor function. 
     * 
     * @param current the current URL.
     * @param forward if is set to {@code true}, this method return all the 
     *                child URLs of {@code current}.
     * @return the list of child URLs.
     * @throws IOException may be thrown.
     */
    private static List<String> baseGetNeighbors(String apiUrl,
                                                 String currentTitle, 
                                                 boolean forward) {
        String jsonDataUrl;
        
        try {
            jsonDataUrl = 
                    apiUrl + String.format(forward ? 
                                                FORWARD_REQUEST_URL : 
                                                BACKWARD_REQUEST_URL, 
                                           URLEncoder.encode(currentTitle, 
                                                             "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        String jsonText;
        
        try {
            jsonText = IOUtils.toString(new URL(jsonDataUrl), 
                                        Charset.forName("UTF-8"));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "[I/O ERROR] Failed loading the JSON data from the " +
                    "Wikipedia API: " + ex.getMessage(), ex);
        }

            return forward ?
                   extractForwardLinkTitles(jsonText) : 
                   extractBackwardLinkTitles(jsonText);
    }

    /**
     * Returns all the child articles that are linked from URL {@code current}.
     * 
     * @param apiUrl  the URL to the Wikipedia API.
     * @param current the URL of the current Wikipedia article.
     * @return the list of URLs that are pointed by {@code current}.
     */
    protected static List<String> getChildArticles(String apiUrl,
                                                   String current) {
        return baseGetNeighbors(apiUrl, current, true);
    }

    /**
     * Returns all the parent articles that are linking to {@code current}.
     * 
     * @param apiUrl  the URL to the Wikipedia API.
     * @param current the URL of the current Wikipedia article.
     * @return the list of URLs that are pointing to {@code current}.
     */
    protected static List<String> getParentArticles(String apiUrl,
                                                    String current) {
        return baseGetNeighbors(apiUrl, current, false);
    }
    
    
    /**
     * Returns all the Wikipedia article titles that the current article links 
     * to.
     * 
     * @param jsonText the data in JSON format.
     * @return a list of Wikipedia article titles parsed from {@code jsonText}.
     */
    private static List<String> extractForwardLinkTitles(String jsonText) {
        List<String> linkNameList = new ArrayList<>();
        JsonArray linkNameArray;

        try {
            JsonObject root = new JsonParser().parse(jsonText).getAsJsonObject();
            JsonObject queryObject = root.get("query").getAsJsonObject();
            JsonObject pagesObject = queryObject.get("pages").getAsJsonObject();
            JsonObject mainObject  = pagesObject.entrySet()
                                                .iterator()
                                                .next()
                                                .getValue()
                                                .getAsJsonObject();

            linkNameArray = mainObject.get("links").getAsJsonArray();
        } catch (NullPointerException ex) {
            return linkNameList;
        }

        linkNameArray.forEach((element) -> {
            int namespace = element.getAsJsonObject().get("ns").getAsInt();

            if (namespace == 0) {
                String title = element.getAsJsonObject()
                                      .get("title")
                                      .getAsString();

                linkNameList.add(encodeWikipediaStyle(title));
            }
        });

        return linkNameList;
    }

    /**
     * Returns all the Wikipedia article titles that link to the current
     * article.
     * 
     * @param jsonText the data in JSON format.
     * @return a list of Wikipedia article titles parsed from {@code jsonText}.
     */
    private static List<String> extractBackwardLinkTitles(String jsonText) {
        List<String> linkNameList = new ArrayList<>();
        JsonArray backLinkArray;

        try {
            JsonObject root = new JsonParser().parse(jsonText).getAsJsonObject();
            JsonObject queryObject = root.get("query").getAsJsonObject();
            backLinkArray = queryObject.get("backlinks").getAsJsonArray();
        } catch (NullPointerException ex) {
            return linkNameList;
        }

        backLinkArray.forEach((element) -> {
            int namespace = element.getAsJsonObject()
                                   .get("ns")
                                   .getAsInt();

            if (namespace == 0) {
                String title = element.getAsJsonObject()
                                      .get("title")
                                      .getAsString();

                linkNameList.add(encodeWikipediaStyle(title));
            }
        });

        return linkNameList;
    }

    private static String encodeWikipediaStyle(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            String encoder = ENCODING_MAP.get(c);

            if (encoder != null) {
                sb.append(encoder);
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }
}
