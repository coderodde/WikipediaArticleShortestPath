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
import java.io.PrintStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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

    private static final String FORWARD_URL_FORMAT = 
            "https://fi.wikipedia.org/w/api.php" +
            "?action=query" +
            "&titles=%s" + 
            "&prop=links" + 
            "&pllimit=max" + 
            "&format=json";

    private static final String BACKWARD_URL_FORMAT = 
            "https://fi.wikipedia.org/w/api.php" +
            "?action=query" +
            "&list=backlinks" +
            "&bltitle=%s" + 
            "&bllimit=max" + 
            "&format=json";

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
    
    
    private static final Pattern WIKIPEDIA_URL_PATTERN = 
            Pattern.compile("^(https://|http://)?..\\.wikipedia.org/wiki/.+$");
            
    private static final String HTTP_PREFIX    = "http://";
    private static final String HTTPS_PREFIX   = "https://"; 
    private static final String API_SCRIPT     = "/w/api.php";
    private static final String WIKI_DIR_TOKEN = "/wiki/";
    
    public List<String> findShortestPathParallel(String sourceTitle,
                                                 String targetTitle,
                                                 PrintStream out) {
        if (sourceTitle.equals(targetTitle)) {
            return new ArrayList<>(Arrays.asList(sourceTitle));
        }
        
        TouchNodeHolder touchNodeHolder = new TouchNodeHolder();
        
        ForwardThread forwardThread = new ForwardThread(sourceTitle,
                                                        touchNodeHolder,
                                                        out);
        
        BackwardThread backwardThread = new BackwardThread(targetTitle,
                                                           touchNodeHolder,
                                                           out);
        touchNodeHolder.setForwardThread(forwardThread);
        touchNodeHolder.setBackwardThread(backwardThread);
        
        forwardThread.start();
        backwardThread.start();
        
        try {
            forwardThread.join();
            backwardThread.join();
        } catch (InterruptedException ex) {
            throw new IllegalStateException(
                    "The forward thread threw " + 
                            ex.getClass().getSimpleName() + ": " + 
                            ex.getMessage(), ex);
        }
        
        return touchNodeHolder.constructPath();
    }
    
    private static class ForwardThread extends Thread {
        
        private final Deque<String> QUEUE = new ArrayDeque<>();
        private final Map<String, String> PARENTS = new HashMap<>();
        private final Map<String, Integer> DISTANCE = new ConcurrentHashMap<>();
        private final PrintStream out;
        private volatile boolean exit;
        private final TouchNodeHolder touchNodeHolder;
        
        ForwardThread(String sourceTitle, 
                      TouchNodeHolder touchNodeHolder,
                      PrintStream out) {
            Objects.requireNonNull(sourceTitle, "The source title is null.");
            
            this.touchNodeHolder = 
                    Objects.requireNonNull(touchNodeHolder, 
                                           "The TouchNodeHolder is null.");
            
            QUEUE.add(sourceTitle);
            PARENTS.put(sourceTitle, null);
            DISTANCE.put(sourceTitle, 0);
            this.out = out;
        }
        
        Map<String, Integer> getDistanceMap() {
            return DISTANCE;
        }
        
        Map<String, String> getParentMap() {
            return PARENTS;
        }
        
        void exitThread() {
            exit = true;
        }
        
        @Override
        public void run() {
            try {
                while (!QUEUE.isEmpty()) {
                    if (exit) {
                        return;
                    }
                    
                    String current = QUEUE.removeFirst();

                    if (out != null) {
                        out.println("Forward:  " + current);
                    }
                    
                    touchNodeHolder.updateFromForwardSearch(current, DISTANCE.get(current));
                    
                    if (touchNodeHolder.pathIsOptimal(current)) {
                        return;
                    }
                    
                    for (String child : getChildArticles(current)) {
                        if (!PARENTS.containsKey(child)) {
                            PARENTS.put(child, current);
                            DISTANCE.put(child, DISTANCE.get(current) + 1);
                            QUEUE.addLast(child);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException(
                        ex.getClass().getSimpleName() + " was thrown in the " +
                        "forward thread: " + ex.getMessage(), ex);
            }
        }
    }
    
    private static final class BackwardThread extends Thread {
        
        private final Deque<String> QUEUE = new ArrayDeque<>();
        private final Map<String, String> PARENTS = new HashMap<>();
        private final Map<String, Integer> DISTANCE = new ConcurrentHashMap<>();
        private final PrintStream out;
        private volatile boolean exit;
        private final TouchNodeHolder touchNodeHolder;
        
        BackwardThread(String targetTitle, 
                       TouchNodeHolder touchNodeHolder,
                       PrintStream out) {
            Objects.requireNonNull(targetTitle, "The target title is null.");
            this.touchNodeHolder = 
                    Objects.requireNonNull(touchNodeHolder, 
                                           "The TouchNodeHolder is null.");
            QUEUE.add(targetTitle);
            PARENTS.put(targetTitle, null);
            DISTANCE.put(targetTitle, 0);
            this.out = out;
        }
        
        Map<String, Integer> getDistanceMap() {
            return DISTANCE;
        }
        
        Map<String, String> getParentMap() {
            return PARENTS;
        }
        
        void exitThread() {
            exit = true;
        }
        
        @Override
        public void run() {
            try {
                while (!QUEUE.isEmpty()) {
                    if (exit) {
                        return;
                    }
                    
                    String current = QUEUE.removeFirst();
                    
                    if (out != null) {
                        out.println("Backward: " + current);
                    }
                    
                    touchNodeHolder.updateFromBackwardThread(current, DISTANCE.get(current));
                    
                    if (touchNodeHolder.pathIsOptimal(current)) {
                        return;
                    }
                    
                    for (String parent : getParentArticles(current)) {
                        if (!PARENTS.containsKey(parent)) {
                            PARENTS.put(parent, current);
                            DISTANCE.put(parent, DISTANCE.get(current) + 1);
                            QUEUE.addLast(parent);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException(
                        ex.getClass().getSimpleName() + " was thrown in the " +
                        "forward thread: " + ex.getMessage(), ex);
            }
        }
    }
    
    private static final class TouchNodeHolder {
        
        private ForwardThread forwardThread;
        private BackwardThread backwardThread;
        private volatile String touchNode;
        private volatile int bestDistanceSoFar = Integer.MAX_VALUE;
        
        void setForwardThread(ForwardThread forwardThread) {
            this.forwardThread = forwardThread;
        }
        
        void setBackwardThread(BackwardThread backwardThread) {
            this.backwardThread = backwardThread;
        }
        
        synchronized boolean pathIsOptimal(String node) {
            if (touchNode == null) {
                return false;
            }
            
            if (!forwardThread.getDistanceMap().containsKey(node)) {
                return false;
            }
            
            if (!backwardThread.getDistanceMap().containsKey(node)) {
                return false;
            }
            
            int distance = forwardThread.getDistanceMap().get(node) + 
                           backwardThread.getDistanceMap().get(node);
            
            if (distance > bestDistanceSoFar) {
                forwardThread .exitThread();
                backwardThread.exitThread();
                return true;
            }
            
            return false;
        }
        
        synchronized void updateFromForwardSearch(String current, int distance) {
            if (backwardThread.getDistanceMap().containsKey(current)) {
                int currentDistance = 
                        forwardThread .getDistanceMap().get(current) +
                        backwardThread.getDistanceMap().get(current);
                
                if (bestDistanceSoFar > currentDistance) {
                    bestDistanceSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized void updateFromBackwardThread(String current, int distance) {
            if (forwardThread.getDistanceMap().containsKey(current)) {
                int currentDistance = 
                        forwardThread .getDistanceMap().get(current) +
                        backwardThread.getDistanceMap().get(current);
                
                if (bestDistanceSoFar > currentDistance) {
                    bestDistanceSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized List<String> constructPath() {
            return tracebackPath(touchNode, 
                                 forwardThread.getParentMap(),
                                 backwardThread.getParentMap());
        }
    }
    
    /**
     * Searches for the shortest path from the Wikipedia article with the title
     * {@code sourceTitle} to the article with the title {@code  targetTitle}.
     * The algorithm is bidirectional breadth-first search.
     * 
     * @param sourceTitle the title of the source article.
     * @param targetTitle the title of the target article.
     * @param out         the output stream to write the progress to.
     * @return the shortest path.
     * @throws IOException may be thrown.
     */
    public List<String> findShortestPath(String sourceTitle, 
                                         String targetTitle,
                                         PrintStream out) 
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

                if (out != null) {
                    out.println("Forward:  " + current);
                }

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

                if (out != null) {
                    out.println("Backward: " + current);
                }

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

    /**
     * Constructs the shortest path.
     * 
     * @param touchNode the article where the two search frontiers "meet" each
     * o                other.
     * @param PARENTSA the parent map of the forward search.
     * @param PARENTSB the parent map of the backward search.
     * @return a shortest path.
     */
    private static List<String> tracebackPath(String touchNode, 
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
    private static List<String> baseGetNeighbors(String currentTitle, 
                                                 boolean forward) 
    throws IOException{
        String jsonDataUrl = 
                String.format(forward ? 
                                    FORWARD_URL_FORMAT : 
                                    BACKWARD_URL_FORMAT,
                              URLEncoder.encode(currentTitle, "UTF-8"));

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
     * Returns all the Wikipedia article titles that the current article links 
     * to.
     * 
     * @param jsonText the data in JSON format.
     * @return a list of Wikipedia article titles parsed from {@code jsonText}.
     */
    private static List<String> extractForwardLinkTitles(String jsonText) {
        List<String> linkNameList = new ArrayList<>();
        JsonArray linkNameArray = null;

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
        JsonArray backLinkArray = null;

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

    public static void main(String[] args) throws IOException {
        List<String> path;
        String from = null;
        String to   = null;
        PrintStream out = System.out;
        
        boolean parallel = false;
        
        if (args.length == 2) {
            from = args[0];
            to   = args[1];
        } else if (args.length == 3) {
            String flag = args[0];
            
            switch (flag) {
                case "--no-output":
                    out = null;
                    break;
                    
                case "--parallel":
                    parallel = true;
                    break;
                    
                default:
                    printUsageMessage();
                    System.exit(1);
            }
            
            from = args[1];
            to   = args[2];
        } else if (args.length == 4) {
            String flag1 = args[0];
            String flag2 = args[1];
            
            switch (flag1) {
                case "--parallel":
                    parallel = true;
                    break;
                    
                case "--no-output":
                    out = null;
                    break;
                    
                default:
                    printUsageMessage();
                    System.exit(1);
            }
            
            switch (flag2) {
                case "--parallel":
                    parallel = true;
                    break;
                    
                case "--no-output":
                    out = null;
                    break;
                    
                default:
                    printUsageMessage();
                    System.exit(1);
            }
            
            from = args[2];
            to   = args[3];
        } else {
            printUsageMessage();
            System.exit(1);
        }
        
        if (!isValidWikipediaURL(from)) {
            System.err.println(
            "[INPUT ERROR] The source URL is not a valid Wikipedia URL: \"" + 
            from + "\".");
            
            System.exit(1);
        }
        
        if (!isValidWikipediaURL(to)) {
            System.err.println(
            "[INPUT ERROR] The target URL is not a valid Wikipedia URL: \"" + 
            to + "\".");
            
            System.exit(1);
        }
        
        from = removeProtocolPrefix(from);
        to   = removeProtocolPrefix(to);
        
        String wikipediaUrlFrom = extractWikipediaMainURL(from);
        String wikipediaUrlTo   = extractWikipediaMainURL(to);
        
        if (!wikipediaUrlFrom.equals(wikipediaUrlTo)) {
            System.err.println("[INPUT ERROR] The source and target articles " +
                               "seem to belong to different languages: \"" +
                               wikipediaUrlFrom + "\" versus \"" + 
                               wikipediaUrlTo + "\".");
            System.exit(1);
        }
        
        String apiUrl = constructWikipediaAPIBaseURL(wikipediaUrlFrom);
        System.out.println("API URL: " + apiUrl);
//        System.exit(0);
        
        to = to.substring(to.lastIndexOf("/") + 1);
        from = from.substring(from.lastIndexOf("/") + 1);
        
        long startTime = System.currentTimeMillis();
        parallel = true;
        
        if (parallel) {
            System.out.println("[STATUS] Doing parallel search");
            path = new PathFinder().findShortestPathParallel(from, to, out);
        } else {
            System.out.println("[STATUS] Doing equential search.");
            path = new PathFinder().findShortestPath(from, to, out);
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println();
        System.out.println("[RESULT] The shortest article path:");
        path.forEach(System.out::println);
        System.out.printf("[RESULT] Duration: %.3f seconds.\n", 
                          (endTime - startTime) / 1000.0);
    }
    
    private static void printUsageMessage() {
        System.out.println(
                "Usage: java -jar FILE.jar [--no-outpu] " + 
                        "[--parallel] SOURCE TARGET");
        System.out.println("Where: --no-output Do not print the progress.");
        System.out.println("       --parallel  Use concurrent path finer.");
        System.out.println("       SOURCE      The source article URL.");
        System.out.println("       TARGET      The target article URL.");
    }
    
    private static String removeProtocolPrefix(String url) {
        if (url.startsWith(HTTPS_PREFIX)) {
            return url.substring(HTTPS_PREFIX.length());
        }
        
        if (url.startsWith(HTTP_PREFIX)) {
            return url.substring(HTTP_PREFIX.length());
        }
        
        return url;
    }
    
    private static String extractWikipediaMainURL(String url) {
        return url.substring(0, url.indexOf(WIKI_DIR_TOKEN));
    }
    
    private static String constructWikipediaAPIBaseURL(String url) {
        return HTTPS_PREFIX + url + API_SCRIPT;
    }
    
    private static boolean isValidWikipediaURL(String url) {
        return WIKIPEDIA_URL_PATTERN.matcher(url).matches();
    }
}
