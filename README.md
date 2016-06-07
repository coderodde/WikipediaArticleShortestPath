# WikipediaArticleShortestPath
Software for finding shortest paths between two Wikipedia articles.

### Usage
`java -jar WikipediaArticleShortestPath-1.6-jar-with-dependencies.jar [--no-output] [--parallel] SOURCE TARGET`

where 
- `SOURCE` is the source Wikipedia article title,
- `TARGET` is the target Wikipedia article title,
- `--no-output` removes the progress output,
- `--serial` runs the single-threaded search; if omitted, a parallel search is used.
