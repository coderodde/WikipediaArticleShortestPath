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

    private static final class Arguments {
        
        static final int DEFAULT_THREAD_COUNT = 1;
        
        private final boolean log;
        private final int threadCount;
        private final String sourceTitle;
        private final String targetTitle;
        
        Arguments(final boolean log, 
                  final int threadCount,
                  final String sourceTitle,
                  final String targetTitle) {
            this.log = log;
            this.threadCount = threadCount;
            this.sourceTitle = sourceTitle;
            this.targetTitle = targetTitle;
        }
        
        boolean doLog() {
            return log;
        }
        
        int getThreadCount() {
            return threadCount;
        }
        
        String getSourceTitle() {
            return sourceTitle;
        }
        
        String getTargetTitle() {
            return targetTitle;
        }
    }
    
    private static Arguments parse2Arguments(final String[] args) {
        return new Arguments(false, 1, args[0], args[1]);
    }
    
    private static Arguments parse3Arguments(final String[] args) {
        boolean log = false;
        
        switch (args[0]) {
            case "--thread":
                
                throw new InvalidCommandLineOptions(
                        "The switch \"--tread\" must have an integer argument: " +
                        "\"--threads N\"");
                
            case "--output":
                
                log = true;
                break;
                
            default:
                
                throw new InvalidCommandLineOptions(
                        "Unknown switch \"" + args[0] + "\".");
        }
        
        return new Arguments(log, 1, args[1], args[2]);
    }
    
    private static Arguments parse4Arguments(final String[] args) {
        switch (args[0]) {
            
            case "--output":
                
                throw new InvalidCommandLineOptions(
                        "Bad command-line format: second argument invalid.");
                
            case "--threads":
                
                return new Arguments(false, 
                                     parseInt(args[1]), 
                                     args[2], 
                                     args[3]);
                
            default:
                
                throw new InvalidCommandLineOptions(
                        "Unknown switch \"" + args[0] + "\".");
        }
    }
    
    private static Arguments parse5Arguments(final String[] args) {
        
    }
    
    private static Arguments parseCommandLine(final String[] args) {
        switch (args.length) {
            case 0:
            case 1:
                
                throw new InvalidCommandLineOptions(
                        "At least two command-line arguments are required (" +
                        "the source article and the target article).");
                
            case 2:
                
                return parse2Arguments(args);
                
            case 3:
                
                return parse3Arguments(args);
                
            case 4:
                
                return parse4Arguments(args);
                
            case 5:
                
                return parse5Arguments(args);
                
            default:
                
                throw new InvalidCommandLineOptions("Bad command-line format.");
        }
    }
    
    private static int parseInt(final String numberString) {
        try {
            return Integer.parseInt(numberString);
        } catch (final NumberFormatException ex) {
            throw new InvalidCommandLineOptions(
                    "Could not parse \"" + numberString + "\" as an integer.");
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            printUsageMessage();
            return;
        }

        String fromUrl;
        String toUrl;
        boolean noOutput = false;
        int threadCount = 1;

        switch (args.length) {
            case 2:
        }
        
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

                    case "--threads":
                        
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

        System.out.println("[RESULT] The search took " + finder.getDuration() +
                           " milliseconds, expanding " + 
                           finder.getNumberOfExpandedNodes() + 
                           " nodes.");

        path.forEach(System.out::println);
    }

    private static void printUsageMessage() {
        System.out.println(
                "Usage: java -jar FILE.jar [--no_output] " + 
                        "[--threads N] SOURCE TARGET");
        System.out.println("Where: --no-output Do not print the progress.");
        System.out.println("       --threads N Use N threads for searching.");
        System.out.println("       SOURCE      The source article URL.");
        System.out.println("       TARGET      The target article URL.");
    }
}
