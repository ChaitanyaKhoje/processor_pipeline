package main.java.apex;

/**
 * This is data memory1 where each 4 consecutive locations in the array are occupied by a value.
 */
public class DataMemory {
    public static int[] data_array = new int[4000];

    public static String getMemoryValues() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < 3999; i = i + 4) {
            sb.append("mem[" + i/4 + "]: " + data_array[i] + " ");
            count ++;
            if (count == 10) {
                sb.append("\n");
                count = 0;
            }
        }
        return sb.toString();
    }
}
