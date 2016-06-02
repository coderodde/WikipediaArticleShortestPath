package net.coderodde.wikipedia.sp;

import org.junit.Test;
import static org.junit.Assert.*;

public class WikipediaURLHandlerTest {

    private WikipediaURLHandler handler;
    
    @Test
    public void testValid() {
        handler = new WikipediaURLHandler("https://en.wikipedia.org/wiki/Funk");
        
        assertEquals("en.wikipedia.org", handler.getBasicURL());
        assertEquals("https://en.wikipedia.org/w/api.php", handler.getAPIURL());
        
        handler = 
                new WikipediaURLHandler("http://fi.wikipedia.org/wiki/Fankki");
        
        assertEquals("fi.wikipedia.org", handler.getBasicURL());
        assertEquals("https://fi.wikipedia.org/w/api.php", handler.getAPIURL());
        
        handler = 
                new WikipediaURLHandler("de.wikipedia.org/wiki/Das_Funk");
        
        assertEquals("de.wikipedia.org", handler.getBasicURL());
        assertEquals("https://de.wikipedia.org/w/api.php", handler.getAPIURL());
    }
        
    @Test(expected = IllegalArgumentException.class) 
    public void testMisspelledWikipedia() {
        new WikipediaURLHandler("en.wikpedia.org/wiki/Funk");
    }
    
    @Test(expected = IllegalArgumentException.class) 
    public void testMisspelledLanguageIdentifier() {
        new WikipediaURLHandler("e.wikipedia.org/wiki/Funk");
    }
    
    @Test(expected = IllegalArgumentException.class) 
    public void testMisspelledWikiDir() {
        new WikipediaURLHandler("e.wikipedia.org/wik/Funk");
    }
    
    @Test(expected = IllegalArgumentException.class) 
    public void testNoKeyword() {
        new WikipediaURLHandler("en.wikipedia.org/wik/");
    }
}
