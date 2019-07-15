package main.java.apex;

public class RobSlot {

    private int result;
    private int clockCycle;
    private int archDestinationRegister;
    private boolean status;
    private InstructionInfo robInstruction = InstructionInfo.createNOPInstruction();

    public RobSlot(InstructionInfo instruction, int arfRegisterAddress) {
        status = false;
        this.archDestinationRegister = arfRegisterAddress;
        this.robInstruction = instruction;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public int getClockCycle() {
        return clockCycle;
    }

    public void setClockCycle(int clockCycle) {
        this.clockCycle = clockCycle;
    }

    public int getArchDestinationRegister() {
        return archDestinationRegister;
    }

    public void setArchDestinationRegister(int archDestinationRegister) {
        this.archDestinationRegister = archDestinationRegister;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public InstructionInfo getRobInstruction() {
        return robInstruction;
    }

    public void setRobInstruction(InstructionInfo robInstruction) {
        this.robInstruction = robInstruction;
    }
}
