package net.coderodde.wikipedia.sp;

import java.io.PrintStream;
import java.util.List;

/**
 * This class contains various utilities.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 (Jul 26, 2016)
 */
public class Miscellanea {

    public static String nth(final int number) {
        return number == 1 ? "" : "s";
    }
    
    public static <T> T removeLast(final List<T> list) {
        return list.remove(list.size() - 1);
    }
    
    public static void print(final PrintStream out, final String text) {
        if (out != null) {
            out.println(text);
        }
    }
    
    public static int parseInt(final String integerString) {
        try {
            return Integer.parseInt(integerString);
        } catch (final NumberFormatException ex) {
            throw new InvalidCommandLineOptionsException(
                    "Cannot convert \"" + integerString + "\" to an integer.");
        }
    }
}
