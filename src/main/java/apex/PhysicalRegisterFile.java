package main.java.apex;

import java.util.ArrayList;
import java.util.Map;

public class PhysicalRegisterFile {

    public static ArrayList<PhysicalRegister> physicalRegistersList = new ArrayList<PhysicalRegister>();

    public PhysicalRegisterFile() {
    }

    public static String getPhysicalRegisterValues() {
        StringBuilder sb = new StringBuilder();

        for(PhysicalRegister physicalRegister: physicalRegistersList) {
            sb.append(physicalRegister.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}
