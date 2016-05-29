package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.List;
import net.coderodde.wikipedia.sp.AbstractWikipediaShortestPathFinder;

/**
 * This class implements a parallel bidirectional breadth-first search for 
 * finding shortest paths in the Wikipedia article graph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 29, 2016)
 */
public final class ParallelBidirectionalWikipediaShortestPathFinder 
extends AbstractWikipediaShortestPathFinder {

    @Override
    public List<String> search(String sourceTitle, String targetTitle, String apiUrlText, PrintStream out) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
