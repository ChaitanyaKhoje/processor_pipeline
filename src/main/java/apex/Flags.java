package main.java.apex;

/**
 * Similar to PSW flags, but has additional flags used for different purposes of managing stages
 */
public class Flags {

    public static boolean ZERO;
    public static boolean CARRY;
    public static boolean NEGATIVE;
    public static boolean HALT;
    public static boolean EXIT;
    public static boolean DivideByZero;

    public static void resetFlags() {
        ZERO = false;
        CARRY = false;
        NEGATIVE = false;
        HALT = false;
        EXIT = false;
    }
}

