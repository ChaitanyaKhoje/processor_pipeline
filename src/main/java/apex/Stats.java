package main.java.apex;

public class Stats {
    public static int cycle = 0;
    public static String stages;
    public static String memory;
    public static String registers;

    public static String printStats() {

        StringBuilder sb = new StringBuilder();
        sb.append("#####################################################");sb.append('\n');
        sb.append("No of cycles run: " + cycle);sb.append('\n');
        sb.append("#####################################################");sb.append('\n');
        sb.append("MEMORY: " + "\n" + memory);sb.append('\n');
        sb.append("#####################################################");sb.append('\n');
        sb.append("REGISTERS: " + "\n" + registers);sb.append('\n');
        sb.append("#####################################################");sb.append('\n');
        sb.append("STAGES: " + "\n" + stages);sb.append('\n');

        return sb.toString();
    }

    public static void clear() {
        cycle = 0;
        stages = "";
        memory = "";
        registers = "";
    }
}
