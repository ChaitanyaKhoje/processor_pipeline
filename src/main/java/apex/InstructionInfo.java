package main.java.apex;

/**
 * This is a single instruction in the pipeline
 */
public class InstructionInfo {

    private int pc;
    private String instruction;
    private Source source1;
    private Source source2;
    private Source source3;
    private int resultValue;
    private boolean instructionDecoded; //  To check if the prior instruction is decoded.
    private boolean instructionExecuted;
    private boolean instructionMemory;
    private boolean instructionWriteback;
    private Register destinationRegister;
    private int targetMemoryAddress = 0;
    private int targetMemoryData = 0;
    private int returnAddress = 0;
    private InstructionType type;
    private FunctionUnitType functionUnitType;  // Set in InstructionDecoder
    private boolean isIQAllocated;

    public int getReturnAddress() {
        return returnAddress;
    }

    public void setReturnAddress(int returnAddress) {
        this.returnAddress = returnAddress;
    }

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

    public void resetIntructionStageFlags() {
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

    @Override
    public String toString() {
        //return instruction.equals("NOP") ? "Empty" : instruction;
        return instruction;
    }
}
