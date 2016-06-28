package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
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
 * crawling the shortest path in the Wikipedia article digraph.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jun 28, 2016)
 */
public class ParallelMultidirectionalWikipediaShortestPathFinder 
extends AbstractWikipediaShortestPathFinder {

    @Override
    public List<String> search(final String sourceTitle,
                               final String targetTitle,
                               final String apiUrlText, 
                               final PrintStream out) {
        if (sourceTitle.equals(targetTitle)) {
            return new ArrayList<>(Arrays.asList(targetTitle));
        }
        
        return null;
    }
 
    /**
     * This class holds all the state of a single search direction.
     */
    private static final class SearchState {
        
        /**
         * This FIFO queue contains the queue of nodes reached but not yet 
         * expanded. It is called <b>the search frontier</b>.
         */
        private final Deque<String> queue = new LinkedBlockingDeque();
        
        /**
         * This map maps each discovered node to its parent on the shortest path
         * so far.
         */
        private final Map<String, String> parents = new ConcurrentHashMap<>();
        
        /**
         * This map maps each discovered node to its best distance from the 
         * start node so far.
         */
        private final Map<String, Integer> distance = new ConcurrentHashMap<>();
        
        /**
         * The set of all the threads working on this particular direction. 
         * Contains the only master thread and all the slave threads spawned by
         * the master thread.
         */
        private final Set<StoppableThread> runningThreadSet = 
                Collections.<StoppableThread>
                        newSetFromMap(new ConcurrentHashMap<>());
        
        /**
         * Returns the queue of the search frontier.
         * 
         * @return the queue of the search frontier.
         */
        Deque<String> getQueue() {
            return queue;
        }
        
        /**
         * Returns the map mapping each node to its parent.
         * 
         * @return the parent map.
         */
        Map<String, String> getParentMap() {
            return parents;
        }
        
        /**
         * Returns the map mapping each node to its best distance.
         * 
         * @return 
         */
        Map<String, Integer> getDistanceMap() {
            return distance;
        }
        
        /**
         * Introduces a new thread to this search direction.
         * 
         * @param thread the thread to introduce.
         */
        void introduceThread(final StoppableThread thread) {
            // WARNING: set instead of list here.
            runningThreadSet.add(thread);
        }
        
        /**
         * Tells all the thread working on current direction to exit so that the
         * threads may be joined.
         */
        void requestThreadsToExit() {
            for (final StoppableThread thread : runningThreadSet) {
                thread.requestThreadToExit();
            }
        }
    }
    
    /**
     * This abstract class defines a thread that may be asked to terminate.
     */
    private abstract static class StoppableThread {
        
        /**
         * If set to {@code true}, this thread should exit.
         */
        protected volatile boolean exit;
        
        /**
         * Sends a request to finish the work.
         */
        void requestThreadToExit() {
            exit = true;
        }
    }
    
    
    
    /**
     * This class holds the state shared by the two search directions.
     */
    private static final class SharedSearchState {
        
        /**
         * The state of all the forward search threads.
         */
        private SearchState searchStateForward;
        
        /**
         * The state of all the backward search threads.
         */
        private SearchState searchStateBackward;
        
        /**
         * Caches the best known length from the source to the target nodes.
         */
        private volatile int bestPathLengthSoFar = Integer.MAX_VALUE;
        
        /**
         * The best search frontier touch node so far.
         */
        private volatile String touchNode;
        
        /**
         * Caches whether the shortest path was found.
         */
        private boolean pathIsFound;
        
        void setForwardSearchState(final SearchState searchStateForward) {
            this.searchStateForward = searchStateForward;
        }
        
        void setBackwardSearchState(final SearchState searchStateBackward) {
            this.searchStateBackward = searchStateBackward;
        }
        
        synchronized void updateFromForwardDirection(final String current) {
            if (searchStateBackward.getDistanceMap().containsKey(current)) {
                final int currentDistance = 
                        searchStateForward .getDistanceMap().get(current) +
                        searchStateBackward.getDistanceMap().get(current);
                
                if (bestPathLengthSoFar > currentDistance) {
                    bestPathLengthSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized void updateFromBackwardDirection(final String current) {
            if (searchStateForward.getDistanceMap().containsKey(current)) {
                final int currentDistance = 
                        searchStateForward .getDistanceMap().get(current) +
                        searchStateBackward.getDistanceMap().get(current);
                
                if (bestPathLengthSoFar > currentDistance) {
                    bestPathLengthSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized boolean pathIsOptimal(final String node) {
            if (touchNode == null) {
                // Once here, the two search trees did not meet each other yet.
                return false;
            }
            
            if (!searchStateForward.getDistanceMap().containsKey(node)) {
                // The forward search did not reach the node 'node' yet.
                return false;
            }
            
            if (!searchStateBackward.getDistanceMap().containsKey(node)) {
                // The backward search did not reach the node 'node' yet.
                return false;
            }
            
            final int distance = 
                    searchStateForward .getDistanceMap().get(node) +
                    searchStateBackward.getDistanceMap().get(node);
            
            if (distance > bestPathLengthSoFar) {
                searchStateForward .requestThreadsToExit();
                searchStateBackward.requestThreadsToExit();
                return true;
            }
            
            return false;
        }
    }
}
