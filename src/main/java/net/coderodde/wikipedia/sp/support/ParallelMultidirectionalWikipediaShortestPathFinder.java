package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayList;
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

    /**
     * Since {@link java.util.concurrent.ConcurrentHashMap} does not allow 
     * {@code null} values, we need a token representing the {@code null}.
     */
    private static final String PARENT_MAP_END_TOKEN = "";
    
    @Override
    public List<String> search(final String sourceTitle,
                               final String targetTitle,
                               final String apiUrlText, 
                               final PrintStream out) {
        // TODO: Find out whether this if is necessary.
        if (sourceTitle.equals(targetTitle)) {
            final List<String> ret = new ArrayList<>(1);
            
            // The artical exists?
            if (!getChildArticles(apiUrlText, sourceTitle).isEmpty()) {
                ret.add(sourceTitle);
            }
        
            return ret;
        }
        
        final SharedSearchState sharedSearchState = new SharedSearchState();
        final SearchState forwardSearchState  = new SearchState(sourceTitle);
        final SearchState backwardSearchState = new SearchState(targetTitle);
        
        sharedSearchState.setForwardSearchState(forwardSearchState);
        sharedSearchState.setBackwardSearchState(backwardSearchState);
        
        final ForwardSearchThread forwardSearchThread =
                new ForwardSearchThread(forwardSearchState,
                                        sharedSearchState,
                                        true,
                                        out,
                                        0,
                                        apiUrlText);
        
        final BackwardSearchThread backwardSearchThread =
                new BackwardSearchThread(backwardSearchState,
                                         sharedSearchState,
                                         true,
                                         out,
                                         0,
                                         apiUrlText);
        
        forwardSearchThread.start();
        backwardSearchThread.start();
        
        try {
            forwardSearchThread.join();
        } catch (final InterruptedException ex) {
            throw new IllegalStateException("The forward thread threw " +
                    ex.getClass().getSimpleName() + ": " +
                    ex.getMessage(), ex);
        }
        
        try {
            backwardSearchThread.join();
        } catch (final InterruptedException ex) {
            throw new IllegalStateException("The backward thread threw " +
                    ex.getClass().getSimpleName() + ": " +
                    ex.getMessage(), ex);
        }
        
        final List<String> path = null;
        
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
         * expanded. It is called the <b>search frontier</b>.
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
        
        public SearchState(final String initialNode) {
            queue.add(initialNode);
            parents.put(initialNode, PARENT_MAP_END_TOKEN);
            distance.put(initialNode, 0);
        }
        
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
         * @return the distance map.
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
         * The state shared by both the directions.
         */
        protected final SharedSearchState sharedSearchState;
        
        /**
         * The output stream for possibly logging the search progress. The value
         * of {@code null} is allowed.
         */
        protected final PrintStream out;
        
        /**
         * Caches the amount of nodes expanded by this thread.
         */
        protected int numberOfExpandedNodes;
        
        /**
         * The ID of this thread.
         */
        protected final int id;
        
        /**
         * The URL to the Wikipedia API.
         */
        protected final String apiUrlText;
        
        /**
         * Constructs a new search thread.
         * 
         * @param searchState the state object.
         * @param master      the boolean flag indicating whether this new 
         *                    thread is a master or not.
         */
        SearchThread(final SearchState searchState, 
                     final SharedSearchState sharedSearchState,
                     final boolean master,
                     final PrintStream out,
                     final int id,
                     final String apiUrlText) {
            this.searchState       = searchState;
            this.sharedSearchState = sharedSearchState;
            this.master            = master;
            this.out               = out;
            this.id                = id;
            this.apiUrlText        = apiUrlText;
        }
            
        @Override
        public boolean equals(final Object other) {
            if (other == null) {
                return false;
            }
            
            if (!getClass().equals(other.getClass())) {
                return false;
            }
            
            return id == ((SearchThread) other).id;
        }
        
        @Override
        public int hashCode() {
            return id;
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
        ForwardSearchThread(final SearchState searchState, 
                            final SharedSearchState sharedSearchState,
                            final boolean master, 
                            final PrintStream out,
                            final int id,
                            final String apiUrlText) {
            super(searchState, 
                  sharedSearchState,
                  master, 
                  out,
                  id,
                  apiUrlText);
        }
        
        @Override
        public void run() {
            final Deque<String> QUEUE           = searchState.getQueue();
            final Map<String, String> PARENTS   = searchState.getParentMap();
            final Map<String, Integer> DISTANCE = searchState.getDistanceMap();
            
            while (!QUEUE.isEmpty()) {
                if (exit) {
                    return;
                }
                
                final String current = QUEUE.removeFirst();
                
                if (out != null) {
                    out.println("[Forward search thread " + 
                                getId() + 
                                " expanding: " + current + "]");
                }
             
                sharedSearchState.updateFromForwardDirection(current);
                
                if (sharedSearchState.pathIsOptimal(current)) {
                    return;
                }
                
                numberOfExpandedNodes++;
                
                for (final String child : 
                        getChildArticles(apiUrlText, current)) {
                    if (!PARENTS.containsKey(child)) {
                        PARENTS.put(child, current);
                        DISTANCE.put(child, DISTANCE.get(current) + 1);
                        QUEUE.addLast(child);
                    }
                }
            }
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
        BackwardSearchThread(final SearchState searchState, 
                             final SharedSearchState sharedSearchState,
                             final boolean master,
                             final PrintStream out,
                             final int id,
                             final String apiUrlText) {
            super(searchState, 
                  sharedSearchState,
                  master,
                  out,
                  id,
                  apiUrlText);
        }
        
        @Override
        public void run() {
            final Deque<String> QUEUE           = searchState.getQueue();
            final Map<String, String> PARENTS   = searchState.getParentMap();
            final Map<String, Integer> DISTANCE = searchState.getDistanceMap();
            
            while (!QUEUE.isEmpty()) {
                if (exit) {
                    return;
                }
                
                String current = QUEUE.removeFirst();
                
                if (out != null) {
                    out.println("[Backward search thread " + 
                                getId() + 
                                " expanding: " + current + "]");
                }
                
                sharedSearchState.updateFromBackwardDirection(current);
                
                if (sharedSearchState.pathIsOptimal(current)) {
                    return;
                }
                
                numberOfExpandedNodes++;
                
                for (final String parent : getParentArticles(apiUrlText, current)) {
                    if (!PARENTS.containsKey(parent)) {
                        PARENTS.put(parent, current);
                        DISTANCE.put(parent, DISTANCE.get(current) + 1);
                        QUEUE.addLast(parent);
                    }
                }
            }
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
