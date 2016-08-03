package net.coderodde.wikipedia.sp;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static net.coderodde.wikipedia.sp.Miscellanea.nth;
import static net.coderodde.wikipedia.sp.Miscellanea.parseInt;
import static net.coderodde.wikipedia.sp.Miscellanea.removeLast;

/**
 * This class parses the command line arguments.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jul 26, 2016)
 */
class CommandLineArgumentParser {
   
    /**
     * This switch specifies that the progress should be printed in a stream.
     */
    static final String LOG_SWITCH_SHORT = "-l";
    
    /**
     * This switch specifies that the progress should be printed in a stream.
     */
    static final String LOG_SWITCH_LONG  = "--log";
    
    /**
     * This switch (and its parameter) determine the number of threads to use in
     * the search.
     */
    static final String THREAD_SWITCH_SHORT = "-t";
    
    /**
     * This switch (and its parameter) determine the number of threads to use in
     * the search.
     */
    static final String THREAD_SWITCH_LONG = "--threads";
    
    /**
     * This switch (and its parameter) determine the number of trials to pop the
     * queue before exiting.
     */
    static final String TRIAL_SWITCH_LONG = "--trials";
    
    /**
     * This switch (and its parameter) determine the number of milliseconds for
     * sleeping after each unsuccessful trials to pop the queue.
     */
    static final String WAIT_TIME_SWITCH_SHORT = "-w";
    
    /**
     * This switch (and its parameter) determine the number of milliseconds for
     * sleeping after each unsuccessful trials to pop the queue.
     */
    static final String WAIT_TIME_SWITCH_LONG = "--wait";
    
    /**
     * The default number of trials to pop the queue.
     */
    private static final int DEFAULT_TRIALS = 100;
    
    /**
     * The default number of milliseconds to wait for the next trial.
     */
    private static final int DEFAULT_WAIT_TIME = 10;
    
    CommandLineArguments parse(final String[] args) {
        if (args.length < 2) {
            throw new InvalidCommandLineOptionsException(
                    "The command is too short, containing only " + args.length +
                    nth(args.length) + " token, when at least two are " +
                    "required.");
        }
        
        final List<String> argumentList = new ArrayList<>(Arrays.asList(args));
        
        final String targetUrl = removeLast(argumentList);
        final String sourceUrl = removeLast(argumentList);
        
        boolean log = false;
        int threadCount = 1;
        int argumentIndex = 0;
        int dequeueTrials = DEFAULT_TRIALS;
        int trialWaitTime = DEFAULT_WAIT_TIME;
        
        while (argumentIndex < argumentList.size()) {
            final String currentArgument = argumentList.get(argumentIndex++);
            
            switch (currentArgument) {
                case LOG_SWITCH_SHORT:
                case LOG_SWITCH_LONG:
                    
                    log = true;
                    break;
                    
                case THREAD_SWITCH_SHORT:
                case THREAD_SWITCH_LONG:
                    
                    if (argumentIndex == argumentList.size()) {
                        throw new InvalidCommandLineOptionsException(
                                "The thread count argument at index " +
                                (argumentIndex - 1) + " does not precede an " +
                                "integer.");
                    } else {
                        threadCount = parseInt(argumentList.get(argumentIndex));
                    }
                
                    break;
                    
                case TRIAL_SWITCH_LONG:
                    
                    if (argumentIndex == argumentList.size()) {
                        throw new InvalidCommandLineOptionsException(
                                "The number of trials argument at index " +
                                (argumentIndex - 1) + " does not precede an " +
                                "integer.");
                    } else {
                        dequeueTrials = 
                                parseInt(argumentList.get(argumentIndex));
                    }
                
                    break;
                    
                case WAIT_TIME_SWITCH_SHORT:
                case WAIT_TIME_SWITCH_LONG:
                    
                    if (argumentIndex == argumentList.size()) {
                        throw new InvalidCommandLineOptionsException(
                                "The wait time argument at index " +
                                (argumentIndex - 1) + " does not precede an " +
                                "integer.");
                    } else {
                        trialWaitTime = 
                                parseInt(argumentList.get(argumentIndex));
                    }
                
                    break;
            }
        }
        
        return new CommandLineArguments(log, 
                                        threadCount, 
                                        sourceUrl, 
                                        targetUrl,
                                        dequeueTrials,
                                        trialWaitTime);
    }
}
