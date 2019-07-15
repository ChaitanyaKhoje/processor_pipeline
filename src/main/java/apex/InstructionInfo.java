package main.java.apex;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a single instruction in the pipeline
 */
public class InstructionInfo {

    private int pc;
    private String instruction;
    private Source source1;
    private Source source2;
    private Source source3;

    // variables to hold addresses of arch registers which will be replaced by physical register addresses
    private int src1PAVal;
    private int src2PAVal;
    private int src3PAVal;
    private int destPAVal;

    private int resultValue;
    private int targetMemoryAddress = 0;
    private int targetMemoryData = 0;
    private int returnAddress = 0;
    private boolean instructionDecoded; //  To check if the prior instruction is decoded.
    private boolean instructionExecuted;
    private boolean instructionMemory;
    private boolean instructionWriteback;
    private boolean isIQAllocated;              // Used for Issue Queue
    private Register destinationRegister;
    private PhysicalRegister destPhysicalRegister;
    private InstructionType type;
    private FunctionUnitType functionUnitType;  // Set in InstructionDecoder
    private int LSQIndex;
    private RegisterType registerType;
    // Ties to decide which instruction to issue to a specific FU are broken by issuing the earliest dispatched instruction
    // that is eligible for issue. To do this, a cycle count for each IQ entry is maintained.
    private int cycleCount;

    public static InstructionInfo createNOPInstruction() {
        InstructionInfo instructionInfo = new InstructionInfo();
        instructionInfo.setInstruction("Empty");
        instructionInfo.setType(InstructionType.NOP);
        instructionInfo.setInstructionDecoded(true);
        instructionInfo.setInstructionExecuted(true);
        instructionInfo.setInstructionMemory(true);
        instructionInfo.setInstructionWriteback(true);
        return instructionInfo;
    }

    public void resetInstructionStageFlags() {
        this.instructionDecoded = false;
        this.instructionExecuted = false;
        this.instructionMemory = false;
        this.instructionWriteback = false;
    }

    public boolean isArithematic() {
        if (this.getType().equals(InstructionType.ADD)
                || this.getType().equals(InstructionType.MUL)
                || this.getType().equals(InstructionType.DIV)
                || this.getType().equals(InstructionType.SUB))
        //   || this.getType().equals(InstructionType.EXOR)
        //   || this.getType().equals(InstructionType.OR)
        //   || this.getType().equals(InstructionType.AND))
        {
            return true;
        } else {
            return false;
        }
    }

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Source getSource1() {
        return source1;
    }

    public void setSource1(Source source1) {
        this.source1 = source1;
    }

    public Source getSource2() {
        return source2;
    }

    public void setSource2(Source source2) {
        this.source2 = source2;
    }

    public Source getSource3() {
        return source3;
    }

    public void setSource3(Source source3) {
        this.source3 = source3;
    }

    public Register getDestinationRegister() {
        return destinationRegister;
    }

    public void setDestinationRegister(Register destinationRegister) {
        this.destinationRegister = destinationRegister;
    }

    public int getTargetMemoryAddress() {
        return targetMemoryAddress;
    }

    public void setTargetMemoryAddress(int targetMemoryAddress) {
        this.targetMemoryAddress = targetMemoryAddress;
    }

    public int getTargetMemoryData() {
        return targetMemoryData;
    }

    public void setTargetMemoryData(int targetMemoryData) {
        this.targetMemoryData = targetMemoryData;
    }

    public InstructionType getType() {
        return type;
    }

    public void setType(InstructionType type) {
        this.type = type;
    }

    public int getResultValue() {
        return resultValue;
    }

    public void setResultValue(int resultValue) {
        this.resultValue = resultValue;
    }

    public boolean isInstructionDecoded() {
        return instructionDecoded;
    }

    public void setInstructionDecoded(boolean instructionDecoded) {
        this.instructionDecoded = instructionDecoded;
    }

    public boolean isInstructionExecuted() {
        return instructionExecuted;
    }

    public void setInstructionExecuted(boolean instructionExecuted) {
        this.instructionExecuted = instructionExecuted;
    }

    public boolean isInstructionMemory() {
        return instructionMemory;
    }

    public void setInstructionMemory(boolean instructionMemory) {
        this.instructionMemory = instructionMemory;
    }

    public boolean isInstructionWriteback() {
        return instructionWriteback;
    }

    public void setInstructionWriteback(boolean instructionWriteback) {
        this.instructionWriteback = instructionWriteback;
    }

    public FunctionUnitType getFunctionUnitType() {
        return functionUnitType;
    }

    public void setFunctionUnitType(FunctionUnitType functionUnitType) {
        this.functionUnitType = functionUnitType;
    }

    public boolean isIQAllocated() {
        return isIQAllocated;
    }

    public void setIQAllocated(boolean IQAllocated) {
        isIQAllocated = IQAllocated;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public int getReturnAddress() {
        return returnAddress;
    }

    public void setReturnAddress(int returnAddress) {
        this.returnAddress = returnAddress;
    }

    public int getLSQIndex() {
        return LSQIndex;
    }

    public void setLSQIndex(int LSQIndex) {
        this.LSQIndex = LSQIndex;
    }

    public int getSrc1PAVal() {
        return src1PAVal;
    }

    public void setSrc1PAVal(int src1PAVal) {
        this.src1PAVal = src1PAVal;
    }

    public int getSrc2PAVal() {
        return src2PAVal;
    }

    public void setSrc2PAVal(int src2PAVal) {
        this.src2PAVal = src2PAVal;
    }

    public int getSrc3PAVal() {
        return src3PAVal;
    }

    public void setSrc3PAVal(int src3PAVal) {
        this.src3PAVal = src3PAVal;
    }

    public int getDestPAVal() {
        return destPAVal;
    }

    public void setDestPAVal(int destPAVal) {
        this.destPAVal = destPAVal;
    }

    public RegisterType getRegisterType() {
        return registerType;
    }

    public void setRegisterType(RegisterType registerType) {
        this.registerType = registerType;
    }

    public PhysicalRegister getDestPhysicalRegister() {
        return destPhysicalRegister;
    }

    public void setDestPhysicalRegister(PhysicalRegister destPhysicalRegister) {
        this.destPhysicalRegister = destPhysicalRegister;
    }

    public List<Register> getAllRegisters() {
        List<Register> sources = new ArrayList<>();
        if(source1 instanceof Register) {
            sources.add((Register) source1);
        }
        if(source2 instanceof Register) {
            sources.add((Register) source2);
        }
        if(source3 instanceof Register) {
            sources.add((Register) source3);
        }
        sources.add(destinationRegister);
        return sources;
    }

    public List<Register> getAllSourceRegisters() {
        List<Register> sources = new ArrayList<>();
        if(source1 instanceof Register) {
            sources.add((Register) source1);
        }
        if(source2 instanceof Register) {
            sources.add((Register) source2);
        }
        if(source3 instanceof Register) {
            sources.add((Register) source3);
        }
        return sources;
    }

    @Override
    public String toString() {
        //return instruction.equals("NOP") ? "Empty" : instruction;
        return instruction;
    }
}
