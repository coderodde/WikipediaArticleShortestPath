package net.coderodde.wikipedia.sp;

import java.util.List;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Pattern;
import net.coderodde.wikipedia.sp.support.BidirectionalWikipediaShortestPathFinder;
import net.coderodde.wikipedia.sp.support.ParallelBidirectionalWikipediaShortestPathFinder;

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
        if (args.length < 2) {
            printUsageMessage();
            return;
        }
        
        String fromUrl;
        String toUrl;
        boolean noOutput = false;
        boolean serial   = false;
        
        switch (args.length) {
            case 2:
                fromUrl = args[0];
                toUrl   = args[1];
                break;
                
            case 3:
                switch (args[0]) {
                    case "--no-output":
                        noOutput = true;
                        break;
                        
                    case "--serial":
                        serial = true;
                        break;
                        
                    default:
                        printUsageMessage();
                        return;
                }
                
                fromUrl = args[1];
                toUrl   = args[2];
                break;
                
            case 4:
                switch (args[0]) {
                    case "--no-output":
                        noOutput = true;
                        break;
                        
                    case "--serial":
                        serial = true;
                        break;
                        
                    default:
                        printUsageMessage();
                        return;
                }
                
                switch (args[1]) {
                    case "--no-output":
                        noOutput = true;
                        break;
                        
                    case "--serial":
                        serial = true;
                        break;
                        
                    default:
                        printUsageMessage();
                        return;
                }
                
                fromUrl = args[2];
                toUrl   = args[3];
                break;
                
            default:
                printUsageMessage();
                return;
        }
        
        WikipediaURLHandler fromUrlHandler;
        WikipediaURLHandler toUrlHandler;
        
        try {
            fromUrlHandler = new WikipediaURLHandler(toUrl);
            toUrlHandler   = new WikipediaURLHandler(fromUrl);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return;
        }            
        
        if (!fromUrlHandler.getBasicURL().equals(toUrlHandler.getBasicURL())) {
            System.out.println("[ERROR] The source and target articles seem " +
                               "to be written in different languages.");
            return;
        }
        
        PrintStream out = noOutput ? null : System.out;
        AbstractWikipediaShortestPathFinder finder = serial ? 
                new BidirectionalWikipediaShortestPathFinder() :
                new ParallelBidirectionalWikipediaShortestPathFinder();
        
        List<String> path = finder.search(fromUrlHandler.getTitle(),
                                          toUrlHandler.getTitle(), 
                                          fromUrlHandler.getAPIURL(), 
                                          out);
        
        path.forEach(System.out::println);
    }
    
    private static void printUsageMessage() {
        System.out.println(
                "Usage: java -jar FILE.jar [--no_output] " + 
                        "[--serial] SOURCE TARGET");
        System.out.println("Where: --no-output Do not print the progress.");
        System.out.println("       --serial    Use non-parallel path finer.");
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
