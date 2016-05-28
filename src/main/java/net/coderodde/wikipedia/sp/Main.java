package net.coderodde.wikipedia.sp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;

/**
 * This class implements a command line program for finding a shortest path from
 * a source Wikipedia article to a target article.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (May 28, 2016)
 */
public class Main {
    
    private static final String URL_FORMAT = 
            "https://en.wikipedia.org/w/api.php" +
            "?action=query" +
            "&titles=%s" + 
            "&prop=links" + 
            "&pllimit=max" + 
            "&format=json";
    
    public static void main(String[] args) throws IOException {
        String jsonText = 
                IOUtils.toString(new URL(getURLByTitle("Disc_jockey")), 
                                 Charset.forName("UTF-8"));
        
        List<String> linkNameList = extractLinkNames(jsonText);
        
        int i = 1;
        
        for (String linkName : linkNameList) {
            System.out.printf("%3d: %s\n", i++, linkName);
        }
    }
    
    private static String getURLByTitle(String title) {
        return String.format(URL_FORMAT, title);
    }
    
    private static List<String> extractLinkNames(final String jsonText) {
        List<String> linkNameList = new ArrayList<>();
        JsonObject root  = new JsonParser().parse(jsonText).getAsJsonObject();
        
        System.out.println(root.has("query"));
        
        JsonObject queryObject  = root.get("query").getAsJsonObject();
        JsonObject pagesObject  = queryObject.get("pages").getAsJsonObject();
        JsonObject mainObject   = pagesObject.entrySet()
                                             .iterator()
                                             .next()
                                             .getValue()
                                             .getAsJsonObject();
        JsonArray linkNameArray = mainObject.get("links").getAsJsonArray();
        
        System.out.println("Yeah");
        System.out.println(linkNameArray);
        
        linkNameArray.forEach((element) -> {
            int namespace = element.getAsJsonObject().get("ns").getAsInt();
            
            if (namespace == 0) {
                String title = element.getAsJsonObject()
                                      .get("title")
                                      .getAsString();
                
                linkNameList.add(title);
            }
        });
        
        return linkNameList;
    }
}
