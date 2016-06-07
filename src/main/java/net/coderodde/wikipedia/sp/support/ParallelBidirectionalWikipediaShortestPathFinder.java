package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
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
     * Since {@link java.util.concurrent.ConcurrentHashMap} does not allow 
     * {@code null} values, we need a token representing the {@code null}.
     */
    private static final String PARENT_MAP_END_TOKEN = "";

    /**
     * Searches for the shortest path from the Wikipedia article with the title
     * {@code sourceTitle} to the article with the title {@code targetTitle}.
     * The algorithm is a parallel bidirectional breadth-first search running 
     * each of the two search frontiers in their own threads.
     * 
     * @param source the title of the source article.
     * @param target the title of the target article.
     * @param apiUrlText  the Wikipedia API access URL text.
     * @param out         the output stream to write the progress to.
     * @return the shortest path.
     */
    @Override
    public List<String> search(String source,
                               String target, 
                               String apiUrlText, 
                               PrintStream out) {
        this.numberOfExpandedNodes = 0;
        this.duration = 0L;

        if (source.equals(target)) {
            return new ArrayList<>(Arrays.asList(source));
        }

        this.duration = System.currentTimeMillis();
        TouchNodeHolder touchNodeHolder = new TouchNodeHolder(source, target);

        ForwardThread forwardThread = new ForwardThread(source,
                                                        apiUrlText,
                                                        touchNodeHolder,
                                                        out);

        BackwardThread backwardThread = new BackwardThread(target,
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

        List<String> path = touchNodeHolder.constructPath();
        this.numberOfExpandedNodes = forwardThread.getNumberOfExpandedNodes() +
                                    backwardThread.getNumberOfExpandedNodes();

        this.duration = System.currentTimeMillis() - this.duration;
        return path;  
    }

    private static class ForwardThread extends Thread {

        private final Deque<String> QUEUE = new ArrayDeque<>();
        private final Map<String, String> PARENTS = new ConcurrentHashMap<>();
        private final Map<String, Integer> DISTANCE = new ConcurrentHashMap<>();
        private final TouchNodeHolder touchNodeHolder;
        private final String apiUrlText;
        private final PrintStream out;
        private volatile boolean exit;
        private int numberOfExpandedNodes;

        ForwardThread(String sourceTitle, 
                      String apiUrlText,
                      TouchNodeHolder touchNodeHolder,
                      PrintStream out) {
            this.apiUrlText = apiUrlText;
            this.touchNodeHolder = touchNodeHolder;
            this.out = out;

            QUEUE.add(sourceTitle);
            PARENTS.put(sourceTitle, PARENT_MAP_END_TOKEN);
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

        int getNumberOfExpandedNodes() {
            return this.numberOfExpandedNodes;
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

                numberOfExpandedNodes++;

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
        private final TouchNodeHolder touchNodeHolder;
        private final String apiUrlText;
        private final PrintStream out;
        private volatile boolean exit;
        private int numberOfExpandedNodes;

        BackwardThread(String targetTitle, 
                       String apiUrlText,
                       TouchNodeHolder touchNodeHolder,
                       PrintStream out) {
            this.apiUrlText = apiUrlText;
            this.touchNodeHolder = touchNodeHolder;
            this.out = out;

            QUEUE.add(targetTitle);
            PARENTS.put(targetTitle, PARENT_MAP_END_TOKEN);
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

        int getNumberOfExpandedNodes() {
            return this.numberOfExpandedNodes;
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

                numberOfExpandedNodes++;

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
        private final String source;
        private final String target;
        private volatile String touchNode;
        private volatile int bestDistanceSoFar = Integer.MAX_VALUE;

        TouchNodeHolder(String source, String target) {
            this.source = source;
            this.target = target;
        }

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
            Map<String, String> forwardParents;
            Map<String, String> backwardParents;

            forwardParents  = new HashMap<>(forwardThread .getParentMap());
            backwardParents = new HashMap<>(backwardThread.getParentMap()); 

            forwardParents .put(source, null);
            backwardParents.put(target, null);

            return tracebackPath(touchNode, 
                                 forwardParents,
                                 backwardParents);
        }
    }
}
