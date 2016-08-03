package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.coderodde.wikipedia.sp.AbstractWikipediaShortestPathFinder;

/**
 * This class implements a parallel multidirectional breadth-first search for 
 * crawling the shortest path in the Wikipedia article digraph.
 * <p>
 * The thread pool semantics are as follows: we have two search directions:
 * the forward search direction, and the backward search direction. The forward
 * search proceeds along the directed arcs, whereas the backward search moves
 * in an "opposite" direction, i.e., from the head of the arc to its tail. 
 * <p>
 * The user of this class must pass {@code threadsPerSearchDirection} which 
 * specifies how many threads to create for each search direction. For each
 * direction, 1 thread will be a "<b>master thread</b>," and the other
 * {@code threadsPerSearchDirection - 1} "<b>slave threads</b>."
 * <p>
 * Whenever a slave thread finds the frontier queue empty, it puts itself to 
 * sleep. Whenever a master thread finds the frontier queue empty, and all the 
 * slave threads working in the same direction are sleeping, the master thread
 * waits for some short period of time, and if even after that wait period the
 * queue does not become non-empty, the master thread requests <b>all</b> the
 * threads of this algorithm to exit, and thus, terminates the search.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jun 28, 2016)
 */
public class ParallelMultidirectionalWikipediaShortestPathFinder 
extends AbstractWikipediaShortestPathFinder {

    /**
     * The minimum allowed number of threads working on a particular search
     * direction.
     */
    private static final int MINIMUM_NUMBER_OF_THREADS_PER_SEARCH_DIRECTION = 1;
    
    /**
     * The number of threads working on a particular search direction.
     */
    private final int threadsPerSearchDirection;
    
    public ParallelMultidirectionalWikipediaShortestPathFinder(
            final int threadsPerSearchDirection) {
        this.threadsPerSearchDirection = 
                Math.max(threadsPerSearchDirection,
                         MINIMUM_NUMBER_OF_THREADS_PER_SEARCH_DIRECTION);
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
        
        if (getChildArticles(apiUrlText, sourceTitle).isEmpty()) {
            this.duration = 0;
            System.out.println("BAD SOURCE");
            return new ArrayList<>(1);
        }
        
        if (getParentArticles(apiUrlText, targetTitle).isEmpty()) {
            this.duration = 0;
            System.out.println("BAD TARGET");
            return new ArrayList<>(1);
        }
        
        this.duration = System.currentTimeMillis();
        
        // Create the state object shared by both the search direction:
        final SharedSearchState sharedSearchState = new SharedSearchState();
        
        // Create the state object shared by all the threads working on forward
        // direction:
        final SearchState forwardSearchState  = 
                new SearchState(sourceTitle, threadsPerSearchDirection);
        
        // Create the state object shared by all the threads working on backward
        // direction:
        final SearchState backwardSearchState = 
                new SearchState(targetTitle, threadsPerSearchDirection);
        
        sharedSearchState.setForwardSearchState(forwardSearchState);
        sharedSearchState.setBackwardSearchState(backwardSearchState);
        
        final ForwardSearchThread[] forwardSearchThreads =
                new ForwardSearchThread[threadsPerSearchDirection];
        
        forwardSearchThreads[0] = 
                new ForwardSearchThread(forwardSearchState,
                                        sharedSearchState, 
                                        true,
                                        out, 
                                        0,
                                        apiUrlText);
        
        forwardSearchState.introduceThread(forwardSearchThreads[0]);
        forwardSearchThreads[0].start();
        
        for (int i = 1; i < threadsPerSearchDirection; ++i) {
            forwardSearchThreads[i] = 
                    new ForwardSearchThread(forwardSearchState,
                                            sharedSearchState,
                                            false,
                                            out,
                                            i,
                                            apiUrlText);
            
            forwardSearchState.introduceThread(forwardSearchThreads[i]);
            forwardSearchThreads[i].start();
        }
            
        final BackwardSearchThread[] backwardSearchThreads =
                new BackwardSearchThread[threadsPerSearchDirection];
        
        backwardSearchThreads[0] = 
                new BackwardSearchThread(backwardSearchState, 
                                         sharedSearchState, 
                                         true, 
                                         out, 
                                         forwardSearchThreads.length, 
                                         apiUrlText);
        
        backwardSearchState.introduceThread(backwardSearchThreads[0]);
        backwardSearchThreads[0].start();
        
        for (int i = 1; i < threadsPerSearchDirection; ++i) {
            backwardSearchThreads[i] = 
                    new BackwardSearchThread(backwardSearchState, 
                                             sharedSearchState,
                                             false,
                                             out, 
                                             forwardSearchThreads.length + i,
                                             apiUrlText);
            
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
        
        this.duration = System.currentTimeMillis() - this.duration;
        this.numberOfExpandedNodes = 0;
        
        for (final ForwardSearchThread thread : forwardSearchThreads) {
            this.numberOfExpandedNodes += thread.getNumberOfExpandedNodes();
        }
        
        for (final BackwardSearchThread thread : backwardSearchThreads) {
            this.numberOfExpandedNodes += thread.getNumberOfExpandedNodes();
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
        private final ConcurrentQueueWrapper queue = 
                new ConcurrentQueueWrapper(new ArrayDeque<>());
        
        /**
         * This map maps each discovered node to its predecessor on the shortest 
         * path so far.
         */
        private final ConcurrentMapWrapper<String, String> parents = 
                new ConcurrentMapWrapper<>();
        
        /**
         * This map maps each discovered node to its best distance from the 
         * start node so far.
         */
        private final ConcurrentMapWrapper<String, Integer> distance =
                new ConcurrentMapWrapper<>();
        
        /**
         * Caches the total number of threads working on the search direction 
         * specified by this state object.
         */
        private final int totalNumberOfThreads;
        
        /**
         * The set of all the threads working on this particular direction. 
         * Contains the only master thread and all the slave threads spawned by
         * the master thread.
         */
        private final Set<StoppableThread> runningThreadSet = 
                Collections.<StoppableThread>
                        newSetFromMap(new ConcurrentHashMap<>());
        
        /**
         * The set of all <b>slave</b> threads that are currently sleeping.
         */
        private final Set<SleepingThread> sleepingThreadSet = 
                Collections.<SleepingThread>
                        newSetFromMap(new ConcurrentHashMap<>());
        
        public SearchState(final String initialNode, 
                           final int totalNumberOfThreads) {
            this.totalNumberOfThreads = totalNumberOfThreads;
            queue.enqueue(initialNode);
            parents.put(initialNode, null);
            distance.put(initialNode, 0);
        }
        
        int getTotalNumberOfThreads() {
            return totalNumberOfThreads;
        }
        
        int getSleepingThreadCount() {
            return sleepingThreadSet.size();
        }
        
        /**
         * Returns the queue of the search frontier.
         * 
         * @return the queue of the search frontier.
         */
        ConcurrentQueueWrapper getQueue() {
            return queue;
        }
        
        /**
         * Returns the map mapping each node to its parent.
         * 
         * @return the parent map.
         */
        ConcurrentMapWrapper<String, String> getParentMap() {
            return parents;
        }
        
        /**
         * Returns the map mapping each node to its best distance.
         * 
         * @return the distance map.
         */
        ConcurrentMapWrapper<String, Integer> getDistanceMap() {
            return distance;
        }
        
        /**
         * Introduces a new thread to this search direction.
         * 
         * @param thread the thread to introduce.
         */
        void introduceThread(final StoppableThread thread) {
            System.out.println("[!] Introducing thread " + thread);
            // WARNING: set instead of list here.
            runningThreadSet.add(thread);
        }
        
        void putThreadToSleep(final SleepingThread thread) {
            System.out.println("[!] Putting thread " + thread + " to sleep.");
            sleepingThreadSet.add(thread);
            thread.putThreadToSleep(true);
        }
        
        void wakeupAllThreads() {
            System.out.println("[!] Waking up all threads.");
            for (final SleepingThread thread : sleepingThreadSet) {
                thread.putThreadToSleep(false);
            }
            
            sleepingThreadSet.clear();
        }
        
//        void wakeupThread() {
//            if (sleepingThreadSet.size() == 0) {
//                System.out.println("[!] No threads to wake up!");
//                return;
//            }
//            
//            final SleepingThread thread = sleepingThreadSet.iterator().next();
//            thread.putThreadToSleep(false);
//            System.out.println("[!] Waking up thread " + thread);
//        }
        
        void removeThread(final StoppableThread thread) {
            runningThreadSet.remove(thread);
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
    
    private abstract static class SleepingThread extends StoppableThread {
        
        protected volatile boolean sleepRequested;
        
        void putThreadToSleep(final boolean toSleep) {
            this.sleepRequested = toSleep;
        }
    }
    
    private abstract static class SearchThread extends SleepingThread {
        
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
         * Indicates whether this thread is a master or a slave thread.
         */
        protected final boolean isMasterThread;
        
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
                     final boolean isMasterThread,
                     final PrintStream out,
                     final int id,
                     final String apiUrlText) {
            this.searchState       = searchState;
            this.sharedSearchState = sharedSearchState;
            this.isMasterThread    = isMasterThread;
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
        
        public String toString() {
            return "[Thread ID: " + id + "]";
        }
        
        SearchState getSearchState() {
            return searchState;
        }
        
        int getNumberOfExpandedNodes() {
            return numberOfExpandedNodes;
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
                            final boolean isMasterThread,
                            final PrintStream out,
                            final int id,
                            final String apiUrlText) {
            super(searchState, 
                  sharedSearchState,
                  isMasterThread,
                  out,
                  id,
                  apiUrlText);
        }
        
        @Override
        public void run() {
            final ConcurrentQueueWrapper QUEUE = searchState.getQueue();
            final ConcurrentMapWrapper<String, String> PARENTS   = 
                    searchState.getParentMap();
            
            final ConcurrentMapWrapper<String, Integer> DISTANCE = 
                    searchState.getDistanceMap();
            
            while (true) {
                if (exit) {
                    return;
                }
                
                if (sleepRequested) {
                    // Only a slave thread may get here.
                    mysleep(30);
                    continue;
                }
                
                String current = QUEUE.dequeue();
                
                if (current == null) {
                    if (isMasterThread) {
                        
                        int trials = 0;
                        
                        while (trials < 50) {
                            mysleep(10);
                            
                            if ((current = QUEUE.dequeue()) != null) {
                                break;
                            }
                            
                            ++trials;
                        }
                        
                        if (searchState.getSleepingThreadCount()
                                == searchState.getTotalNumberOfThreads() - 1) {
                            sharedSearchState.requestExit();
                        } else {
                            continue;
                        }
                    } else {
                        // This thread is a slave thread, make it sleep:
                        getSearchState().putThreadToSleep(this);
                        putThreadToSleep(true);
                        continue;
                    }
                } else if (!QUEUE.isEmpty()) {
                    searchState.wakeupAllThreads();
                }
                
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
                        QUEUE.enqueue(child);
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
                             final boolean isMasterThread,
                             final PrintStream out,
                             final int id,
                             final String apiUrlText) {
            super(searchState, 
                  sharedSearchState,
                  isMasterThread,
                  out,
                  id,
                  apiUrlText);
        }
        
        @Override
        public void run() {
            final ConcurrentQueueWrapper QUEUE = searchState.getQueue();
            final ConcurrentMapWrapper<String, String> PARENTS = 
                    searchState.getParentMap();
            
            final ConcurrentMapWrapper<String, Integer> DISTANCE = 
                    searchState.getDistanceMap();
            
            while (true) {
                if (exit) {
                    return;
                }
                
                if (sleepRequested) {
                    // Only a slave thread may get here.
                    mysleep(30);
                    continue;
                }
                
                String current = QUEUE.dequeue();
                
                if (current == null) {
                    if (isMasterThread) {
                        
                        int trials = 0;
                        
                        while (trials < 50) {
                            mysleep(10);
                            
                            if ((current = QUEUE.dequeue()) != null) {
                                break;
                            }
                            
                            ++trials;
                        }
                        
                        if (searchState.getSleepingThreadCount()
                                == searchState.getTotalNumberOfThreads() - 1) {
                            sharedSearchState.requestExit();
                        } else {
                            continue;
                        }
                    } else {
                        // This thread is a slave thread, make it sleep:
                        getSearchState().putThreadToSleep(this);
                        putThreadToSleep(true);
                        continue;
                    }
                } else if (!QUEUE.isEmpty()) {
                    searchState.wakeupAllThreads();
                }
                
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
                        QUEUE.enqueue(parent);
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
            if (searchStateBackward.getDistanceMap().containsKey(current)
                    && searchStateForward.getDistanceMap()
                                         .containsKey(current)) {
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
            if (searchStateForward.getDistanceMap().containsKey(current)
                    && searchStateBackward.getDistanceMap()
                                          .containsKey(current)) {
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
            
            final ConcurrentMapWrapper<String, String> parentMapForward = 
                    searchStateForward.getParentMap();
            
            final ConcurrentMapWrapper<String, String> parentMapBackward = 
                    searchStateBackward.getParentMap();
            
            final List<String> path = new ArrayList<>();
            
            String current = touchNode;
            
            while (current != null) {
                path.add(current);
                current = parentMapForward.get(current);
            }
            
            Collections.<String>reverse(path);
            current = parentMapBackward.get(touchNode);
            
            while (current != null) {
                path.add(current);
                current = parentMapBackward.get(current);
            }
            
            return path;
        }
    }
    
    private static final class ConcurrentMapWrapper<K, V> {
        
        private final Map<K, V> map = new HashMap<>();
        
        synchronized boolean containsKey(final K key) {
            return map.containsKey(key);
        }
        
        // Unlike java.util.concurrent.ConcurrentHashMap, this map wrapper 
        // allows 'null' values:
        synchronized void put(final K key, final V value) {
            map.put(key, value);
        }
        
        synchronized V get(final K key) {
            return map.get(key);
        }
    }
    
    private static final class ConcurrentQueueWrapper {
        
        private final Deque<String> queue;
        
        ConcurrentQueueWrapper(final Deque<String> queue) {
            this.queue = queue;
        }
        
        synchronized String dequeue() {
            if (queue.isEmpty()) {
                return null;
            }
            
            return queue.removeFirst();
        }
        
        synchronized void enqueue(final String node) {
            queue.addLast(node);
        }
        
        synchronized boolean isEmpty() {
            return queue.isEmpty();
        }
    }
    
    /**
     * This method puts the calling thread to sleep for {@code milliseconds}
     * milliseconds.
     * 
     * @param milliseconds the number of milliseconds to sleep for.
     */
    private static void mysleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException ex) {
            
        }
    }
}