package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import net.coderodde.wikipedia.sp.AbstractWikipediaShortestPathFinder;

/**
 * This class implements a parallel multidirectional breadth-first search for 
 * crawling the shortest path in the Wikipedia article (di)graph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jun 24, 2016)
 */
public final class ParallelMultidirectionalWikipediaShortestPathFinder 
extends AbstractWikipediaShortestPathFinder {

    @Override
    public List<String> search(String sourceTitle, String targetTitle, String apiUrlText, PrintStream out) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private static class StoppableThread extends Thread {
        
        protected volatile boolean exit;
        
        void stopThread() {
            exit = true;
        }
    }
    
    private static final class SearchState {
        
        private static final String PARENT_MAP_END_TOKEN = "";
        
        SearchState(final String start) {
            queue.addLast(start);
            // ConcurrentMap does not support null values:
            parentMap.put(start, PARENT_MAP_END_TOKEN);
            distanceMap.put(start, 0);
        }
        
        /**
         * Holds the actual, concurrent queue of the generated nodes.
         */
        private final Deque<String> queue = new LinkedBlockingDeque<>();
        
        /**
         * Holds the concurrent map mapping each node to the node it was 
         * generated from.
         */
        private final Map<String, String> parentMap = new ConcurrentHashMap<>();
        
        /**
         * Maps each node to its best so far distance.
         */
        private final Map<String, Integer> distanceMap = 
                new ConcurrentHashMap<>();
        
        /**
         * Caches all the threads that work on the same direction of the search.
         * As soon as this set becomes empty, the threads in the opposite search
         * direction may exit, since the target node is not reachable.
         */
        private final Set<StoppableThread> runningThreadSet = 
                Collections.<StoppableThread>
                        newSetFromMap(new ConcurrentHashMap<>());
        
        /**
         * The state of the opposite search direction.
         */
        private SearchState oppositeSearchState;
        
        SearchState getOppositeSearchState() {
            return oppositeSearchState;
        }
        
        void setOppositeSearchState(final SearchState oppositeSearchState) {
            this.oppositeSearchState = oppositeSearchState;
        }
        
        /**
         * Adds the input thread to the set {@code runningThreadSet}.
         * 
         * @param thread the new thread.
         */
        void introduceThread(final StoppableThread thread) {
            runningThreadSet.add(thread);
        }
        
        /**
         * Sends the message to all threads working on this state to exit as 
         * soon as they hit the status check.
         */
        void requestExit() {
            for (final StoppableThread thread : runningThreadSet) {
                thread.stopThread();
            }
        }
        
        /**
         * Removes the thread out of the set of threads working.
         * 
         * @param thread 
         */
        void markThreadAsDone(final Thread thread) {
            runningThreadSet.remove(thread);
            
            if (runningThreadSet.isEmpty()) {
                getOppositeSearchState().requestExit();
            }
        }
    }
    
    private static final class ForwardThread extends StoppableThread {
        
        @Override
        public void run() {
            
        }
    }
    
    private static final class BackwardThread extends StoppableThread {
        
        @Override
        public void run() {
            
        }
    }
}
