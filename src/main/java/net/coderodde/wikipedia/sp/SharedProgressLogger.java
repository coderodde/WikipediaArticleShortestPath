package net.coderodde.wikipedia.sp;

import java.util.List;

/**
 * This class logs the shared progress on starting and ending the search.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Aug 4, 2016)
 */
public class SharedProgressLogger extends ProgressLogger<String> {
    
    private static final String LABEL = "[SHARED SEARCH PROGRESS]";
    
    @Override
    public void onBeginSearch(final String source, final String target) {
        System.out.println(
                LABEL + " Began searching the path from \"" + source + 
                "\" to \"" + target + "\".");
    }
    
    @Override
    public void onShortestPath(final List<String> path) {
        System.out.println(
                LABEL + " Found a shortest path from \"" + path.get(0) + 
                "\" to \"" + path.get(path.size() - 1) + "\".");
    }

    @Override
    public void onTargetUnreachable(final String source, final String target) {
        System.out.println(
                LABEL + " Failed to find any path from \"" + source + 
                "\" to \"" + target + "\".");
    }
}
