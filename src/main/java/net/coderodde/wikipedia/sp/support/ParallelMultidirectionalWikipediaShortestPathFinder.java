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
         * The number of threads spawned on behalf of this search direction.
         */
        private int threadsSpawnCount;
        
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
            threadsSpawnCount++;
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
        
        /**
         * Returns the total number of threads ever spawned for this search 
         * direction.
         * 
         * @return the total number of threads.
         */
        int getNumberOfSpawnedThreads() {
            return threadsSpawnCount;
        }
    }
    
    /**
     * This abstract class defines a thread that may be asked to terminate.
     */
    private abstract static class StoppableThread extends Thread {
        
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
    
    private abstract static class SearchThread extends StoppableThread {
        
        /**
         * This field specifies whether the current thread is a master thread or
         * a slave thread. For each search direction, there may be only one 
         * master thread. Both slave and master threads perform the actual 
         * search, yet only the master thread may create new slave threads.
         */
        protected final boolean master;
        
        /**
         * The entire state of this search thread, shared possibly with other
         * threads.
         */
        protected final SearchState searchState;
        
        /**
         * Constructs a new search thread.
         * 
         * @param searchState the state object.
         * @param master      the boolean flag indicating whether this new 
         *                    thread is a master or not.
         */
        SearchThread(final SearchState searchState, final boolean master) {
            this.master = master;
            this.searchState = searchState;
            searchState.introduceThread(this);
        }
        
        /**
         * Constructs a new slave search thread.
         * 
         * @param searchState the state object.
         */
        SearchThread(final SearchState searchState) {
            this(searchState, false);
        }
    }
    
    /**
     * This class implements a search thread searching in forward direction.
     */
    private static final class ForwardSearchThread extends SearchThread {

        /**
         * Constructs a new forward search thread.
         * 
         * @param searchState the state object.
         * @param master      the boolean flag indicating whether this search
         *                    thread is a master or a slave thread.
         */
        ForwardSearchThread(SearchState searchState, boolean master) {
            super(searchState, master);
        }
        
        /**
         * Constructs a new slave forward search thread.
         * 
         * @param searchState the state object.
         */
        ForwardSearchThread(SearchState searchState) {
            this(searchState, false);
        }
        
        @Override
        public void run() {
            
        }
    }
    
    /**
     * This class implements a search thread searching in backward direction.
     */
    private static final class BackwardSearchThread extends SearchThread {

        /**
         * Constructs a new backward search thread.
         * 
         * @param searchState the state object.
         * @param master      the boolean flag indicating whether this search
         *                    thread is a master or a slave thread.
         */
        BackwardSearchThread(SearchState searchState, boolean master) {
            super(searchState, master);
        }
        
        /**
         * Constructs a new slave backward search thread.
         * 
         * @param searchState the state object.
         */
        BackwardSearchThread(SearchState searchState) {
            this(searchState, false);
        }
        
        @Override
        public void run() {
            
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
