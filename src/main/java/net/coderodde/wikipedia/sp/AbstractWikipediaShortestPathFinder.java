package net.coderodde.wikipedia.sp;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This interface defines the API for the shortest path algorithms working on 
 * the Wikipedia article graph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 29, 2016)
 */
public abstract class AbstractWikipediaShortestPathFinder {
    
    public abstract List<String> search(String sourceTitle, 
                                        String targetTitle,
                                        String apiUrlText,
                                        PrintStream out);
    
        
    /**
     * Constructs the shortest path.
     * 
     * @param touchNode the article where the two search frontiers "meet" each
     * o                other.
     * @param PARENTSA the parent map of the forward search.
     * @param PARENTSB the parent map of the backward search.
     * @return a shortest path.
     */
    protected static List<String> tracebackPath(String touchNode, 
                                                Map<String, String> PARENTSA, 
                                                Map<String, String> PARENTSB) {
        List<String> path = new ArrayList<>();
        String node = touchNode;

        while (node != null) {
            path.add(node);
            node = PARENTSA.get(node);
        }

        Collections.reverse(path);
        node = PARENTSB.get(touchNode);

        while (node != null) {
            path.add(node);
            node = PARENTSB.get(node);
        }

        return path;
    }
}
