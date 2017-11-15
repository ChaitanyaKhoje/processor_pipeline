package main.java.apex;

public class InstructionDecoder {

    /**
     * This method will take a InstructionInfo object and decode its string parameter
     * and populate it back with all its fields
     *
     * @param instructionInfo
     * @return decoded instruction info
     */
    public static InstructionInfo decode(InstructionInfo instructionInfo) {
        if (instructionInfo == null) {
            return InstructionInfo.createNOPInstruction();
        }
        // we need to clear the stage flags because BNZ will use the same instruction object whose values were set.
        if (instructionInfo.getType() == InstructionType.NOP) {
            return instructionInfo;
        }

        instructionInfo.resetIntructionStageFlags();
        // instructionString will be in format ADD R0, R1, R2 or ADD R0, R1, #10
        String instructionString = instructionInfo.getInstruction();
        String[] instructionTokens = instructionString.split(",| ", 2);
        InstructionType type = InstructionType.valueOf(instructionTokens[0]);

        if (type == InstructionType.HALT) {
            instructionInfo.setInstructionDecoded(true);
            instructionInfo.setType(InstructionType.HALT);
            Flags.HALT = true;
            return instructionInfo;
        }

        instructionInfo.setType(type);
        String[] operands = instructionTokens[1].replace(" ", "").split(",");

        // Set waiting register variables which will be used while data forwarding.




        instructionInfo.setInstructionDecoded(true);

        switch (type) {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case OR:
            case EXOR:
            case AND:
                instructionInfo = decodeAsm(instructionInfo, operands);
                break;
            case MOVC:
                instructionInfo = decodeMovc(instructionInfo, operands);
                break;
            case LOAD:
                instructionInfo = decodeLoad(instructionInfo, operands);
                break;
            case STORE:
                instructionInfo = decodeStore(instructionInfo, operands);
                break;
            case BZ:
            case BNZ:
                instructionInfo = decodeBnz(instructionInfo, operands);
                break;
            case JUMP:
                instructionInfo = decodeJump(instructionInfo, operands);
                break;
            case JAL:
                instructionInfo = decodeJAL(instructionInfo, operands);
        }
        return instructionInfo;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["R1", "R2", #4] or ["R1", "R2", "R3"]
     * @return returns decoded and populated instruction info
     */
    private static InstructionInfo decodeAsm(InstructionInfo instruction, String[] operands) {
        if (operands[0] != null) {
            Register destinationRegister = RegisterFile.registerMap.get(operands[0]);
            instruction.setDestinationRegister(destinationRegister);
        }

        if (operands[1] != null) {
            Register sourceRegister1 = RegisterFile.registerMap.get(operands[1]);
            instruction.setSource1(sourceRegister1);
        }

        if (operands[2] != null && operands[2].startsWith("#")) {
            int value = Integer.parseInt(operands[2].substring(1));
            Source literal = new Source(value);
            instruction.setSource2(literal);
        } else {
            Register sourceRegister2 = RegisterFile.registerMap.get(operands[2]);
            instruction.setSource2(sourceRegister2);
        }

        /**
         * Setting up the FU for the instruction.
         * This information is used in IQ.
         */
        if (instruction.getType() != InstructionType.NOP
                && instruction.getType() != InstructionType.MUL
                && instruction.getType() != InstructionType.DIV) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        } else if (instruction.getType() != InstructionType.NOP
                && instruction.getType() == InstructionType.MUL) {
            instruction.setFunctionUnitType(FunctionUnitType.MulFU);
        } else if (instruction.getType() != InstructionType.NOP
                && instruction.getType() == InstructionType.DIV) {
            instruction.setFunctionUnitType(FunctionUnitType.DivFU);
        }

        return instruction;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["R0", #2]
     * @return returns decoded and populated instruction info
     * Note that the register to which we are writing, destn register, will be stalled until WB stage.
     * Update INVALID for that register.
     */
    private static InstructionInfo decodeMovc(InstructionInfo instruction, String[] operands) {

        if (operands[0] != null) {
            Register destinationRegister = RegisterFile.registerMap.get(operands[0]);
            instruction.setDestinationRegister(destinationRegister);
        }

        if (operands[1] != null) {
            int value = Integer.parseInt(operands[1].substring(1));
            Source literal = new Source(value);
            instruction.setSource1(literal);
        }

        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["R0", "R1", "#2"]
     * @return returns decoded and populated instruction info
     */
    private static InstructionInfo decodeLoad(InstructionInfo instruction, String[] operands) {
        if (operands[0] != null) {
            Register destinationRegister = RegisterFile.registerMap.get(operands[0]);
            instruction.setDestinationRegister(destinationRegister);
        }

        if (operands[1] != null) {
            Register sourceRegister1 = RegisterFile.registerMap.get(operands[1]);
            instruction.setSource1(sourceRegister1);
        }

        if (operands[2] != null) {
            int value = Integer.parseInt(operands[2].substring(1));
            Source literal = new Source(value);
            instruction.setSource2(literal);
        }
        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["R0", "R1", "#2"]
     * @return returns decoded and populated instruction info
     * Note that the STORE instruction's first operand is the source.
     * The second operand is used calculate the destination address
     * by adding its contents with the third operand, a literal.
     */
    private static InstructionInfo decodeStore(InstructionInfo instruction, String[] operands) {
        if (operands[0] != null) {
            Register sourceRegister1 = RegisterFile.registerMap.get(operands[0]);
            instruction.setSource1(sourceRegister1);
        }
        if (operands[1] != null) {
            Register sourceRegister2 = RegisterFile.registerMap.get(operands[1]);
            instruction.setSource2(sourceRegister2);
        }
        if (operands[2] != null) {
            int value = Integer.parseInt(operands[2].substring(1));
            Source literal = new Source(value);
            instruction.setSource3(literal);
        }
        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["#2"]
     * @return returns decoded and populated instruction info
     */
    private static InstructionInfo decodeBnz(InstructionInfo instruction, String[] operands) {
        if (operands[0] != null) {
            int value = Integer.parseInt(operands[0].substring(1));
            Source literal = new Source(value);
            instruction.setSource1(literal);
        }
        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    /**
     * @param instruction instruction info we need to populate.
     * @param operands    Operands are in the format ["#2"]
     * @return returns decoded and populated instruction info
     */
    private static InstructionInfo decodeJump(InstructionInfo instruction, String[] operands) {
        if (operands[0] != null) {
            Register sourceRegister = RegisterFile.registerMap.get(operands[0]);
            instruction.setSource1(sourceRegister);
        }

        if (operands[1] != null) {
            int value = Integer.parseInt(operands[1].substring(1));
            Source literal = new Source(value);
            instruction.setSource2(literal);
        }
        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    private static InstructionInfo decodeJAL(InstructionInfo instruction, String[] operands) {

        if (operands[0] != null) {
            Register destinationRegister = RegisterFile.registerMap.get(operands[0]);
            instruction.setDestinationRegister(destinationRegister);
        }

        if (operands[1] != null) {
            Register sourceRegister = RegisterFile.registerMap.get(operands[1]);
            instruction.setSource1(sourceRegister);
        }

        if (operands[2] != null) {
            int value = Integer.parseInt(operands[2].substring(1));
            Source literal = new Source(value);
            instruction.setSource2(literal);
        }
        if(instruction.getType() != InstructionType.NOP) {
            instruction.setFunctionUnitType(FunctionUnitType.IntFU);
        }
        return instruction;
    }

    public static void setRegisters(InstructionInfo outputInstruction) {
        if (outputInstruction != null) {
            Register r = outputInstruction.getDestinationRegister();

            if (r != null) {
                r.setStatus(true);
                RegisterFile.setUnsetRegister(r, true);
            }
        }
    }
}

