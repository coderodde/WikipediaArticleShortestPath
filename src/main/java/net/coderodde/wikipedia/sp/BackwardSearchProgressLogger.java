package net.coderodde.wikipedia.sp;

/**
 * This class implements a backward search progress logger.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Aug 4, 2016)
 */
public class BackwardSearchProgressLogger extends ProgressLogger<String> {

    private static final String LABEL = "[BACKWARD SEARCH PROGRESS]";
    
    @Override
    public void onExpansion(final String node) {
        System.out.println(LABEL + " Expanding \"" + node + "\".");
    }
}
