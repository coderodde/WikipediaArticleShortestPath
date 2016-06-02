package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * Searches for the shortest path from the Wikipedia article with the title
     * {@code sourceTitle} to the article with the title {@code targetTitle}.
     * The algorithm is a parallel bidirectional breadth-first search running 
     * each of the two search frontiers in their own threads.
     * 
     * @param sourceTitle the title of the source article.
     * @param targetTitle the title of the target article.
     * @param apiUrlText  the Wikipedia API access URL text.
     * @param out         the output stream to write the progress to.
     * @return the shortest path.
     */
    @Override
    public List<String> search(String sourceTitle,
                               String targetTitle, 
                               String apiUrlText, 
                               PrintStream out) {
    
        if (sourceTitle.equals(targetTitle)) {
            return new ArrayList<>(Arrays.asList(sourceTitle));
        }
        
        TouchNodeHolder touchNodeHolder = new TouchNodeHolder();
        
        ForwardThread forwardThread = new ForwardThread(sourceTitle,
                                                        apiUrlText,
                                                        touchNodeHolder,
                                                        out);
        
        BackwardThread backwardThread = new BackwardThread(targetTitle,
                                                           apiUrlText,
                                                           touchNodeHolder,
                                                           out);
        touchNodeHolder.setForwardThread(forwardThread);
        touchNodeHolder.setBackwardThread(backwardThread);
        
        forwardThread.start();
        backwardThread.start();
        
        try {
            forwardThread.join();
            backwardThread.join();
        } catch (InterruptedException ex) {
            throw new IllegalStateException(
                    "The forward thread threw " + 
                            ex.getClass().getSimpleName() + ": " + 
                            ex.getMessage(), ex);
        }
        
        return touchNodeHolder.constructPath();    
    }
    
    private static class ForwardThread extends Thread {
        
        private final Deque<String> QUEUE = new ArrayDeque<>();
        private final Map<String, String> PARENTS = new ConcurrentHashMap<>();
        private final Map<String, Integer> DISTANCE = new ConcurrentHashMap<>();
        private final TouchNodeHolder touchNodeHolder;
        private final String apiUrlText;
        private final PrintStream out;
        private volatile boolean exit;
        
        ForwardThread(String sourceTitle, 
                      String apiUrlText,
                      TouchNodeHolder touchNodeHolder,
                      PrintStream out) {
            this.apiUrlText = apiUrlText;
            this.touchNodeHolder = touchNodeHolder;
            this.out = out;
            
            QUEUE.add(sourceTitle);
            PARENTS.put(sourceTitle, null);
            DISTANCE.put(sourceTitle, 0);
        }
        
        Map<String, Integer> getDistanceMap() {
            return DISTANCE;
        }
        
        Map<String, String> getParentMap() {
            return PARENTS;
        }
        
        void exitThread() {
            exit = true;
        }
        
        @Override
        public void run() {
            while (!QUEUE.isEmpty()) {
                if (exit) {
                    return;
                }

                String current = QUEUE.removeFirst();

                if (out != null) {
                    out.println("[Forward search expanding:  " + current + "]");
                }

                touchNodeHolder.updateFromForwardSearch(current);

                if (touchNodeHolder.pathIsOptimal(current)) {
                    return;
                }

                for (String child : getChildArticles(apiUrlText, current)) {
                    if (!PARENTS.containsKey(child)) {
                        PARENTS.put(child, current);
                        DISTANCE.put(child, DISTANCE.get(current) + 1);
                        QUEUE.addLast(child);
                    }
                }
            }
        }
    }
    
    private static final class BackwardThread extends Thread {
        
        private final Deque<String> QUEUE = new ArrayDeque<>();
        private final Map<String, String> PARENTS = new ConcurrentHashMap<>();
        private final Map<String, Integer> DISTANCE = new ConcurrentHashMap<>();
        private final String apiUrlText;
        private final PrintStream out;
        private volatile boolean exit;
        private final TouchNodeHolder touchNodeHolder;
        
        BackwardThread(String targetTitle, 
                       String apiUrlText,
                       TouchNodeHolder touchNodeHolder,
                       PrintStream out) {
            this.apiUrlText = apiUrlText;
            this.touchNodeHolder = touchNodeHolder;
            this.out = out;
            
            QUEUE.add(targetTitle);
            PARENTS.put(targetTitle, null);
            DISTANCE.put(targetTitle, 0);
        }
        
        Map<String, Integer> getDistanceMap() {
            return DISTANCE;
        }
        
        Map<String, String> getParentMap() {
            return PARENTS;
        }
        
        void exitThread() {
            exit = true;
        }
        
        @Override
        public void run() {
            while (!QUEUE.isEmpty()) {
                if (exit) {
                    return;
                }

                String current = QUEUE.removeFirst();

                if (out != null) {
                    out.println("[Backward search expanding: " + current + "]");
                }

                touchNodeHolder.updateFromBackwardThread(current);

                if (touchNodeHolder.pathIsOptimal(current)) {
                    return;
                }

                for (String parent : getParentArticles(apiUrlText, current)) {
                    if (!PARENTS.containsKey(parent)) {
                        PARENTS.put(parent, current);
                        DISTANCE.put(parent, DISTANCE.get(current) + 1);
                        QUEUE.addLast(parent);
                    }
                }
            }
        }
    }
    
    private static final class TouchNodeHolder {
        
        private ForwardThread forwardThread;
        private BackwardThread backwardThread;
        private volatile String touchNode;
        private volatile int bestDistanceSoFar = Integer.MAX_VALUE;
        
        void setForwardThread(ForwardThread forwardThread) {
            this.forwardThread = forwardThread;
        }
        
        void setBackwardThread(BackwardThread backwardThread) {
            this.backwardThread = backwardThread;
        }
        
        synchronized boolean pathIsOptimal(String node) {
            if (touchNode == null) {
                return false;
            }
            
            if (!forwardThread.getDistanceMap().containsKey(node)) {
                return false;
            }
            
            if (!backwardThread.getDistanceMap().containsKey(node)) {
                return false;
            }
            
            int distance = forwardThread.getDistanceMap().get(node) + 
                           backwardThread.getDistanceMap().get(node);
            
            if (distance > bestDistanceSoFar) {
                forwardThread .exitThread();
                backwardThread.exitThread();
                return true;
            }
            
            return false;
        }
        
        synchronized void updateFromForwardSearch(String current) {
            if (backwardThread.getDistanceMap().containsKey(current)) {
                int currentDistance = 
                        forwardThread .getDistanceMap().get(current) +
                        backwardThread.getDistanceMap().get(current);
                
                if (bestDistanceSoFar > currentDistance) {
                    bestDistanceSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized void updateFromBackwardThread(String current) {
            if (forwardThread.getDistanceMap().containsKey(current)) {
                int currentDistance = 
                        forwardThread .getDistanceMap().get(current) +
                        backwardThread.getDistanceMap().get(current);
                
                if (bestDistanceSoFar > currentDistance) {
                    bestDistanceSoFar = currentDistance;
                    touchNode = current;
                }
            }
        }
        
        synchronized List<String> constructPath() {
            return tracebackPath(touchNode, 
                                 forwardThread.getParentMap(),
                                 backwardThread.getParentMap());
        }
    }
}
