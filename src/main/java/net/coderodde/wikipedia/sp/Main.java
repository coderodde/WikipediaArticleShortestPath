package net.coderodde.wikipedia.sp;

import java.util.List;
import java.io.PrintStream;
import static net.coderodde.wikipedia.sp.Miscellanea.nth;
import net.coderodde.wikipedia.sp.support.BidirectionalWikipediaShortestPathFinder;
import net.coderodde.wikipedia.sp.support.ParallelBidirectionalWikipediaShortestPathFinder;
import net.coderodde.wikipedia.sp.support.ParallelMultidirectionalWikipediaShortestPathFinder;

/**
 * This class implements an unweighted shortest path finder in the Wikipedia 
 * article finder.
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class Main {

    public static void main(String[] args) {
        CommandLineArgumentParser parser =
                new CommandLineArgumentParser();
        
        CommandLineArguments arguments = null;
        
        try {
            arguments = parser.parse(args);
        } catch (final InvalidCommandLineOptionsException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            printUsageMessage();
            System.exit(1);
        }
        
        System.out.println("[CONFIGURATION] Logging progress: " + 
                arguments.doLog());
        
        System.out.println("[CONFIGURATION] Thread count:     " + 
                arguments.getThreadCount());
        
        System.out.println("[CONDIGURATION] Source URL:       " + 
                arguments.getSourceUrl());
        
        System.out.println("[CONFIGURATION] Target URL:       " +
                arguments.getTargetUrl());
        
        System.out.println("[CONFIGURATION] Dequeue trials:   " + 
                arguments.getDequeueTrials());
        
        System.out.println("[CONFIGURATION] Trial wait time:  " +
                arguments.getTrialWaitTime());

        WikipediaURLHandler fromUrlHandler = null;
        WikipediaURLHandler toUrlHandler   = null;

        try {
            fromUrlHandler = new WikipediaURLHandler(arguments.getSourceUrl());
            toUrlHandler   = new WikipediaURLHandler(arguments.getTargetUrl());
        } catch (IllegalArgumentException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            printUsageMessage();
            System.exit(1);
        }            

        if (!fromUrlHandler.getBasicURL().equals(toUrlHandler.getBasicURL())) {
            System.out.println("ERROR: The source and target articles seem " +
                               "to be written in different languages.");
            System.exit(1);
        }

        System.out.println(
                "[STATUS] Searching from \"" + arguments.getSourceUrl() + 
                "\" to \"" + arguments.getTargetUrl() + "\" using " +
                arguments.getThreadCount() + " thread" + nth(arguments.getThreadCount()) + ".");

        PrintStream out = arguments.doLog() ? System.out : null;
        AbstractWikipediaShortestPathFinder finder; 
        
        final int numberOfThreads = arguments.getThreadCount();
        
        if (numberOfThreads < 2) {
            finder = new BidirectionalWikipediaShortestPathFinder();
        } else if (numberOfThreads == 2) {
            finder = new ParallelBidirectionalWikipediaShortestPathFinder();
        } else {
            finder = new 
            ParallelMultidirectionalWikipediaShortestPathFinder(
                    arguments.getThreadCount() / 2,
                    arguments.getDequeueTrials(),
                    arguments.getTrialWaitTime());
        }
        
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
                "Usage: java -jar FILE.jar [" +
                        CommandLineArgumentParser.LOG_SWITCH_SHORT + " | " +
                        CommandLineArgumentParser.LOG_SWITCH_LONG + "] " +
                        "[" + CommandLineArgumentParser.THREAD_SWITCH_SHORT + 
                        " | " + CommandLineArgumentParser.THREAD_SWITCH_LONG + 
                        "] [" + CommandLineArgumentParser.TRIAL_SWITCH_LONG + 
                        "] [" + 
                        CommandLineArgumentParser.WAIT_TIME_SWITCH_SHORT + 
                        " | " + 
                        CommandLineArgumentParser.WAIT_TIME_SWITCH_LONG +
                        "] SOURCE_URL TARGET_URL");
        
        System.out.println(
                "Where:");
        System.out.println("    " +
                CommandLineArgumentParser.LOG_SWITCH_SHORT + ", " + 
                CommandLineArgumentParser.LOG_SWITCH_LONG +
                "  Request progress logging.");
        
        System.out.println("    " +
                CommandLineArgumentParser.THREAD_SWITCH_SHORT + " N, " + 
                CommandLineArgumentParser.THREAD_SWITCH_LONG + " N  " +
                "Request N threads for the search.");
        
        System.out.println("    " +
                CommandLineArgumentParser.TRIAL_SWITCH_LONG + " N  " +
                "Request N trials for popping the queue.");
        
        System.out.println("    " +
                CommandLineArgumentParser.WAIT_TIME_SWITCH_SHORT + " N, " +
                CommandLineArgumentParser.WAIT_TIME_SWITCH_LONG + "N" +
                "  Request the trial wait time of N milliseconds.");
        
        System.out.println("    SOURCE_URL the URL of the source article.");
        System.out.println("    TARGET_URL the URL of the target article.");
    }
}
