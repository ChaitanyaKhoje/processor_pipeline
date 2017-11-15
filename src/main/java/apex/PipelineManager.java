package main.java.apex;

import java.io.File;
import java.util.*;

public class PipelineManager {

    public static int programCounter;
    public static Stage fetch;
    public static Stage decode;
    public static Stage execute;
    public static Stage executeMul1;
    public static Stage executeMul2;
    public static Stage executeDiv1;
    public static Stage executeDiv2;
    public static Stage executeDiv3;
    public static Stage executeDiv4;
    public static Stage memory;
    public static Stage writeback;

    public static boolean isFlushed;    // Used for handling stalled BZ/BNZ/JAL/JUMP in EX stage (flush only once!).
    public boolean multipleMULs = true; // Explanation in setStallForStages.


    public static Map<Integer, InstructionInfo> instructionInfoMap = new HashMap<Integer, InstructionInfo>();
    public static Map<Integer, Integer> instructionPrefixInDisplayMap = new HashMap<Integer, Integer>();       // A map used to get the line number of the instruction while displaying it in the stages.
    public static Map<Integer, Integer> renameTableMap = new HashMap<Integer, Integer>();       // A map that tells us which architectural register is assigned to which physical register.
    public static List<InstructionInfo> issueQueue = new ArrayList<InstructionInfo>();
    private PipelineInitializer initializer = new PipelineInitializer();

    /**
     * Initialize the pipeline. Resets stats, flags, cycles, registers, memory, etc.
     */
    public void initialize(File file) {
        initializer.initialize(file);
    }

    /**
     * Simulates the pipeline for given number of cycles.
     */
    public void simulate(int cycles) {

        for (int i = 0; i < cycles; i++) {
            System.out.println("-------------------------------------------------");
            System.out.println("Cycle: " + (i + 1));

            handleJUMPJAL();
            setStallForStages();
            doWriteback();
            doMemory();
            doDiv4();
            doDiv3();
            doDiv2();
            doDiv1();
            // TODO: 27/10/2017 How to handle division by zero better?
            if (Flags.DivideByZero) {
                System.out.println("Division by zero occurred!");
                break;
            }
            doMul2();
            doMul1();
            doExecute();
            doDecode();
            doFetch();
            handleBzBnz();

            // clean up because we could have again added after cleaning up in int alu in execute step since we
            // reset the pc.
            if ((execute.getInstruction().getType().equals(InstructionType.JUMP)
                    || execute.getInstruction().getType().equals(InstructionType.JAL)
                    || execute.getInstruction().getType().equals(InstructionType.BZ)
                    || execute.getInstruction().getType().equals(InstructionType.BNZ))) {
                if (TransferControl.jump && !isFlushed) {
                    flushInstructionsBeforeExecute();
                    isFlushed = true;   // Explanation given in JAL code segment in InstructionExecutor
                    Flags.HALT = false; // Explanation given in HALT code segment in InstructionExecutor
                }
            }
            String stageOutput = display();
            Stats.stages = Stats.stages + stageOutput;
            Stats.cycle = i + 1;
            if (Flags.EXIT) break;
        }
    }

    private void doFetch() {

        if (fetch.isStalled()) return;
        InstructionInfo newInstruction = instructionInfoMap.get(programCounter);
        if (newInstruction == null || Flags.HALT) {
            newInstruction = InstructionInfo.createNOPInstruction();
        }
        fetch.setInstruction(newInstruction);
        programCounter = programCounter + 4;
    }

    /**
     *  The decode stage will now contain the logic for register renaming as well.
     */
    public void doDecode() {
        InstructionInfo instruction;
        if (fetch.isStalled()) {
            if (!decode.isStalled()) {
                instruction = InstructionInfo.createNOPInstruction();
                decode.setInstruction(instruction);
            }
        } else {
            instruction = fetch.getInstruction();
            instruction = InstructionDecoder.decode(instruction);
            decode.setInstruction(instruction);

            // Register Renaming
            renameRegisters(instruction);
        }
    }

    public void doExecute() {
        InstructionInfo instruction = decode.getInstruction();
        if (!decode.isStalled()
                && !InstructionType.MUL.equals(instruction.getType())
                && !InstructionType.HALT.equals(instruction.getType())
                && !InstructionType.DIV.equals(instruction.getType())
                && (!execute.isStalled() || execute.getInstruction().getType() == InstructionType.NOP)) {

            InstructionDecoder.setRegisters(instruction);
            if (!instruction.isInstructionExecuted()) {
                InstructionExecutor.execute(instruction);
            }
            if (instruction.isArithematic()) {
                BzLock.IS_BUSY = true;
                BzLock.INSTRUCTION = instruction;
            }
            execute.setInstruction(instruction);
        } else if (!execute.isStalled()) {
            instruction = InstructionInfo.createNOPInstruction();
            execute.setInstruction(instruction);
        }
    }

    public void doMul1() {
        InstructionInfo instruction = decode.getInstruction();

        if (instruction.isArithematic()) {
            BzLock.IS_BUSY = true;
            BzLock.INSTRUCTION = instruction;
        }

        if (!decode.isStalled() && (InstructionType.MUL.equals(instruction.getType()))) {

            instruction = InstructionExecutor.execute(instruction);
            InstructionDecoder.setRegisters(instruction);
            executeMul1.setInstruction(instruction);
        } else if(!executeMul1.isStalled()) {
            instruction = InstructionInfo.createNOPInstruction();
            executeMul1.setInstruction(instruction);
        }
    }

    public void doMul2() {
        InstructionInfo instruction;
        if (!executeMul1.isStalled()) {
            instruction = executeMul1.getInstruction();
            executeMul2.setInstruction(instruction);
        }
    }

    public void doDiv1(){
        InstructionInfo instruction = decode.getInstruction();

        if (instruction.isArithematic()) {
            BzLock.IS_BUSY = true;
            BzLock.INSTRUCTION = instruction;
        }

        if (!decode.isStalled()
            && InstructionType.DIV.equals(instruction.getType())
            || InstructionType.HALT.equals(instruction.getType())) {

            instruction = InstructionExecutor.execute(instruction);
            if (!Flags.DivideByZero) {                          //  Handling division by zero
                InstructionDecoder.setRegisters(instruction);
                executeDiv1.setInstruction(instruction);
            }
        } else {
            instruction = InstructionInfo.createNOPInstruction();
            executeDiv1.setInstruction(instruction);
        }
    }

    public void doDiv2() {
        InstructionInfo instruction;
        instruction = executeDiv1.getInstruction();
        executeDiv2.setInstruction(instruction);
    }

    public void doDiv3() {
        InstructionInfo instruction;
        instruction = executeDiv2.getInstruction();
        executeDiv3.setInstruction(instruction);
    }

    public void doDiv4() {
        InstructionInfo instruction;
        instruction = executeDiv3.getInstruction();
        executeDiv4.setInstruction(instruction);
    }

    public void doMemory() {

        /* NEW logic implies the priorities for using the MEM stage as follows:
           DU (highest), MULtiply FU, IntegerFU (lowest).
        */
        InstructionInfo instructionDiv = executeDiv4.getInstruction();
        InstructionInfo instructionMUL = executeMul2.getInstruction();

        if (instructionDiv != null && !instructionDiv.getType().equals(InstructionType.NOP)) {
            // There's a DIV instruction in the Div4 which needs to enter the MEM stage.
            instructionDiv = MemoryOperator.doMemoryOperations(instructionDiv);
            memory.setInstruction(instructionDiv);
        } else if (instructionMUL != null && !instructionMUL.getType().equals(InstructionType.NOP)) {
            // There's no DIV in Div4, MUL is given priority.
            instructionMUL = MemoryOperator.doMemoryOperations(instructionMUL);
            memory.setInstruction(instructionMUL);
        } else {
            // There's no DIV and MUL that need priority for MEM stage. Hence, IntFU instruction is given priority.
            InstructionInfo instructionIntFU = execute.getInstruction();
            instructionIntFU = MemoryOperator.doMemoryOperations(instructionIntFU);
            memory.setInstruction(instructionIntFU);
        }

        /** OLD logic, used for handling MUL */

        /*
        InstructionInfo instruction = executeMul2.getInstruction();
        if (instruction != null && !instruction.getType().equals(InstructionType.NOP)) {
            //instruction = InstructionWriter.writeToDestination(instruction);
            instruction = MemoryOperator.doMemoryOperations(instruction);
            memory.setInstruction(instruction);

        } else {
            instruction = execute.getInstruction();
            //instruction = InstructionWriter.writeToDestination(instruction);
            instruction = MemoryOperator.doMemoryOperations(instruction);
            memory.setInstruction(instruction);
        }
        */
    }

    public void doWriteback() {
        InstructionInfo instruction = memory.getInstruction();
        instruction = InstructionWriter.writeToDestination(instruction);
        writeback.setInstruction(instruction);
        InstructionWriter.resetRegisters(instruction);
        if (instruction.getType().equals(InstructionType.HALT)) {
            Flags.EXIT = true;
        }

        if (instruction.equals(BzLock.INSTRUCTION) && instruction.isArithematic()) {
            BzLock.IS_BUSY = false;
            BzLock.INSTRUCTION = null;
            Flags.ZERO = instruction.getResultValue() == 0;
        }
    }

    private void handleJUMPJAL() {
        InstructionInfo jump = execute.getInstruction();
        if (jump.getType().equals(InstructionType.JUMP)
                ||jump.getType().equals(InstructionType.JAL)) {
            if (TransferControl.jump) {
                isFlushed = false;
                programCounter = execute.getInstruction().getResultValue();
                TransferControl.jump = false;
            }
        }
    }

    private void setStallForStages() {

        boolean registerDependency = false;

        decode.setStalled(false);
        fetch.setStalled(false);
        execute.setStalled(false);
        executeMul1.setStalled(false);
        executeMul2.setStalled(false);
        multipleMULs = false;

        registerDependency = checkRegisterDependency(false);

        // if there is register dependency, stall decode and fetch anyway.
        if (registerDependency) {
            decode.setStalled(true);
            fetch.setStalled(true);

            /** Forwarding
             If the address of the destination register of the instructions in the EX and MEM stage matches the
             address of source register of an instruction that is about to enter the EX stage, data forwarding is necessary.
             */

            List<Source> registersInDecode = decode.getAllSources();

            int count = 0;
            for(Source register: registersInDecode) {
                if (execute.getDestination() != null && execute.getInstruction().getType() != InstructionType.LOAD) {
                    if (execute.getDestination().equals(register)
                        && !execute.getDestination().equals(decode.getDestination())) {
                        register.setValue(execute.getInstruction().getResultValue());
                        ((Register) register).setForwarded(true);
                        Flags.ZERO = execute.getInstruction().getResultValue() == 0;
                        //System.out.println("Data forwarding for " + ((Register) register).getName() + " from IntFU performed!");
                        count++;
                        continue;
                    }
                }

                if (executeMul2.getDestination() != null) {
                    if(executeMul2.getDestination().equals(register)
                            && !executeMul2.getDestination().equals(decode.getDestination())) {
                        register.setValue(executeMul2.getInstruction().getResultValue());
                        count++;
                        ((Register) register).setForwarded(true);
                        Flags.ZERO = executeMul2.getInstruction().getResultValue() == 0;
                        //System.out.println("Data forwarding for " + ((Register) register).getName() + " from MulFU performed!");
                        continue;
                    }
                }
                if (executeDiv4.getDestination() != null) {
                    if(executeDiv4.getDestination().equals(register)
                            && !executeDiv4.getDestination().equals(decode.getDestination())) {
                        register.setValue(executeDiv4.getInstruction().getResultValue());
                        count++;
                        ((Register) register).setForwarded(true);
                        Flags.ZERO = executeDiv4.getInstruction().getResultValue() == 0;
                        //System.out.println("Data forwarding for " + ((Register) register).getName() + " from DivFU performed!");
                    }
                }
            }

            /**
             * Count is a variable that increments when a register value is forwarded to the decode stage.
             * If there are 2 source registers to be forwarded, and only 1 register is forwarded, the count doesn't match
             * and it goes to the OR condition where register dependency is checked again; if it returns false
             * it implies that the second register which was not forwarded to, was not the one setting the registerDependency
             * in the first place. The one which set it to true while starting setStallForStages is already forwarded and hence
             * all the sources are available and the instruction need not stall in the decode stage.
             *
             * In addition to this, we need to set the isForwarded flag of the forwarded register to false so that
             * later instructions will have a chance to set it again.
             */
            if(count == registersInDecode.size()
                    || !checkRegisterDependency(false)) {
                fetch.setStalled(false);
                decode.setStalled(false);

                for (Source register: registersInDecode) {
                    ((Register) register).setForwarded(false);
                }
            }
        }

        boolean bzStall = false;
        if (InstructionType.BNZ.equals(decode.getInstruction().getType())
                || InstructionType.BZ.equals(decode.getInstruction().getType())) {
            if (BzLock.IS_BUSY) {
                bzStall = true;
            }
        }

        if (bzStall) {
            decode.setStalled(true);
            fetch.setStalled(true);
        }

        //  Forward flags (overrides the above code; from bzstall=false;)

        /**
         *  This code segment handles flag forwarding for BZ BNZ
         *
         *  Example:
         *  Here, BNZ should stall in the next cycle and wait for DIV to set the flag in WB
         *
            Fetch      :   (I21)  [MUL R12,R5,R6]
            DRF        :   (I20)  [BNZ #32]
            INTFU      :   [NOP]
            MUL1       :   [NOP]
            MUL2       :   (I18)  [MUL R5,R9,R4]
            DIV1       :   (I19)  [DIV R13,R2,R3]
            DIV2       :   [NOP]
            DIV3       :   [NOP]
            DIV4       :   [NOP]
            MEM        :   [NOP]
            WB         :   [NOP]
         */
        if (decode.getInstruction().getType() == InstructionType.BZ
                || decode.getInstruction().getType() == InstructionType.BNZ) {

            InstructionInfo instruction = new InstructionInfo();
            if (execute.getInstruction().isArithematic()
                    && execute.getInstruction().getType() != InstructionType.NOP) {
                Flags.ZERO = execute.getInstruction().getResultValue() == 0;
                instruction = execute.getInstruction();
                //System.out.println("Flags for BZ/BNZ forwarded from IntFU!");
            }

            if (executeMul2.getInstruction().isArithematic()
                    && executeMul2.getInstruction().getType() != InstructionType.NOP) {
                Flags.ZERO = executeMul2.getInstruction().getResultValue() == 0;
                instruction = executeMul2.getInstruction();
                //System.out.println("Flags for BZ/BNZ forwarded from MulFU!");
            }

            if (executeDiv4.getInstruction().isArithematic()
                    && executeDiv4.getInstruction().getType() != InstructionType.NOP) {
                Flags.ZERO = executeDiv4.getInstruction().getResultValue() == 0;
                instruction = executeDiv4.getInstruction();
                //System.out.println("Flags for BZ/BNZ forwarded from DivFU!");
            }

            /** Resume decode and fetch only if ZERO flag is forwarded from the immediately prior instruction to BZ/BNZ
             * (See example above)
             * BNZ should pick up flag from DIV4.
             * The condition below checks the PC value of BZ/BNZ in decode stage, and the PC value of the instruction
             * which forwarded the ZERO flag.
             * It checks if it is the immediately prior instruction to BZ/BNZ and releases F and DRF as a result.
             */

            if (decode.getInstruction().getPc() == instruction.getPc() + 4) {
                decode.setStalled(false);
                fetch.setStalled(false);
            }
        }

        if (executeDiv4.getInstruction().getType() != InstructionType.NOP) {
            execute.setStalled(true);
            executeMul2.setStalled(true);
            if(executeMul2.getInstruction().getType() != InstructionType.NOP) {
                executeMul1.setStalled(true);
            }
        }

        /**
         *  The code below this comment handles multiple back to back MUL instructions being stalled.
            Fetch      :   (I12)  [MUL R2,R10,R5]
            DRF        :   (I11)  [MUL R5,R10,R5]
            INTFU      :   [NOP]
            MUL1       :   (I10)  [MUL R9,R5,R6]
            MUL2       :   (I9)  [MUL R10,R1,R6]   Stalled
            DIV1       :   [NOP]
            DIV2       :   [NOP]
            DIV3       :   (I8)  [DIV R8,R2,R3]
            DIV4       :   (I7)  [DIV R7,R2,R3]
            MEM        :   (I6)  [DIV R4,R2,R3]
            WB         :   (I5)  [DIV R1,R3,R2]

            In the next cycle, MUL1 should be stalled, and as DRF contains a MUL as well, DRF should be stalled as MUL1
            is stalled. Lastly, F will stall due to DRF being stalled.
         */
        if (executeMul2.getInstruction().getType() != InstructionType.NOP
                && executeMul2.isStalled()
                && executeDiv4.getInstruction().getType() != InstructionType.NOP
                && executeMul1.getInstruction().getType() != InstructionType.NOP
                && !multipleMULs) {
            executeMul1.setStalled(true);
            decode.setStalled(true);
            fetch.setStalled(true);
            multipleMULs = true;
        } else {
            /**
             *  And to release the above stall (when MUL1 is free).
             *  Example below;
             *
             *  Cycle: 20
             Data forwarding for R10 from MulFU performed!
             -------------------------------------------------
             Fetch      :   (I12)  [MUL R2,R10,R5]   Stalled
             DRF        :   (I11)  [MUL R5,R10,R5]   Stalled
             INTFU      :   [NOP]
             MUL1       :   (I10)  [MUL R9,R5,R6]   Stalled
             MUL2       :   (I9)  [MUL R10,R1,R6]   Stalled
             DIV1       :   [NOP]
             DIV2       :   [NOP]
             DIV3       :   [NOP]
             DIV4       :   [NOP]
             MEM        :   (I8)  [DIV R8,R2,R3]
             WB         :   (I7)  [DIV R7,R2,R3]
             -------------------------------------------------
             Cycle: 21
             Data forwarding for R10 from MulFU performed!
             -------------------------------------------------
             Fetch      :   (I12)  [MUL R2,R10,R5]   Stalled
             DRF        :   (I11)  [MUL R5,R10,R5]   Stalled
             INTFU      :   [NOP]
             MUL1       :   [NOP]
             MUL2       :   (I10)  [MUL R9,R5,R6]
             DIV1       :   [NOP]
             DIV2       :   [NOP]
             DIV3       :   [NOP]
             DIV4       :   [NOP]
             MEM        :   (I9)  [MUL R10,R1,R6]
             WB         :   (I8)  [DIV R8,R2,R3]

             Cycle 21 has generated incorrect output!!!
             It is handled as below.
             */
            if (executeMul1.getInstruction().getType() == InstructionType.NOP
                    && decode.isStalled() && fetch.isStalled() && multipleMULs) {
                decode.setStalled(false);
                fetch.setStalled(false);
                multipleMULs = false;
            }
        }

        if (executeMul2.getInstruction().getType() != InstructionType.NOP) {
            execute.setStalled(true);
        }

        if (execute.isStalled() && execute.getInstruction() != null
                && execute.getInstruction().getType() != InstructionType.NOP
                && !decode.getInstruction().getType().equals(InstructionType.MUL)
                && !decode.getInstruction().getType().equals(InstructionType.HALT)
                && !decode.getInstruction().getType().equals(InstructionType.DIV)) {
            decode.setStalled(true);
        }

        if (decode.isStalled() && decode.getInstruction() != null
                && decode.getInstruction().getType() != InstructionType.NOP) {
            fetch.setStalled(true);
        }
    }

    /**
     * This method fetches all the sources and destinations from the decode stage's current instruction
     * and loops over each register to check if it is SET and NOT forwarded.
     * Sets boolean flag to true if the conditions are satisfied. This flag stalls the F and DRF later on.
     * @param registerDependency
     * @return registerDependency
     */
    private boolean checkRegisterDependency(boolean registerDependency) {
        List<Source> allRegisters = decode.getAllSourcesAndDestination();
        if (allRegisters != null) {
            for (Source r : allRegisters) {
                if (r != null && r.isStatus() && !r.isForwarded()) {
                    registerDependency = true;
                    break;
                }
            }
        }
        return registerDependency;
    }

    /**
     * A function for register renaming; instruction is passed through decode stage.
     * Added on 2017-11-13.
     */
    public void renameRegisters(InstructionInfo instruction) {

        if (instruction.getInstruction() != null
            && instruction.getType() != InstructionType.NOP) {

            int architecturalRegisterAddress = instruction.getDestinationRegister().getAddress();
            int physicalRegisterAddress = 0;

            if (renameTableMap.isEmpty()) {
                if (!PhysicalRegisterFile.physicalRegistersList.isEmpty()
                        && !PhysicalRegisterFile.physicalRegistersList.get(0).isAllocated()) {
                    physicalRegisterAddress = PhysicalRegisterFile.physicalRegistersList.get(0).getAddress();
                    //instruction.getDestinationRegister().setAddress(physicalRegisterAddress);
                    renameTableMap.put(architecturalRegisterAddress, physicalRegisterAddress);
                } else if (!PhysicalRegisterFile.physicalRegistersList.isEmpty()
                        && PhysicalRegisterFile.physicalRegistersList.get(0).isAllocated()) {
                    for (PhysicalRegister physicalRegister : PhysicalRegisterFile.physicalRegistersList) {
                        if (!physicalRegister.isAllocated()) {
                            physicalRegisterAddress = physicalRegister.getAddress();
                            //instruction.getDestinationRegister().setAddress(physicalRegisterAddress);
                            renameTableMap.put(architecturalRegisterAddress, physicalRegisterAddress);
                            break;
                        }
                    }
                }
                Collections.sort(PhysicalRegisterFile.physicalRegistersList);
            }

            if (renameTableMap.get(architecturalRegisterAddress) == null) {
                System.out.println("Rename table is empty.");

                // Check if physical registers are available, if not stall decode.



            }
        }
    }


    /**
     * Fetch the current instruction from the EX stage and check if it is BZ or BNZ.
     * If BZ, check if Zero flag is set
     * If BNZ, checks
     */
    public void handleBzBnz() {
        InstructionInfo instruction = execute.getInstruction();
        if (instruction.getType().equals(InstructionType.BZ) && Flags.ZERO) {
            programCounter = instruction.getResultValue();
            flushInstructionsBeforeExecute();
        }

        if (instruction.getType().equals(InstructionType.BNZ) && !Flags.ZERO) {
            programCounter = instruction.getResultValue();
            flushInstructionsBeforeExecute();
        }
    }

    /**
     * Sets NOP for fetch and decode stages (flushes instructions from F and DRF).
     */
    private static void flushInstructionsBeforeExecute() {
        InstructionInfo nop = InstructionInfo.createNOPInstruction();
        fetch.setInstruction(nop);
        decode.setInstruction(nop);
    }

    public void displayStats() {
        Stats.registers = RegisterFile.getRegisterValues();
        Stats.memory = DataMemory.getMemoryValues();

        System.out.println(Stats.printStats());
    }

    /**
     * This method is used for printing the output for each cycle.
     * The instruction number is fetched from a map by providing it the pc value of the instruction in the desired stage.
     * @return toString of the StringBuilder.
     */
    public String display() {
        StringBuilder sb = new StringBuilder();

        sb.append("-------------------------------------------------");
        sb.append('\n');

        if (instructionPrefixInDisplayMap.get(fetch.getInstruction().getPc()) == null) {
            sb.append("Fetch      :   " + fetch.toString());
            sb.append('\n');
        } else {
            sb.append("Fetch      :   " + "(I" + instructionPrefixInDisplayMap.get(fetch.getInstruction().getPc()) + ")  " + fetch.toString() + (fetch.isStalled() ? "   Stalled" : ""));
            sb.append('\n');
        }
        if (!decode.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("DRF        :   " + "(I" + instructionPrefixInDisplayMap.get(decode.getInstruction().getPc()) + ")  " + decode.toString() + (decode.isStalled() ? "   Stalled" : ""));
        } else {
            sb.append("DRF        :   " + decode.toString());
        }
        sb.append('\n');
        if (!execute.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("INTFU      :   " + "(I" + instructionPrefixInDisplayMap.get(execute.getInstruction().getPc()) + ")  " + execute.toString() + (execute.isStalled() ? "   Stalled" : ""));
        } else {
            sb.append("INTFU      :   " + execute.toString());
        }
        sb.append('\n');
        if (!executeMul1.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("MUL1       :   " + "(I" + instructionPrefixInDisplayMap.get(executeMul1.getInstruction().getPc()) + ")  " + executeMul1.toString() + (executeMul1.isStalled() ? "   Stalled" : ""));
        } else {
            sb.append("MUL1       :   " + executeMul1.toString());
        }
        sb.append('\n');
        if (!executeMul2.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("MUL2       :   " + "(I" + instructionPrefixInDisplayMap.get(executeMul2.getInstruction().getPc()) + ")  " + executeMul2.toString() + (executeMul2.isStalled() ? "   Stalled" : ""));
        } else {
            sb.append("MUL2       :   " + executeMul2.toString());
        }
        sb.append('\n');
        if (!executeDiv1.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("DIV1       :   " + "(I" + instructionPrefixInDisplayMap.get(executeDiv1.getInstruction().getPc()) + ")  " + executeDiv1.toString());
        } else {
            sb.append("DIV1       :   " + executeDiv1.toString());
        }
        sb.append('\n');
        if (!executeDiv2.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("DIV2       :   " + "(I" + instructionPrefixInDisplayMap.get(executeDiv2.getInstruction().getPc()) + ")  " + executeDiv2.toString());
        } else {
            sb.append("DIV2       :   " + executeDiv2.toString());
        }
        sb.append('\n');
        if (!executeDiv3.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("DIV3       :   " + "(I" + instructionPrefixInDisplayMap.get(executeDiv3.getInstruction().getPc()) + ")  " + executeDiv3.toString());
        } else {
            sb.append("DIV3       :   " + executeDiv3.toString());
        }
        sb.append('\n');
        if (!executeDiv4.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("DIV4       :   " + "(I" + instructionPrefixInDisplayMap.get(executeDiv4.getInstruction().getPc()) + ")  " + executeDiv4.toString() + (executeDiv4.isStalled() ? "   Stalled" : ""));
        } else {
            sb.append("DIV4       :   " + executeDiv4.toString());
        }
        sb.append('\n');
        if (!memory.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("MEM        :   " + "(I" + instructionPrefixInDisplayMap.get(memory.getInstruction().getPc()) + ")  " + memory.toString());
        } else {
            sb.append("MEM        :   " + memory.toString());
        }
        sb.append('\n');
        if (!writeback.getInstruction().getType().equals(InstructionType.NOP)) {
            sb.append("WB         :   " + "(I" + instructionPrefixInDisplayMap.get(writeback.getInstruction().getPc()) + ")  " + writeback.toString());
        } else {
            sb.append("WB         :   " + writeback.toString());
        }
        sb.append('\n');
        System.out.print(sb.toString());
        return sb.toString();
    }
}
