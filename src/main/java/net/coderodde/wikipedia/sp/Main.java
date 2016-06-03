package net.coderodde.wikipedia.sp;

import java.util.List;
import java.io.IOException;
import java.io.PrintStream;
import net.coderodde.wikipedia.sp.support.BidirectionalWikipediaShortestPathFinder;
import net.coderodde.wikipedia.sp.support.ParallelBidirectionalWikipediaShortestPathFinder;

/**
 * This class implements an unweighted shortest path finder in the Wikipedia 
 * article finder.
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class Main {

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
            fromUrlHandler = new WikipediaURLHandler(fromUrl);
            toUrlHandler   = new WikipediaURLHandler(toUrl);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return;
        }            
        
        if (!fromUrlHandler.getBasicURL().equals(toUrlHandler.getBasicURL())) {
            System.out.println("[ERROR] The source and target articles seem " +
                               "to be written in different languages.");
            return;
        }
        
        System.out.println("[STATUS] Searching from \"" + fromUrl + "\" to \"" +
                           toUrl + "\" using " +
                           (serial ? "serial " : "parallel ") + "algorithm.");
        
        PrintStream out = noOutput ? null : System.out;
        AbstractWikipediaShortestPathFinder finder = serial ? 
                new BidirectionalWikipediaShortestPathFinder() :
                new ParallelBidirectionalWikipediaShortestPathFinder();
        
        String sourceTitle = fromUrlHandler.getTitle();
        String targetTitle = toUrlHandler.getTitle();
        
        List<String> path = finder.search(sourceTitle,
                                          targetTitle, 
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
}
