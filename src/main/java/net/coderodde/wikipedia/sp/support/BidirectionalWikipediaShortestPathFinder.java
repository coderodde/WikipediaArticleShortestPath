package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.List;
import net.coderodde.wikipedia.sp.AbstractWikipediaShortestPathFinder;

/**
 * This class implements a bidirectional breadth-first search for finding 
 * shortest paths in the Wikipedia article graph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 29, 2016)
 */
public final class BidirectionalWikipediaShortestPathFinder 
extends AbstractWikipediaShortestPathFinder {

    @Override
    public List<String> search(String sourceTitle, 
                               String targetTitle, 
                               String apiUrlText, 
                               PrintStream out) {
        return null;
    }
}
