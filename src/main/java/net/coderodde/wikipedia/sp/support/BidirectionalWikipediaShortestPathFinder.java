package net.coderodde.wikipedia.sp.support;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Searches for the shortest path from the Wikipedia article with the title
     * {@code sourceTitle} to the article with the title {@code targetTitle}.
     * The algorithm is a bidirectional breadth-first search.
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
        this.numberOfExpandedNodes = 0;
        this.duration = 0L;

        if (sourceTitle.equals(targetTitle)) {
            return new ArrayList<>(Arrays.asList(sourceTitle));
        }

        this.duration = System.currentTimeMillis();

        Deque<String> QUEUEA = new ArrayDeque<>();
        Deque<String> QUEUEB = new ArrayDeque<>();

        Map<String, String> PARENTSA = new HashMap<>();
        Map<String, String> PARENTSB = new HashMap<>();

        Map<String, Integer> DISTANCEA = new HashMap<>();
        Map<String, Integer> DISTANCEB = new HashMap<>();

        String touchNode = null;
        int bestDistanceSoFar = Integer.MAX_VALUE;

        QUEUEA.add(sourceTitle);
        QUEUEB.add(targetTitle);

        PARENTSA.put(sourceTitle, null);
        PARENTSB.put(targetTitle, null);

        DISTANCEA.put(sourceTitle, 0);
        DISTANCEB.put(targetTitle, 0);

        while (!QUEUEA.isEmpty() && !QUEUEB.isEmpty()) {
            if (touchNode != null) {
                int distanceFromSource = DISTANCEA.get(QUEUEA.getFirst());
                int distanceFromTarget = DISTANCEB.get(QUEUEB.getFirst());

                if (bestDistanceSoFar < distanceFromSource + 
                                        distanceFromTarget) {
                    List<String> path = tracebackPath(touchNode, 
                                                      PARENTSA,
                                                      PARENTSB);
                    this.duration = System.currentTimeMillis() - this.duration;
                    return path;
                }
            }

            if (DISTANCEA.size() < DISTANCEB.size()) {
                String current = QUEUEA.removeFirst();

                if (out != null) {
                    out.println("Forward:  " + current);
                }

                if (PARENTSB.containsKey(current) 
                        && bestDistanceSoFar > DISTANCEA.get(current) +
                                               DISTANCEB.get(current)) {
                    bestDistanceSoFar = DISTANCEA.get(current) +
                                        DISTANCEB.get(current);
                    touchNode = current;
                }

                numberOfExpandedNodes++;

                for (String child : getChildArticles(apiUrlText, current)) {
                    if (!PARENTSA.containsKey(child)) {
                        PARENTSA.put(child, current);
                        DISTANCEA.put(child, DISTANCEA.get(current) + 1);
                        QUEUEA.addLast(child);
                    }
                }
            } else {
                String current = QUEUEB.removeFirst();

                if (out != null) {
                    out.println("Backward: " + current);
                }

                if (PARENTSA.containsKey(current) 
                        && bestDistanceSoFar > DISTANCEA.get(current) + 
                                               DISTANCEB.get(current)) {
                    bestDistanceSoFar = DISTANCEA.get(current) +
                                        DISTANCEB.get(current);
                    touchNode = current;
                }

                numberOfExpandedNodes++;

                for (String parent : getParentArticles(apiUrlText, current)) {
                    if (!PARENTSB.containsKey(parent)) {
                        PARENTSB.put(parent, current);
                        DISTANCEB.put(parent, DISTANCEB.get(current) + 1);
                        QUEUEB.addLast(parent);
                    }
                }
            }
        }

        this.duration = System.currentTimeMillis() - this.duration;
        return new ArrayList<>();
    }
}
