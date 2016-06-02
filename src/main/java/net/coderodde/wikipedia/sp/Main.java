package net.coderodde.wikipedia.sp;

import java.util.List;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * This class implements an unweighted shortest path finder in the Wikipedia 
 * article finder.
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class Main {


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
//            path = new Main().findShortestPathParallel(from, to, out);
        } else {
            System.out.println("[STATUS] Doing equential search.");
//            path = new Main().findShortestPath(from, to, out);
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println();
        System.out.println("[RESULT] The shortest article path:");
//        path.forEach(System.out::println);
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
