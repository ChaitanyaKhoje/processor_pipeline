package main.java.apex;

public class InstructionWriter {

    public static InstructionInfo writeToDestination(InstructionInfo instructionInfo) {
        if (instructionInfo == null) {
            return InstructionInfo.createNOPInstruction();
        }
        if (instructionInfo.getType() == InstructionType.NOP) {
            return instructionInfo;
        }

        InstructionType instructionType = instructionInfo.getType();

        switch (instructionType) {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
                instructionInfo.getDestinationRegister().setValue(instructionInfo.getResultValue());
                RegisterFile.registerMap
                        .put(instructionInfo.getDestinationRegister().getName(), instructionInfo.getDestinationRegister());
                break;
            case MOVC:
                instructionInfo.getDestinationRegister().setValue(instructionInfo.getResultValue());
                RegisterFile.registerMap
                        .put(instructionInfo.getDestinationRegister().getName(), instructionInfo.getDestinationRegister());
                break;
            case LOAD:
                instructionInfo.getDestinationRegister().setValue(DataMemory.data_array[instructionInfo.getTargetMemoryAddress()]);
                break;
            case STORE:
                //  Do nothing.
                break;
            case BNZ:
                break;
            case HALT:
                break;
            case JAL:
                instructionInfo.getDestinationRegister().setValue(instructionInfo.getReturnAddress());
        }

        instructionInfo.setInstructionWriteback(true);
        return instructionInfo;
    }

    public static void resetRegisters(InstructionInfo instructionInfo) {
        RegisterFile.setUnsetRegister(instructionInfo.getDestinationRegister(), false);
    }
}
