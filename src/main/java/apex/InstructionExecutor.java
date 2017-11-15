package main.java.apex;

import java.nio.channels.Pipe;

public class InstructionExecutor {
    /**
     * The Instruction Executor is responsible for arithmetic operations;
     * ADD, SUB, MUL, MOVC, AND, OR, EX-OR, and address calculations for LOAD & STORE.
     * This also includes operations for BNZ, BZ, JUMP, HALT.
     */
    private static int tempMemoryAddress;

    public static InstructionInfo execute(InstructionInfo instructionInfo) {
        if (instructionInfo == null) {
            return InstructionInfo.createNOPInstruction();
        }

        if (instructionInfo.getType() == InstructionType.NOP) {
            return instructionInfo;
        }

        InstructionType instructionType = instructionInfo.getType();

        switch (instructionType) {
            case ADD:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() + instructionInfo.getSource2().getValue());
                break;
            case SUB:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() - instructionInfo.getSource2().getValue());
                break;
            case MUL:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() * instructionInfo.getSource2().getValue());
                break;
            case DIV:
                int Source2 = instructionInfo.getSource2().getValue();
                if (Source2 != 0) {
                    instructionInfo.setResultValue(instructionInfo.getSource1().getValue() / Source2);
                } else {
                    Flags.DivideByZero = true;
                    break;
                }
                break;
            case MOVC:
                int tempLiteral = instructionInfo.getSource1().getValue();
                instructionInfo.setResultValue(tempLiteral + 0);
                break;
            case OR:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() | instructionInfo.getSource2().getValue());
                break;
            case EXOR:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() ^ instructionInfo.getSource2().getValue());
                break;
            case AND:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue() & instructionInfo.getSource2().getValue());
            case LOAD:
                /**  LOAD R3, R0, #8
                 *   Add contents of R0 and literal 8. The result gives us a memory location of-
                 *   -which the value is to be stored into R3.
                 */
                tempMemoryAddress = (instructionInfo.getSource1().getValue() + instructionInfo.getSource2().getValue());
                instructionInfo.setTargetMemoryAddress(tempMemoryAddress);
                break;
            case STORE:
                /**  STORE R2, R0, #4
                 *   Add contents of R0 and literal 4. The result gives us an address to which we write-
                 *   the value of R2.
                 */
                tempMemoryAddress = (instructionInfo.getSource2().getValue() + ((instructionInfo.getSource3().getValue())));
                instructionInfo.setTargetMemoryAddress(tempMemoryAddress);
                break;
            case BNZ:
                tempMemoryAddress = instructionInfo.getSource1().getValue();
                instructionInfo.setResultValue(instructionInfo.getPc() + tempMemoryAddress);
                break;
            case BZ:
                tempMemoryAddress = instructionInfo.getSource1().getValue();
                instructionInfo.setResultValue(instructionInfo.getPc() + tempMemoryAddress);
                break;
            case JUMP:
                instructionInfo.setResultValue(instructionInfo.getSource2().getValue()
                        + instructionInfo.getSource1().getValue());
                TransferControl.jump = true;
                break;
            case JAL:
                instructionInfo.setResultValue(instructionInfo.getSource1().getValue()
                        + instructionInfo.getSource2().getValue());
                //  The address of the instruction following the JAL is calculated;
                tempMemoryAddress = instructionInfo.getPc() + 4;
                instructionInfo.setReturnAddress(tempMemoryAddress);
                /** If JAL stalls in INTFU due to an instruction with higher preference(MUL or DIV) trying to enter MEM,
                 *  it will flush the instructions in F and DRF ONLY ONCE!
                 *  Which means, when JAL is stalled for the first time in INTFU, it will flush F and DRF.
                 *  In the next cycle, the calculated address to which a jump is to be performed is fetched.
                 *  This implies, F and DRF are released.
                 *  If by the time the newly fetched instruction reaches DRF and JAL is still stalled due to multiple
                 *  high priority instructions in MUL/DIV entering MEM, F and DRF are stalled again because INTFU is stalled.
                 *
                 *  To handle this condition;
                 *  We need a flag/counter that tells us that JAL has already flushed F and DRF once and that we should
                 *  now fetch new instructions even if JAL is stalled in INTFU.
                 *  IN SHORT: JAL FLUSHES ONLY ONCE!! (This also applies to BZ/BNZ/JUMP).
                 */
                TransferControl.jump = true;
            case HALT:
                /**
                 * We just have to break when the instruction is HALT. But there's a case that needs to be handled as
                 * shown below.
                 *
                 *  Fetch      :   (I13)  [HALT]
                    DRF        :   (I12)  [JAL R13,R14,#-4]
                    INTFU      :   [NOP]
                    MUL1       :   (I11)  [MUL R10,R5,R6]
                    MUL2       :   [NOP]
                    DIV1       :   [NOP]
                    DIV2       :   (I10)  [DIV R7,R2,R3]
                    DIV3       :   (I9)  [DIV R4,R2,R3]
                    DIV4       :   (I8)  [DIV R1,R2,R3]
                    MEM        :   [NOP]
                    WB         :   [NOP]

                    Consider this eg.
                    Here, when JAL reaches INTFU, it will flush F and DRF BUT our HALT flag is set. So the check made in
                    F stage for creating a NOP when this flag is set is satisfied. But the problem is that in the next cycle
                    for the above instruction, JAL will be stalled in INTFU and the cycle after that, we need to fetch new
                    instruction. This new instruction won't be fetch as the HALT flag is already set.
                    This flag needs to be unset to handle this situation.
                 */
                break;
        }
        instructionInfo.setInstructionExecuted(true);

        return instructionInfo;
    }
}