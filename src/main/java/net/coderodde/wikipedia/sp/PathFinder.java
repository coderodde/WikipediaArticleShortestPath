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

/**
 * This class implements an unweighted shortest path finder in the Wikipedia 
 * article finder.
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class PathFinder {
    
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
    
    public List<String> findShortestPath(String sourceUrl, String targetUrl) 
    throws IOException {
        sourceUrl = sourceUrl.toLowerCase().trim();
        targetUrl = targetUrl.toLowerCase().trim();
        
        if (!sourceUrl.contains(EXPECTED_URL_SUBSTRING)) {
            throw new IllegalArgumentException(
                    "Invalid source URL to Wikipedia: \"" + sourceUrl + "\".");
        }
        
        if (!targetUrl.contains(EXPECTED_URL_SUBSTRING)) {
            throw new IllegalArgumentException(
                    "Invalid target URL to Wikipedia: \"" + targetUrl + "\".");
        }
        
        if (differentLanguages(sourceUrl, targetUrl)) {
            throw new IllegalArgumentException(
                    "The Wikipedia URLs (" + sourceUrl + ", " + targetUrl + 
                    ") seem to point to the articles of different languages.");
        }
        
        return computeShortestPath(sourceUrl, targetUrl);
    }
    
    private List<String> computeShortestPath(String source, String target) 
    throws IOException {
        if (source.equals(target)) {
            return new ArrayList<>(Arrays.asList(source));
        }
        
        Deque<String> QUEUEA = new ArrayDeque<>();
        Deque<String> QUEUEB = new ArrayDeque<>();
        
        Map<String, String> PARENTSA = new HashMap<>();
        Map<String, String> PARENTSB = new HashMap<>();
        
        Map<String, Integer> DISTANCEA = new HashMap<>();
        Map<String, Integer> DISTANCEB = new HashMap<>();
        
        String touchNode = null;
        int bestDistanceSoFar = Integer.MAX_VALUE;
        
        QUEUEA.add(source);
        QUEUEB.add(target);
        
        PARENTSA.put(source, null);
        PARENTSB.put(target, null);
        
        DISTANCEA.put(source, 0);
        DISTANCEB.put(target, 0);
        
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
                
                if (PARENTSB.containsKey(current) 
                        && bestDistanceSoFar > DISTANCEA.get(current) +
                                               DISTANCEB.get(current)) {
                    bestDistanceSoFar = DISTANCEA.get(current) +
                                        DISTANCEB.get(current);
                    touchNode = current;
                }
                
                for (String child : getChildArticles(current)) {
                    
                }
            } else {
                String current = QUEUEB.removeFirst();
            }
        }
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
        return url1.substring(0, 2).equals(url2.substring(0, 2));
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
    private static List<String> baseGetNeighbors(String current, 
                                                 boolean forward) 
    throws IOException{
        int lastIndexOfSlash = current.lastIndexOf('/');
        String currentTitle = current.substring(lastIndexOfSlash + 1);
        String jsonText = 
                String.format(forward ? 
                                    FORWARD_URL_FORMAT : 
                                    BACKWARD_URL_FORMAT, 
                              currentTitle);
        return extractLinkTitles(jsonText, forward);
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
    
    private static List<String> extractLinkTitles(String jsonText, 
                                                  boolean forward) {
        List<String> linkNameList = new ArrayList<>();
        JsonObject root  = new JsonParser().parse(jsonText).getAsJsonObject();
        
        System.out.println(root.has("query"));
        
        JsonObject queryObject  = root.get("query").getAsJsonObject();
        JsonObject pagesObject  = queryObject.get("pages").getAsJsonObject();
        JsonObject mainObject   = pagesObject.entrySet()
                                             .iterator()
                                             .next()
                                             .getValue()
                                             .getAsJsonObject();
        JsonArray linkNameArray = mainObject.get("links").getAsJsonArray();
        
//        System.out.println("Yeah");
//        System.out.println(linkNameArray);
//        
        linkNameArray.forEach((element) -> {
            int namespace = element.getAsJsonObject().get("ns").getAsInt();
            
            if (namespace == 0) {
                String title = element.getAsJsonObject()
                                      .get("title")
                                      .getAsString();
                
                linkNameList.add(forward ? );
            }
        });
        
        return linkNameList;
    }
}
