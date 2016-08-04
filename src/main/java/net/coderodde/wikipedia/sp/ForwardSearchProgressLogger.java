package net.coderodde.wikipedia.sp;

import java.util.List;

/**
 * This class implements a forward search progress logger.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Aug 4, 2016)
 */
public class ForwardSearchProgressLogger extends ProgressLogger<String> {

    private static final String LABEL = "[FORWARD SEARCH PROGRESS]";
    
    @Override
    public void onExpansion(final String node) {
        System.out.println(LABEL + " Expanding \"" + node + "\".");
    }
}
