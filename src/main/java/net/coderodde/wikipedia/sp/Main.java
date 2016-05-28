package net.coderodde.wikipedia.sp;

import java.io.IOException;
import java.util.List;

/**
 * This class implements a command line program for finding a shortest path from
 * a source Wikipedia article to a target article.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class Main {
   
    public static void main(String[] args) throws IOException {
        List<String> path = new PathFinder().findShortestPath("Helsinki", "Education_Index");
        path.forEach(System.out::println);
    }
}
