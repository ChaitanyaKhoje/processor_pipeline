package main.java.apex;

import java.util.ArrayList;
import java.util.List;

public class LoadStoreQueueElement {

    private InstructionType instructionType;         // LOAD/STORE
    private int computedMemoryAddress;      // Passed from IntFU
    private Register destinationRegister;   // Used for LOAD instruction
    private int valueToBeStored;            // Passed through forwarding
    private InstructionInfo instruction = InstructionInfo.createNOPInstruction();

    //public static List<LoadStoreQueueElement> LSQList = new ArrayList<LoadStoreQueueElement>();

    public LoadStoreQueueElement() {}
    public LoadStoreQueueElement(InstructionType instructionType, int computedMemoryAddress,
                                 Register destinationRegister, int valueToBeStored) {
        this.instructionType = instructionType;
        this.computedMemoryAddress = computedMemoryAddress;
        this.destinationRegister = destinationRegister;
        this.valueToBeStored = valueToBeStored;
    }

    public InstructionType getInstructionType() {
        return instructionType;
    }

    public void setInstructionType(InstructionType instructionType) {
        this.instructionType = instructionType;
    }

    public int getComputedMemoryAddress() {
        return computedMemoryAddress;
    }

    public void setComputedMemoryAddress(int computedMemoryAddress) {
        this.computedMemoryAddress = computedMemoryAddress;
    }

    public Register getDestinationRegister() {
        return destinationRegister;
    }

    public void setDestinationRegister(Register destinationRegister) {
        this.destinationRegister = destinationRegister;
    }

    public int getValueToBeStored() {
        return valueToBeStored;
    }

    public void setValueToBeStored(int valueToBeStored) {
        this.valueToBeStored = valueToBeStored;
    }

    public InstructionInfo getInstruction() {
        return instruction;
    }

    public void setInstruction(InstructionInfo instruction) {
        this.instruction = instruction;
    }
}
