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
    
    /**
     * The minimum allowed number of threads working on a particular search
     * direction.
     */
    private static final int MINIMUM_NUMBER_OF_THREADS_PER_SEARCH_DIRECTION = 1;
    
    /**
     * The number of threads working on a particular search direction.
     */
    private final int threadsPerSearchDirection;
    
    /**
     * The number of trials to dequeue the queue before giving up and exiting
     * the thread.
     */
    private final int dequeueTrials;
    
    /**
     * The number of milliseconds to wait during a dequeuing trial.
     */
    private final int trialWaitTime;
    
    public ParallelMultidirectionalWikipediaShortestPathFinder(
            final int threadsPerSearchDirection,
            final int dequeueTrials,
            final int trialWaitTime) {
        this.threadsPerSearchDirection = 
                Math.max(threadsPerSearchDirection,
                         MINIMUM_NUMBER_OF_THREADS_PER_SEARCH_DIRECTION);
        this.dequeueTrials = dequeueTrials;
        this.trialWaitTime = trialWaitTime;
    }
    
    @Override
    public List<String> search(final String sourceTitle,
                               final String targetTitle,
                               final String apiUrlText, 
                               final PrintStream out) {
        // TODO: Find out whether this if is necessary.
        if (sourceTitle.equals(targetTitle)) {
            final List<String> ret = new ArrayList<>(1);
            
            // The article exists?
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
        
        final ForwardSearchThread[] forwardSearchThreads =
                new ForwardSearchThread[threadsPerSearchDirection];
        
        for (int i = 0; i < threadsPerSearchDirection; ++i) {
            forwardSearchThreads[i] = 
                    new ForwardSearchThread(forwardSearchState,
                                            sharedSearchState,
                                            out,
                                            i,
                                            apiUrlText,
                                            dequeueTrials,
                                            trialWaitTime);
            
            forwardSearchState.introduceThread(forwardSearchThreads[i]);
            forwardSearchThreads[i].start();
        }
            
        final BackwardSearchThread[] backwardSearchThreads =
                new BackwardSearchThread[threadsPerSearchDirection];
        
        for (int i = 0; i < threadsPerSearchDirection; ++i) {
            backwardSearchThreads[i] = 
                    new BackwardSearchThread(forwardSearchState, 
                                             sharedSearchState, 
                                             out, 
                                             i, 
                                             apiUrlText,
                                             dequeueTrials,
                                             trialWaitTime);
            
            backwardSearchState.introduceThread(backwardSearchThreads[i]);
            backwardSearchThreads[i].start();
        }
        
        try {
            for (final ForwardSearchThread thread : forwardSearchThreads) {
                thread.join();
            }
        } catch (final InterruptedException ex) {
            throw new IllegalStateException("The forward thread threw " +
                    ex.getClass().getSimpleName() + ": " +
                    ex.getMessage(), ex);
        }
        
        try {
            for (final BackwardSearchThread thread : backwardSearchThreads) {
                thread.join();
            }
        } catch (final InterruptedException ex) {
            throw new IllegalStateException("The backward thread threw " +
                    ex.getClass().getSimpleName() + ": " +
                    ex.getMessage(), ex);
        }
        
        return sharedSearchState.getPath();
    }
 
    /**
     * This class holds all the state of a single search direction.
     */
    private static final class SearchState {
        
        /**
         * This FIFO queue contains the queue of nodes reached but not yet 
         * expanded. It is called the <b>search frontier</b>.
         */
        private final Deque<String> queue = new LinkedBlockingDeque();
        
        /**
         * This map maps each discovered node to its predecessor on the shortest 
         * path so far.
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
         * The number of trials to dequeue the queue.
         */
        protected final int dequeuTrials;
        
        /**
         * The number of milliseconds to wait if a dequeuing trial fails.
         */
        protected final int trialWaitTime;
        
        /**
         * Constructs a new search thread.
         * 
         * @param searchState the state object.
         * @param master      the boolean flag indicating whether this new 
         *                    thread is a master or not.
         */
        SearchThread(final SearchState searchState, 
                     final SharedSearchState sharedSearchState,
                     final PrintStream out,
                     final int id,
                     final String apiUrlText,
                     final int dequeuTrials,
                     final int trialWaitTime) {
            this.searchState       = searchState;
            this.sharedSearchState = sharedSearchState;
            this.out               = out;
            this.id                = id;
            this.apiUrlText        = apiUrlText;
            this.dequeuTrials      = dequeuTrials;
            this.trialWaitTime     = trialWaitTime;
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
                            final PrintStream out,
                            final int id,
                            final String apiUrlText,
                            final int dequeueTrials,
                            final int trialWaitTime) {
            super(searchState, 
                  sharedSearchState,
                  out,
                  id,
                  apiUrlText,
                  dequeueTrials,
                  trialWaitTime);
        }
        
        @Override
        public void run() {
            final Deque<String> QUEUE           = searchState.getQueue();
            final Map<String, String> PARENTS   = searchState.getParentMap();
            final Map<String, Integer> DISTANCE = searchState.getDistanceMap();
            
            while (true) {
                if (exit) {
                    return;
                }
                
                while (QUEUE.isEmpty()) {
                    if (exit) {
                        return;
                    }
                    
                    mysleep(trialWaitTime);
                }
                
                final String current = QUEUE.removeFirst();
                
                if (out != null) {
                    out.println("[Forward search thread " + 
                                getId() + 
                                " expanding: " + current + "]");
                }
             
                sharedSearchState.updateFromForwardDirection(current);
                
                if (sharedSearchState.pathIsOptimal(current)) {
                    sharedSearchState.requestExit();
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
                             final PrintStream out,
                             final int id,
                             final String apiUrlText,
                             final int dequeuTrial,
                             final int trialWaitTime) {
            super(searchState, 
                  sharedSearchState,
                  out,
                  id,
                  apiUrlText,
                  dequeuTrial,
                  trialWaitTime);
        }
        
        @Override
        public void run() {
            final Deque<String> QUEUE           = searchState.getQueue();
            final Map<String, String> PARENTS   = searchState.getParentMap();
            final Map<String, Integer> DISTANCE = searchState.getDistanceMap();
            
            while (true) {
                if (exit) {
                    return;
                }
                
                while (QUEUE.isEmpty()) {
                    if (exit) {
                        return;
                    }
                    
                    mysleep(trialWaitTime);
                }
                
                String current = QUEUE.removeFirst();
                
                if (out != null) {
                    out.println("[Backward search thread " + 
                                getId() + 
                                " expanding: " + current + "]");
                }
                
                sharedSearchState.updateFromBackwardDirection(current);
                
                if (sharedSearchState.pathIsOptimal(current)) {
                    sharedSearchState.requestExit();
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
                pathIsFound = true;
                return true;
            }
            
            return false;
        }
        
        synchronized void requestExit() {
            searchStateForward .requestThreadsToExit();
            searchStateBackward.requestThreadsToExit();
        }
        
        synchronized List<String> getPath() {
            if (!pathIsFound) {
                return new ArrayList<>();
            }
            
            final Map<String, String> parentMapForward = 
                    searchStateForward.getParentMap();
            
            final Map<String, String> parentMapBackward = 
                    searchStateBackward.getParentMap();
            
            final List<String> path = new ArrayList<>();
            
            String current = touchNode;
            
            while (current != PARENT_MAP_END_TOKEN) {
                path.add(current);
                current = parentMapForward.get(current);
            }
            
            Collections.<String>reverse(path);
            current = parentMapBackward.get(touchNode);
            
            while (current != PARENT_MAP_END_TOKEN) {
                path.add(current);
                current = parentMapBackward.get(current);
            }
            
            return path;
        }
    }
    
    private static void mysleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException ex) {
            
        }
    }
}
