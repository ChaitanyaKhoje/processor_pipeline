package main.java.apex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PhysicalRegisterFile {

    public static ArrayList<PhysicalRegister> physicalRegistersList = new ArrayList<PhysicalRegister>();
    public static Map<String, PhysicalRegister> physicalRegisterMap = new HashMap<String, PhysicalRegister>();

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

    public static void setUnsetPhysicalRegister(Source register, boolean status) {
        if(register instanceof PhysicalRegister) {
            PhysicalRegister pr = (PhysicalRegister) register;
            pr.setStatus(status);
            physicalRegisterMap.put(pr.getName(), pr);
        }
    }

    public static PhysicalRegister getAvailablePhysicalRegister() {
        for (PhysicalRegister physicalRegister : PhysicalRegisterFile.physicalRegistersList) {
            if (!physicalRegister.isAllocated()) {
                return physicalRegister;
            }
        }
        return null;
    }

    public static PhysicalRegister getByAddress(int address) {
        for(PhysicalRegister physicalRegister: physicalRegistersList) {
            if(physicalRegister.getAddress() == address) return physicalRegister;
        }
        return null;
    }
}
