package main.java.apex;

public class MemoryOperator {

    public static InstructionInfo doMemoryOperations(InstructionInfo instructionInfo) {
        if (instructionInfo == null) {
            return InstructionInfo.createNOPInstruction();
        }
        if (instructionInfo.getType() == InstructionType.NOP) {
            return instructionInfo;
        }

        InstructionType instructionType = instructionInfo.getType();

        switch (instructionType) {
            case STORE:
                /**
                 *  STORE instruction stores the source value into the memory location computed in the EX stage.
                 */
                int tempTargetAddress = instructionInfo.getTargetMemoryAddress();   // Memory location to write to.
                int registerValue = instructionInfo.getSource1().getValue();        // The value to be written.
                DataMemory.data_array[tempTargetAddress] = registerValue;
                break;
            default:
                break;
        }
        instructionInfo.setInstructionMemory(true);
        return instructionInfo;
    }
}
