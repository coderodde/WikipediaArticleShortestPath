package net.coderodde.wikipedia.sp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

/**
 * This class implements an unweighted shortest path finder in the Wikipedia 
 * article finder.
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class PathFinder {
    
    private static final Map<Character, String> ENCODING_MAP = new HashMap<>();
    
    static {
        ENCODING_MAP.put(' ', "_");
        ENCODING_MAP.put('"', "%22");
//        ENCODING_MAP.put('\'', "%27");
//        ENCODING_MAP.put(',', "%2C");
        ENCODING_MAP.put(';', "%3B");
        
        ENCODING_MAP.put('<', "%3C");
        ENCODING_MAP.put('>', "%3E");
        ENCODING_MAP.put('?', "%3F");
        ENCODING_MAP.put('[', "%5B");
        ENCODING_MAP.put(']', "%5D");
        
        ENCODING_MAP.put('{', "%7B");
        ENCODING_MAP.put('|', "%7C");
        ENCODING_MAP.put('}', "%7D");
    }
    
    private static final String PROTOCOL_PREFIX_1 = "https://";
    private static final String PROTOCOL_PREFIX_2 = "http://";
    private static final String EXPECTED_URL_SUBSTRING = "wikipedia.org";
    
    private static final String FORWARD_URL_FORMAT = 
            "https://en.wikipedia.org/w/api.php" +
            "?action=query" +
            "&titles=%s" + 
            "&prop=links" + 
            "&pllimit=max" + 
            "&format=json";
    
    private static final String BACKWARD_URL_FORMAT = 
            "https://en.wikipedia.org/w/api.php" +
            "?action=query" +
            "&list=backlinks" +
            "&bltitle=%s" + 
            "&bllimit=max" + 
            "&format=json";
    
    public List<String> findShortestPath(String sourceTitle, String targetTitle) 
    throws IOException {
        sourceTitle = sourceTitle.trim();
        targetTitle = targetTitle.trim();
        
        if (sourceTitle.equals(targetTitle)) {
            return new ArrayList<>(Arrays.asList(sourceTitle));
        }
        
        Deque<String> QUEUEA = new ArrayDeque<>();
        Deque<String> QUEUEB = new ArrayDeque<>();
        
        Map<String, String> PARENTSA = new HashMap<>();
        Map<String, String> PARENTSB = new HashMap<>();
        
        Map<String, Integer> DISTANCEA = new HashMap<>();
        Map<String, Integer> DISTANCEB = new HashMap<>();
        
        String touchNode = null;
        int bestDistanceSoFar = Integer.MAX_VALUE;
        
        QUEUEA.add(sourceTitle);
        QUEUEB.add(targetTitle);
        
        PARENTSA.put(sourceTitle, null);
        PARENTSB.put(targetTitle, null);
        
        DISTANCEA.put(sourceTitle, 0);
        DISTANCEB.put(targetTitle, 0);
        
        while (!QUEUEA.isEmpty() && !QUEUEB.isEmpty()) {
            if (touchNode != null) {
                int distanceFromSource = DISTANCEA.get(QUEUEA.getFirst());
                int distanceFromTarget = DISTANCEB.get(QUEUEB.getFirst());
                
                if (bestDistanceSoFar < distanceFromSource + 
                                        distanceFromTarget) {
                    return tracebackPath(touchNode, PARENTSA, PARENTSB);
                }
            }
            
            if (DISTANCEA.size() < DISTANCEB.size()) {
                String current = QUEUEA.removeFirst();
                System.out.println("Forward: " + current);
                
                if (PARENTSB.containsKey(current) 
                        && bestDistanceSoFar > DISTANCEA.get(current) +
                                               DISTANCEB.get(current)) {
                    bestDistanceSoFar = DISTANCEA.get(current) +
                                        DISTANCEB.get(current);
                    touchNode = current;
                }
                
                for (String child : getChildArticles(current)) {
                    if (!PARENTSA.containsKey(child)) {
                        PARENTSA.put(child, current);
                        DISTANCEA.put(child, DISTANCEA.get(current) + 1);
                        QUEUEA.addLast(child);
                    }
                }
            } else {
                String current = QUEUEB.removeFirst();
                
                System.out.println("Backward: " + current);
                
                if (PARENTSA.containsKey(current) 
                        && bestDistanceSoFar > DISTANCEA.get(current) + 
                                               DISTANCEB.get(current)) {
                    bestDistanceSoFar = DISTANCEA.get(current) +
                                        DISTANCEB.get(current);
                    touchNode = current;
                }
                
                for (String parent : getParentArticles(current)) {
                    if (!PARENTSB.containsKey(parent)) {
                        PARENTSB.put(parent, current);
                        DISTANCEB.put(parent, DISTANCEB.get(current) + 1);
                        QUEUEB.addLast(parent);
                    }
                }
            }
        }
        
        return new ArrayList<>();
    }
    
    private List<String> tracebackPath(String touchNode, 
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
    
    private static boolean differentLanguages(String url1, String url2) {
        url1 = removeProtocolPrefix(url1);
        url2 = removeProtocolPrefix(url2);
        return !url1.substring(0, 2).equals(url2.substring(0, 2));
    }
    
    private static String removeProtocolPrefix(String url) {
        if (url.startsWith(PROTOCOL_PREFIX_1)) {
            return url.substring(PROTOCOL_PREFIX_1.length());
        } else if (url.startsWith(PROTOCOL_PREFIX_2)) {
            return url.substring(PROTOCOL_PREFIX_2.length());
        }
        
        return url;
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
    private static List<String> baseGetNeighbors(String currentTitle, 
                                                 boolean forward) 
    throws IOException{
        String jsonDataUrl = 
                String.format(forward ? 
                                    FORWARD_URL_FORMAT : 
                                    BACKWARD_URL_FORMAT,
                              URLEncoder.encode(currentTitle, "UTF-8"));
        
        System.out.println("Direction: " + (forward ? "forward" : "backward") +
                           ", URL: " + jsonDataUrl);
        
        String jsonText = 
                IOUtils.toString(new URL(jsonDataUrl), 
                                 Charset.forName("UTF-8"));
        
        return forward ?
               extractForwardLinkTitles(jsonText) : 
               extractBackwardLinkTitles(jsonText);
    }
    
    /**
     * Returns all the child articles that are linked from URL {@code current}.
     * 
     * @param current the URL of the current Wikipedia article.
     * @return the list of URLs that are pointed by {@code current}.
     * @throws IOException may be thrown.
     */
    private static List<String> getChildArticles(String current) 
    throws IOException {
        return baseGetNeighbors(current, true);
    }
    
    /**
     * Returns all the parent articles that are linking to {@code current}.
     * 
     * @param current the URL of the current Wikipedia article.
     * @return the list of URLs that are pointing to {@code current}.
     * @throws IOException may be thrown.
     */
    private static List<String> getParentArticles(String current) 
    throws IOException {
        return baseGetNeighbors(current, false);
    }
    
    /**
     * Returns all the Wikipedia article titles parsed from a JSON text 
     * {@code jsonText}.
     * 
     * @param jsonText the data in JSON format.
     * @return a list of Wikipedia article titles parsed from {@code jsonText}.
     */
    private static List<String> extractForwardLinkTitles(String jsonText) {
        List<String> linkNameList = new ArrayList<>();

        JsonObject root = new JsonParser().parse(jsonText).getAsJsonObject();
        JsonObject queryObject = root.get("query").getAsJsonObject();
        JsonObject pagesObject = queryObject.get("pages").getAsJsonObject();
        JsonObject mainObject  = pagesObject.entrySet()
                                            .iterator()
                                            .next()
                                            .getValue()
                                            .getAsJsonObject();
        
        JsonArray linkNameArray = mainObject.get("links")
                                            .getAsJsonArray();
        
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
    
    private static List<String> extractBackwardLinkTitles(String jsonText) {
        List<String> linkNameList = new ArrayList<>();
        
        JsonObject root = new JsonParser().parse(jsonText).getAsJsonObject();
        JsonObject queryObject = root.get("query").getAsJsonObject();
        JsonArray backLinkArray = queryObject.get("backlinks").getAsJsonArray();
        
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
