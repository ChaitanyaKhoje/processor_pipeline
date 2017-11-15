package main.java.apex;

import java.io.File;
import java.util.Scanner;

public class Menu {


    public void showMenu(File path) {

        Scanner sc = new Scanner(System.in);    //  Scanner initialized.
        PipelineManager pipelineManager = new PipelineManager();

        //  The do-while loop checks if the user wants to continue or not.
        //  switch case contains the simulator commands.

        while (true) {
            System.out.println('\n');
            System.out.println("1. Initialize");
            System.out.println("2. Simulate");
            System.out.println("3. Display");
            System.out.println("4. Exit");
            System.out.println('\n');
            System.out.println("Please enter your choice...");
            int simCommand = sc.nextInt();
            System.out.println("-----------------------------------------------------");
            switch (simCommand) {
                case 1:
//                    System.out.println("Please enter the full path along with the input file name...");
//                    path = sc.next();
//                    File file = new File(path);
                    pipelineManager.initialize(path);
                    System.out.println("Instruction pipeline initialized.");
                    break;
                case 2:
                    System.out.println("Please enter the desired number of cycles: ");
                    int cycles = sc.nextInt();
                    pipelineManager.simulate(cycles);
                    System.out.println('\n');
                    System.out.println("Simulation complete!");
                    break;
                case 3:
                    System.out.println("Display stats");
                    pipelineManager.displayStats();
                    //  Display stage-wise information.
                    break;
                case 4:
                    System.exit(0);
                default:
                    System.out.println("Invalid choice!");
                    break;
            }
            System.out.println("-----------------------------------------------------");
        }
    }
}
