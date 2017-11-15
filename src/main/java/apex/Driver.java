package main.java.apex;

import java.io.File;

/**
 * This is the main class and entry point for our program.
 */
public class Driver {
    public static void main(String[] args) {
        String path = args[0];
        File file = new File(path);
        Menu myMenu = new Menu();
        myMenu.showMenu(file);
    }
}
