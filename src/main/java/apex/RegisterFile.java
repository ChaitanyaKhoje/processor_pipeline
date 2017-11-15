package main.java.apex;

import java.util.HashMap;
import java.util.Map;

public class RegisterFile {

    public static Map<String, Register> registerMap = new HashMap<String, Register>();

    public static String getRegisterValues() {
        StringBuilder sb = new StringBuilder();

        for(Map.Entry entry: registerMap.entrySet()) {
            sb.append(entry.getValue().toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static void setUnsetRegister(Source register, boolean status) {
        if(register instanceof Register) {
            Register r = (Register) register;
            r.setStatus(status);
            registerMap.put(r.getName(), r);
        }
    }
}
