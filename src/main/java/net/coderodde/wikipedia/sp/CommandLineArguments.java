package net.coderodde.wikipedia.sp;

/**
 * This class holds all the command line arguments for the program.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jul 26, 2016)
 */
class CommandLineArguments {
   
    /**
     * Specifies whether the progress should be logged in a stream.
     */
    private final boolean log;
    
    /**
     * The number of threads to use in the actual search.
     */
    private final int threadCount;
    
    /**
     * The URL of the source article.
     */
    private final String sourceUrl;
    
    /**
     * The URL of the target article.
     */
    private final String targetUrl;
    
    /**
     * The number of trials to pop the queue before exiting.
     */
    private final int dequeueTrials;
    
    /**
     * The number of milliseconds to wait after each unsuccessful trial to pop
     * the queue.
     */
    private final int trialWaitTime;
    
    /**
     * Constructs this object holding the parsed command line arguments.
     * 
     * @param log           whether to log the progress to a stream.
     * @param threadCount   the number of threads to use in the search.
     * @param sourceUrl     the URL of the source article.
     * @param targetUrl     the URL of the target article.
     * @param dequeueTrials the number of trials to pop the queue.
     * @param trialWaitTime the number of milliseconds to wait after each 
     *                      unsuccessful trial.
     */
    CommandLineArguments(final boolean log,
                         final int threadCount,
                         final String sourceUrl, 
                         final String targetUrl,
                         final int dequeueTrials,
                         final int trialWaitTime) {
        this.log           = log;
        this.threadCount   = threadCount;
        this.sourceUrl     = sourceUrl;
        this.targetUrl     = targetUrl;
        this.dequeueTrials = dequeueTrials;
        this.trialWaitTime = trialWaitTime;
    }
    
    boolean doLog() {
        return log;
    }
    
    int getThreadCount() {
        return threadCount;
    }
    
    String getSourceUrl() {
        return sourceUrl;
    }
    
    String getTargetUrl() {
        return targetUrl;
    }
    
    int getDequeueTrials() {
        return dequeueTrials;
    }
    
    int getTrialWaitTime() {
        return trialWaitTime;
    }
}
