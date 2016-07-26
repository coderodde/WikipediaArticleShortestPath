package net.coderodde.wikipedia.sp;

/**
 * This class defines the class thrown on bad command-line arguments.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6
 */
public class InvalidCommandLineOptions extends RuntimeException {
    
    public InvalidCommandLineOptions(final String message) {
        super(message);
    }
}
