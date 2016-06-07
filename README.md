# WikipediaArticleShortestPath
Software for finding shortest paths between two Wikipedia articles.

### Building
Change to the directory containing **`pom.xml`** of this project; then type **`mvn clean compile assembly:single`**, change to **`target`** and run **`java -jar File.jar ...`**

### Usage
`java -jar WikipediaArticleShortestPath-1.6-jar-with-dependencies.jar [--no-output] [--parallel] SOURCE TARGET`

where 
- `SOURCE` is the source Wikipedia article title,
- `TARGET` is the target Wikipedia article title,
- `--no-output` removes the progress output,
- `--serial` runs the single-threaded search; if omitted, a parallel search is used.
