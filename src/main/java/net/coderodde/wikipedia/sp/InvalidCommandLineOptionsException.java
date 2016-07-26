package net.coderodde.wikipedia.sp;

/**
 * This class defines the class thrown on bad command-line arguments.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6
 */
public class InvalidCommandLineOptionsException extends RuntimeException {
    
    public InvalidCommandLineOptionsException(final String message) {
        super(message);
    }
}
