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
    public static Stage issueQueueStage;

    public static int iqLimit = 16;
    public static int lsqLimit = 32;
    public static int robLimit = 32;
    public static int memDelay = 0;

    public boolean physicalRegistersAvailable = true;   // Initialized as physical registers are available for renaming.
    public static boolean isFlushed;    // Used for handling stalled BZ/BNZ/JAL/JUMP in EX stage (flush only once!).
    public boolean multipleMULs = true; // Explanation in setStallForStages.
    public static boolean physicalRegisterProvided = false;
    public static boolean issueQueueFull = false;
    public static boolean lsqFull = false;
    public static boolean robFull = false;

    public boolean canByPass = false;
    public static LoadStoreQueueElement byPassingElement = null;

    public static boolean instructionInIQIsReady = false;
    public static boolean allStagesEmpty = false;   // Used for stopping the display of empty cycles (usage in display()).

    public static Map<Integer, InstructionInfo> instructionInfoMap = new HashMap<Integer, InstructionInfo>();
    public static Map<Integer, Integer> instructionPrefixInDisplayMap = new HashMap<Integer, Integer>();       // A map used to get the line number of the instruction while displaying it in the stages.
    // A map that tells us which architectural register is assigned to which physical register.
    public static Map<Integer, Integer> renameTableMap = new HashMap<>();
    public static List<InstructionInfo> issueQueueList = new ArrayList<InstructionInfo>();
    public static List<LoadStoreQueueElement> LSQ = new ArrayList<LoadStoreQueueElement>();
    public static List<RobSlot> ROB = new ArrayList<RobSlot>();

    public RobSlot committedInstruction = null;
    public static List<RobSlot> committedInstructionsList = new ArrayList<RobSlot>();
    private PipelineInitializer initializer = new PipelineInitializer();
    private Map<Integer, InstructionInfo> renameTableInstructionMap = new HashMap<Integer, InstructionInfo>();
    private int globalRobDeleteCount = 0;

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
            doDataForwarding();
            doMemory();
            doCommit();
            doDiv4();
            doDiv3();
            doDiv2();
            doDiv1();
            doMul2();
            doMul1();
            doExecute(); 
            doIssuing();
            doDecode(i);
            doFetch();
            handleBzBnz();

            globalRobDeleteCount = 0;
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
            //if (Flags.EXIT) break;
            if (allStagesEmpty || Flags.EXIT) {
                System.out.println("All the cycles henceforth are empty. \n");
                double numOfInstructions = instructionPrefixInDisplayMap.size();
                double CPI = (i/numOfInstructions);
                System.out.println("CPI = "+ CPI);
                break;
            }
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
     * The decode stage will now contain the logic for register renaming as well.
     * The argument i passed to decode stage is used for counting the cycles, a cycleCount is maintained which is used
     * in the Issue Queue.
     */
    public void doDecode(int i) {
        InstructionInfo instruction;
        if (fetch.isStalled()) {
            if (!decode.isStalled()) {
                instruction = InstructionInfo.createNOPInstruction();
                decode.setInstruction(instruction);
            }
        } else {
            instruction = fetch.getInstruction();
            instruction = InstructionDecoder.decode(instruction);
            //decode.setInstruction(instruction);

            // Register Renaming
            // add destination register to rename map.
            instruction = renameRegisters(instruction);
            instruction.setCycleCount(i);
            //instruction = renameRegisters(instruction, i);
            // Dispatch instruction; DRF to Issue Queue.
            // instruction = dispatchInstruction(instruction);
            decode.setInstruction(instruction);
        }
    }

    public void doIssuing() {

        InstructionInfo ins = decode.getInstruction();
        ins = dispatchInstruction(ins);

        List<InstructionInfo> sortedInstructions = issueQueueList;

        Collections.sort(sortedInstructions, new Comparator<InstructionInfo>() {
            @Override
            public int compare(InstructionInfo o1, InstructionInfo o2) {
                return o1.getCycleCount() - o2.getCycleCount();
            }
        });

        // need to check for stall
        for (InstructionInfo instruction : sortedInstructions) {
            Register dest = instruction.getDestinationRegister();
            // int phyAddr = renameTableMap.get(dest.getAddress());
            //PhysicalRegister physicalRegister = PhysicalRegisterFile.getByAddress(phyAddr);
            //if (physicalRegister != null && physicalRegister.isStatus()) {
            if (!checkRegisterDependency2(false, instruction.getAllRegisters())) {
                issueQueueStage.setInstruction(instruction);
                break;
            }
        }
    }

//    public void revalidateIssueQueueStage() {
//
//        List<InstructionInfo> sortedInstructions = issueQueueList;
//        Collections.sort(sortedInstructions, new Comparator<InstructionInfo>() {
//            @Override
//            public int compare(InstructionInfo o1, InstructionInfo o2) {
//                return o1.getCycleCount() - o2.getCycleCount();
//            }
//        });
//
//        // need to check for stall
//        for (InstructionInfo ins : sortedInstructions) {
//            if (!checkRegisterDependency2(false, ins.getAllSourceRegisters())) {
//                if (issueQueueStage.isStalled()) {
//                    issueQueueStage.setStalled(false);
//                }
//                issueQueueStage.setInstruction(ins);
//                break;
//            }
//        }
//    }

    public void revalidateIssueQueueStage(FunctionUnitType type) {

        List<InstructionInfo> sortedInstructions = new ArrayList<InstructionInfo>();
        switch (type) {
            case IntFU:
                for (InstructionInfo instruction : issueQueueList) {
                    if (instruction.getType() != InstructionType.MUL
                            && instruction.getType() != InstructionType.DIV) {
                        sortedInstructions.add(instruction);
                    }
                }
            case MulFU:
                for (InstructionInfo instruction : issueQueueList) {
                    if (instruction.getType() == InstructionType.MUL) {
                        sortedInstructions.add(instruction);
                    }
                }
            case DivFU:
                for (InstructionInfo instruction : issueQueueList) {
                    if (instruction.getType() == InstructionType.DIV) {
                        sortedInstructions.add(instruction);
                    }
                }
        }

        //List<InstructionInfo> sortedInstructions = issueQueueList;

        Collections.sort(sortedInstructions, new Comparator<InstructionInfo>() {
            @Override
            public int compare(InstructionInfo o1, InstructionInfo o2) {
                return o1.getCycleCount() - o2.getCycleCount();
            }
        });

        // need to check for stall
        for (InstructionInfo ins : sortedInstructions) {
            if (!checkRegisterDependency2(false, ins.getAllSourceRegisters())) {
                if (issueQueueStage.isStalled()) {
                    issueQueueStage.setStalled(false);
                }
                issueQueueStage.setInstruction(ins);
                break;
            }
        }

    }

    public void doExecute() {
        revalidateIssueQueueStage(FunctionUnitType.IntFU);
        InstructionInfo instruction = issueQueueStage.getInstruction();
        if (instruction.getType().equals(InstructionType.NOP)) return;
        if (!issueQueueStage.isStalled()
                && !InstructionType.MUL.equals(instruction.getType())
                && !InstructionType.HALT.equals(instruction.getType())
                && !InstructionType.DIV.equals(instruction.getType())
                && (!execute.isStalled() || execute.getInstruction().getType() == InstructionType.NOP)) {

            issueQueueList.remove(instruction);
            issueQueueStage.setInstruction(InstructionInfo.createNOPInstruction());
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
        // MOV R0 #1
        // R0 -> P0

        // update phy register

        Register register = instruction.getDestinationRegister();
        if (register != null) {
            PhysicalRegister physicalRegister = PhysicalRegisterFile.getByAddress(renameTableMap.get(register.getAddress()));
            if (physicalRegister != null) {
                physicalRegister.setStatus(true);
                physicalRegister.setValue(instruction.getResultValue());
            }
        }

        // update ROB status

        for (RobSlot slot : ROB) {
            if (slot.getRobInstruction().getInstruction().equals(instruction.getInstruction())
                    && !slot.getRobInstruction().getType().equals(InstructionType.LOAD)) {
                slot.setStatus(true);
                break;
            }
        }

        // update LSQ
        // LOAD R1 R2 #10 -> mem[R2 + 10] into R1
        // STORE R1 R2 #10 -> R1's value in mem[R2 + 10]

        // LSQ
        // bypass logic
        if (execute.getInstruction().getType().equals(InstructionType.LOAD)) {
            InstructionInfo loadInst = execute.getInstruction();
            List<InstructionInfo> allStores = new ArrayList<>();

            LoadStoreQueueElement loadElement = null;
            for (LoadStoreQueueElement elem : LSQ) {
                if (elem.getInstruction().getType().equals(InstructionType.STORE)
                        && elem.getInstruction().getCycleCount() < loadInst.getCycleCount()) {
                    allStores.add(elem.getInstruction());
                } else {
                    if (elem.getInstruction().equals(loadInst)) {
                        loadElement = elem;
                    }
                }
            }

            canByPass = true;
            for (InstructionInfo ins : allStores) {
                if (ins.getTargetMemoryAddress() == loadInst.getTargetMemoryAddress()) {
                    canByPass = false;
                    break;
                }
            }

            if (canByPass && loadElement != null) {
                LSQ.remove(loadElement);
                LSQ.add(0, loadElement);
                byPassingElement = loadElement;
            }
        }
    }

    // copy from store to load directly and remove load
    public void checkForLoadStoreForwarding(InstructionInfo loadInst) {
        List<InstructionInfo> allStores = new ArrayList<>();

        for (LoadStoreQueueElement elem : LSQ) {
            if (elem.getInstruction().getType().equals(InstructionType.STORE)
                    && elem.getInstruction().getCycleCount() < loadInst.getCycleCount()) {
                allStores.add(elem.getInstruction());
            }
        }
        // logic for forwarding/copying value of store into load
        if (allStores.size() > 0) {
            InstructionInfo nearestStore = allStores.get(0);
            if (nearestStore.getTargetMemoryAddress() == loadInst.getTargetMemoryAddress()) {
                loadInst.setResultValue(nearestStore.getSource1().getValue());

                // physical register of the load that needs to be updated
                PhysicalRegister physicalRegister
                        = PhysicalRegisterFile
                        .getByAddress(renameTableMap.get(loadInst.getDestinationRegister().getAddress()));

                // update physical register and ROB
                // where-ever loadInst.getDestinationRegister() register is used as destination, we must update ROV
                if (physicalRegister != null) {
                    physicalRegister.setValue(nearestStore.getSource1().getValue());
                }
                for (RobSlot slot : ROB) {
                    if (slot.getRobInstruction().getDestinationRegister() != null) {
                        if (slot.getRobInstruction().getDestinationRegister().equals(loadInst.getDestinationRegister())) {
                            slot.setStatus(true);
                        }
                    }
                }

                // remove from LSQ
                LoadStoreQueueElement toRemove = null;
                for (LoadStoreQueueElement currentInLSQ : LSQ) {
                    if (currentInLSQ.getInstruction().equals(loadInst)) {
                        toRemove = currentInLSQ;
                        break;
                    }
                }
                if (toRemove != null) {
                    LSQ.remove(toRemove);
                }

                // do forwarding
                for (InstructionInfo ins : issueQueueList) {
                    for (Register r : ins.getAllSourceRegisters()) {

                        if (loadInst.getDestinationRegister().equals(r)) {
                            int phyAddr = renameTableMap.get(r.getAddress());
                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
                            if (phyRegister != null) {
                                phyRegister.setValue(nearestStore.getSource1().getValue());
                                r.setForwarded(true);
                            }
                            //count++
                            Flags.ZERO = memory.getInstruction().getResultValue() == 0;
                        }
                    }
                }
            }
        }
    }

    public void doMul1() {
        revalidateIssueQueueStage(FunctionUnitType.MulFU);
        InstructionInfo instruction = issueQueueStage.getInstruction();

        if (instruction.isArithematic()) {
            BzLock.IS_BUSY = true;
            BzLock.INSTRUCTION = instruction;
        }

        if (!issueQueueStage.isStalled() && (InstructionType.MUL.equals(instruction.getType()))) {
            issueQueueList.remove(instruction);
            issueQueueStage.setInstruction(InstructionInfo.createNOPInstruction());

            instruction = InstructionExecutor.execute(instruction);
            InstructionDecoder.setRegisters(instruction);
            executeMul1.setInstruction(instruction);
        } else if (!executeMul1.isStalled()) {
            instruction = InstructionInfo.createNOPInstruction();
            executeMul1.setInstruction(instruction);
        }
    }

    public void doMul2() {
        InstructionInfo instruction;
        if (!executeMul1.isStalled()) {
            instruction = executeMul1.getInstruction();
            issueQueueList.remove(instruction);
            executeMul2.setInstruction(instruction);

            for (RobSlot slot : ROB) {
                if (slot.getRobInstruction().getInstruction().equals(instruction.getInstruction())) {
                    slot.setStatus(true);
                    slot.setResult(instruction.getResultValue());
                }
            }
        }
    }

    public void doDiv1() {
        revalidateIssueQueueStage(FunctionUnitType.DivFU);
        InstructionInfo instruction = issueQueueStage.getInstruction();

        if (instruction.isArithematic()) {
            BzLock.IS_BUSY = true;
            BzLock.INSTRUCTION = instruction;
        }

        if (!issueQueueStage.isStalled()
                && InstructionType.DIV.equals(instruction.getType())
                || InstructionType.HALT.equals(instruction.getType())) {
            issueQueueList.remove(instruction);
            issueQueueStage.setInstruction(InstructionInfo.createNOPInstruction());

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
        issueQueueList.remove(instruction);

        for (RobSlot slot : ROB) {
            if (slot.getRobInstruction().getInstruction().equals(instruction.getInstruction())) {
                slot.setStatus(true);
                slot.setResult(instruction.getResultValue());
            }
        }
    }

    public void doCommit() {

        // remove from execute stages
        execute.setInstruction(InstructionInfo.createNOPInstruction());
        executeMul2.setInstruction(InstructionInfo.createNOPInstruction());
        executeDiv4.setInstruction(InstructionInfo.createNOPInstruction());

        committedInstructionsList = new ArrayList<RobSlot>();
        committedInstruction = null;
        // instruction at head of ROB
        if (ROB.size() <= 0) return;
        while (ROB.size() > 0 && globalRobDeleteCount < 2) {

            globalRobDeleteCount ++;
            RobSlot head = ROB.get(0);

            if (head.getRobInstruction().getType().equals(InstructionType.HALT)) {
                if (memory.getInstruction().getType().equals(InstructionType.NOP)) {
                    committedInstruction = ROB.get(0);
                    ROB.remove(0);
                }
            }

            InstructionInfo instruction = head.getRobInstruction();
            Register dest = instruction.getDestinationRegister();
            // load store is handled separately
            if (dest != null && instruction.getType() != InstructionType.STORE) {

                PhysicalRegister physicalRegister = PhysicalRegisterFile.getByAddress(renameTableMap.get(dest.getAddress()));
                if (head.isStatus() && physicalRegister != null) {

                    if (head.getRobInstruction().getType().equals(InstructionType.LOAD)) {
                        if (head.isStatus()) {
                            // Fetch the instruction being committed to add it in display buffer
                            committedInstruction = ROB.get(0);
                            ROB.remove(0);
                        }
                    } else {
                        committedInstruction = ROB.get(0);
                        ROB.remove(0);
                    }
                    //InstructionInfo instToDelete = slotToDelete.getRobInstruction();
                /*
                if (execute.getInstruction().equals(instToDelete)) {
                    execute.setInstruction(InstructionInfo.createNOPInstruction());
                } else if (executeMul2.getInstruction().equals(instToDelete)) {
                    executeMul2.setInstruction(InstructionInfo.createNOPInstruction());
                } else if (executeDiv4.getInstruction().equals(instToDelete)) {
                    executeDiv4.setInstruction(InstructionInfo.createNOPInstruction());
                } else if (memory.getInstruction().equals(instToDelete)) {
                    memory.setInstruction(InstructionInfo.createNOPInstruction());
                }
                */
                    Register r = RegisterFile.registerAddressMap.get(head.getArchDestinationRegister());
                    r.setValue(physicalRegister.getValue());
                    r.setStatus(false);
                    RegisterFile.registerAddressMap.remove(head.getArchDestinationRegister());
                    physicalRegister.setAllocated(false);

                    // todo do we need this line
                    InstructionWriter.resetRegisters(head.getRobInstruction());

                    if (renameTableInstructionMap.get(r.getAddress()).equals(head.getRobInstruction())) {
                        renameTableMap.remove(r.getAddress());
                    }
                }
            }
            // Adding committed instructions to a list to display them later on for a particular cycle.
            if (committedInstruction != null && !committedInstructionsList.contains(committedInstruction)) {
                committedInstructionsList.add(committedInstruction);
            }
        }
    }

    public void doMemory() {

        /* NEW logic implies the priorities for using the MEM stage as follows:
           DU (highest), MULtiply FU, IntegerFU (lowest).
        */
        if (execute.getInstruction().getType().equals(InstructionType.LOAD)) {
            checkForLoadStoreForwarding(execute.getInstruction());
        }
        if (memory.getInstruction().getType().equals(InstructionType.NOP)
                || memory.getInstruction() == null
                || memDelay == 3) {

            // means some instruction was there for 3 cycles, so we remove it now
            if (memDelay == 3) {
                //  InstructionWriter.writeToDestination(memory.getInstruction());

                for (RobSlot slot : ROB) {
                    if (slot.getRobInstruction().equals(memory.getInstruction())
                            && memory.getInstruction().getType().equals(InstructionType.LOAD)) {
                        slot.setStatus(true);
                        break;
                    }
                }
                memory.setInstruction(InstructionInfo.createNOPInstruction());
            }

            memDelay = 1;

            /*InstructionInfo instructionDiv = executeDiv4.getInstruction();
            InstructionInfo instructionMUL = executeMul2.getInstruction();

            if (instructionDiv != null && !instructionDiv.getType().equals(InstructionType.NOP)) {
                // There's a DIV instruction in the Div4 which needs to enter the MEM stage.
                instructionDiv = MemoryOperator.doMemoryOperations(instructionDiv);
                memory.setInstruction(instructionDiv);
            } else if (instructionMUL != null && !instructionMUL.getType().equals(InstructionType.NOP)) {
                // There's no DIV in Div4, MUL is given priority.
                instructionMUL = MemoryOperator.doMemoryOperations(instructionMUL);
                memory.setInstruction(instructionMUL);
            } else {*/
            // There's no DIV and MUL that need priority for MEM stage. Hence, IntFU instruction is given priority.
            if (LSQ.size() > 0 && LSQ.get(0) != null) {
                InstructionInfo lsqHeadInstruction = LSQ.get(0).getInstruction();

                if (memory.getInstruction().getType().equals(InstructionType.NOP)) {
                    if (ROB.get(0) != null) {
                        InstructionInfo robHead = ROB.get(0).getRobInstruction();
                        //if (robHead.equals(lsqHeadInstruction)) {
                        if (robHead.equals(lsqHeadInstruction)
                                || (canByPass && (byPassingElement != null
                                && byPassingElement.getInstruction().equals(lsqHeadInstruction)))) {
                            canByPass = false;
                            if (LSQ.get(0) != null) {
                                lsqHeadInstruction = MemoryOperator.doMemoryOperations(lsqHeadInstruction);
                                memory.setInstruction(lsqHeadInstruction);
                                LSQ.remove(0);
                                // load is removed in doCommit since it has a destination register.
                                if (lsqHeadInstruction.getType().equals(InstructionType.STORE)) {
                                    committedInstruction = ROB.get(0);
                                    if (committedInstruction != null) committedInstructionsList.add(committedInstruction);
                                    ROB.remove(0);
                                    globalRobDeleteCount++;
                                }
                            }
                        }
                    }
                }
            }

            // A flag for stalling the mem for 3 cycles


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
        } else {
            memDelay++;
        }
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
                || jump.getType().equals(InstructionType.JAL)) {
            if (TransferControl.jump) {
                isFlushed = false;
                programCounter = execute.getInstruction().getResultValue();
                TransferControl.jump = false;
            }
        }
    }

    private void stallStages() {
        decode.setStalled(false);
        fetch.setStalled(false);
    }

    private void doDataForwarding() {
        boolean registerDependency = false;

        decode.setStalled(false);
        fetch.setStalled(false);
        execute.setStalled(false);
        executeMul1.setStalled(false);
        executeMul2.setStalled(false);
        issueQueueStage.setStalled(false);
        multipleMULs = false;

        List<Register> allRegistersInIssueQueueList = new ArrayList<>();
        for (InstructionInfo ins : issueQueueList) {
            allRegistersInIssueQueueList.addAll(ins.getAllSourceRegisters());
        }
        registerDependency = checkRegisterDependency2(false, allRegistersInIssueQueueList);

        // if there is register dependency, stall decode and fetch anyway.
        if (registerDependency) {
            issueQueueStage.setStalled(true);

            /** Forwarding
             If the address of the destination register of the instructions in the EX and MEM stage matches the
             address of source register of an instruction that is about to enter the EX stage, data forwarding is necessary.
             */
            for (InstructionInfo instruction : issueQueueList) {
                for (Register register : instruction.getAllSourceRegisters()) {

                    if (execute.getDestination() != null && execute.getInstruction().getType() != InstructionType.LOAD) {
                        // there was a condition below to check output dependency. removing it
                        // && !execute.getDestination().equals(issueQueueStage.getDestination()

                        if (execute.getDestination().equals(register)) {
                            int phyAddr = renameTableMap.get(register.getAddress());
                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
                            if (phyRegister != null) {
                                phyRegister.setValue(execute.getInstruction().getResultValue());
                                register.setForwarded(true);
                            }
                            Flags.ZERO = execute.getInstruction().getResultValue() == 0;
                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from IntFU performed!");
                            // count++;
                            continue;
                        }
                    }

                    if (executeMul2.getDestination() != null) {
                        if (executeMul2.getDestination().equals(register)) {
                            int phyAddr = renameTableMap.get(register.getAddress());
                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
                            if (phyRegister != null) {
                                phyRegister.setValue(executeMul2.getInstruction().getResultValue());
                                register.setForwarded(true);
                            }
                            // count++
                            Flags.ZERO = executeMul2.getInstruction().getResultValue() == 0;
                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from MulFU performed!");
                            continue;
                        }
                    }

                    if (executeDiv4.getDestination() != null) {
                        if (executeDiv4.getDestination().equals(register)) {
                            int phyAddr = renameTableMap.get(register.getAddress());
                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
                            if (phyRegister != null) {
                                phyRegister.setValue(executeDiv4.getInstruction().getResultValue());
                                register.setForwarded(true);
                            }
                            // count++
                            Flags.ZERO = executeDiv4.getInstruction().getResultValue() == 0;
                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from DivFU performed!");
                        }
                    }

                    if (memory.getDestination() != null) {
                        if (memory.getDestination().equals(register)) {
                            int phyAddr = renameTableMap.get(register.getAddress());
                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
                            if (phyRegister != null) {
                                phyRegister.setValue(memory.getInstruction().getResultValue());
                                register.setForwarded(true);
                            }
                            //count++
                            Flags.ZERO = memory.getInstruction().getResultValue() == 0;
                        }
                    }
                }
            }
        }
    }


//    private void setStallForStages() {
//
//        boolean registerDependency = false;
//
//        decode.setStalled(false);
//        fetch.setStalled(false);
//        execute.setStalled(false);
//        executeMul1.setStalled(false);
//        executeMul2.setStalled(false);
//        issueQueueStage.setStalled(false);
//        multipleMULs = false;
//
//        registerDependency = checkRegisterDependency(false);
//
//        // if there is register dependency, stall decode and fetch anyway.
//        if (registerDependency) {
//            issueQueueStage.setStalled(true);
//
//            /** Forwarding
//             If the address of the destination register of the instructions in the EX and MEM stage matches the
//             address of source register of an instruction that is about to enter the EX stage, data forwarding is necessary.
//             */
//            for(InstructionInfo instruction: issueQueueList) {
//                for(Register register: instruction.getAllSourceRegisters()) {
//
//                    if (execute.getDestination() != null && execute.getInstruction().getType() != InstructionType.LOAD) {
//                        // there was a condition below to check output dependency. removing it
//                        // && !execute.getDestination().equals(issueQueueStage.getDestination()
//
//                        if (execute.getDestination().equals(register)) {
//                            int phyAddr = renameTableMap.get(register.getAddress());
//                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
//                            if(phyRegister != null) {
//                                phyRegister.setValue(execute.getInstruction().getResultValue());
//                                register.setForwarded(true);
//                            }
//                            Flags.ZERO = execute.getInstruction().getResultValue() == 0;
//                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from IntFU performed!");
//                           // count++;
//                            continue;
//                        }
//                    }
//
//                    if (executeMul2.getDestination() != null) {
//                        if (executeMul2.getDestination().equals(register)) {
//                            int phyAddr = renameTableMap.get(register.getAddress());
//                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
//                            if(phyRegister != null) {
//                                phyRegister.setValue(executeMul2.getInstruction().getResultValue());
//                                register.setForwarded(true);
//                            }
//                            // count++
//                            Flags.ZERO = executeMul2.getInstruction().getResultValue() == 0;
//                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from MulFU performed!");
//                            continue;
//                        }
//                    }
//
//                    if (executeDiv4.getDestination() != null) {
//                        if (executeDiv4.getDestination().equals(register)) {
//                            int phyAddr = renameTableMap.get(register.getAddress());
//                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
//                            if(phyRegister != null) {
//                                phyRegister.setValue(executeDiv4.getInstruction().getResultValue());
//                                register.setForwarded(true);
//                            }
//                            // count++
//                            Flags.ZERO = executeDiv4.getInstruction().getResultValue() == 0;
//                            //System.out.println("Data forwarding for " + ((Register) register).getName() + " from DivFU performed!");
//                        }
//                    }
//
//                    if (memory.getDestination() != null) {
//                        if (memory.getDestination().equals(register)) {
//                            int phyAddr = renameTableMap.get(register.getAddress());
//                            Register phyRegister = PhysicalRegisterFile.getByAddress(phyAddr);
//                            if(phyRegister != null) {
//                                phyRegister.setValue(memory.getInstruction().getResultValue());
//                                register.setForwarded(true);
//                            }
//                            //count++
//                            Flags.ZERO = memory.getInstruction().getResultValue() == 0;
//                        }
//                    }
//                }
//            }
//
//            /**
//             * Count is a variable that increments when a register value is forwarded to the decode stage.
//             * If there are 2 source registers to be forwarded, and only 1 register is forwarded, the count doesn't match
//             * and it goes to the OR condition where register dependency is checked again; if it returns false
//             * it implies that the second register which was not forwarded to, was not the one setting the registerDependency
//             * in the first place. The one which set it to true while starting setStallForStages is already forwarded and hence
//             * all the sources are available and the instruction need not stall in the decode stage.
//             *
//             * In addition to this, we need to set the isForwarded flag of the forwarded register to false so that
//             * later instructions will have a chance to set it again.
//             */
//            if (count == registersInIssueQueueStage.size()
//                    || !checkRegisterDependency(false)) {
//                issueQueueStage.setStalled(false);
//
//                for (Source register : registersInIssueQueueStage) {
//                    ((Register) register).setForwarded(false);
//                }
//            }
//        }
//
//        boolean bzStall = false;
//        if (InstructionType.BNZ.equals(issueQueueStage.getInstruction().getType())
//                || InstructionType.BZ.equals(issueQueueStage.getInstruction().getType())) {
//            if (BzLock.IS_BUSY) {
//                bzStall = true;
//            }
//        }
//
//        if (bzStall) {
//           issueQueueStage.setStalled(true);
//        }
//
//        //  Forward flags (overrides the above code; from bzstall=false;)
//
//        /**
//         *  This code segment handles flag forwarding for BZ BNZ
//         *
//         *  Example:
//         *  Here, BNZ should stall in the next cycle and wait for DIV to set the flag in WB
//         *
//         Fetch      :   (I21)  [MUL R12,R5,R6]
//         DRF        :   (I20)  [BNZ #32]
//         INTFU      :   [NOP]
//         MUL1       :   [NOP]
//         MUL2       :   (I18)  [MUL R5,R9,R4]
//         DIV1       :   (I19)  [DIV R13,R2,R3]
//         DIV2       :   [NOP]
//         DIV3       :   [NOP]
//         DIV4       :   [NOP]
//         MEM        :   [NOP]
//         WB         :   [NOP]
//         */
//        if (issueQueueStage.getInstruction().getType() == InstructionType.BZ
//                || issueQueueStage.getInstruction().getType() == InstructionType.BNZ) {
//
//            InstructionInfo instruction = new InstructionInfo();
//            if (execute.getInstruction().isArithematic()
//                    && execute.getInstruction().getType() != InstructionType.NOP) {
//                Flags.ZERO = execute.getInstruction().getResultValue() == 0;
//                instruction = execute.getInstruction();
//                //System.out.println("Flags for BZ/BNZ forwarded from IntFU!");
//            }
//
//            if (executeMul2.getInstruction().isArithematic()
//                    && executeMul2.getInstruction().getType() != InstructionType.NOP) {
//                Flags.ZERO = executeMul2.getInstruction().getResultValue() == 0;
//                instruction = executeMul2.getInstruction();
//                //System.out.println("Flags for BZ/BNZ forwarded from MulFU!");
//            }
//
//            if (executeDiv4.getInstruction().isArithematic()
//                    && executeDiv4.getInstruction().getType() != InstructionType.NOP) {
//                Flags.ZERO = executeDiv4.getInstruction().getResultValue() == 0;
//                instruction = executeDiv4.getInstruction();
//                //System.out.println("Flags for BZ/BNZ forwarded from DivFU!");
//            }
//
//            /** Resume issueQueueStage and fetch only if ZERO flag is forwarded from the immediately prior instruction to BZ/BNZ
//             * (See example above)
//             * BNZ should pick up flag from DIV4.
//             * The condition below checks the PC value of BZ/BNZ in issueQueueStage stage, and the PC value of the instruction
//             * which forwarded the ZERO flag.
//             * It checks if it is the immediately prior instruction to BZ/BNZ and releases F and DRF as a result.
//             */
//
//            if (issueQueueStage.getInstruction().getPc() == instruction.getPc() + 4) {
//                issueQueueStage.setStalled(false);
//            }
//        }
//
//        if (executeDiv4.getInstruction().getType() != InstructionType.NOP) {
//            execute.setStalled(true);
//            executeMul2.setStalled(true);
//            if (executeMul2.getInstruction().getType() != InstructionType.NOP) {
//                executeMul1.setStalled(true);
//            }
//        }
//
//        /**
//         *  The code below this comment handles multiple back to back MUL instructions being stalled.
//         Fetch      :   (I12)  [MUL R2,R10,R5]
//         DRF        :   (I11)  [MUL R5,R10,R5]
//         INTFU      :   [NOP]
//         MUL1       :   (I10)  [MUL R9,R5,R6]
//         MUL2       :   (I9)  [MUL R10,R1,R6]   Stalled
//         DIV1       :   [NOP]
//         DIV2       :   [NOP]
//         DIV3       :   (I8)  [DIV R8,R2,R3]
//         DIV4       :   (I7)  [DIV R7,R2,R3]
//         MEM        :   (I6)  [DIV R4,R2,R3]
//         WB         :   (I5)  [DIV R1,R3,R2]
//
//         In the next cycle, MUL1 should be stalled, and as DRF contains a MUL as well, DRF should be stalled as MUL1
//         is stalled. Lastly, F will stall due to DRF being stalled.
//         */
//        if (executeMul2.getInstruction().getType() != InstructionType.NOP
//                && executeMul2.isStalled()
//                && executeDiv4.getInstruction().getType() != InstructionType.NOP
//                && executeMul1.getInstruction().getType() != InstructionType.NOP
//                && !multipleMULs) {
//            executeMul1.setStalled(true);
//            issueQueueStage.setStalled(true);
//            multipleMULs = true;
//        } else {
//            /**
//             *  And to release the above stall (when MUL1 is free).
//             *  Example below;
//             *
//             *  Cycle: 20
//             Data forwarding for R10 from MulFU performed!
//             -------------------------------------------------
//             Fetch      :   (I12)  [MUL R2,R10,R5]   Stalled
//             DRF        :   (I11)  [MUL R5,R10,R5]   Stalled
//             INTFU      :   [NOP]
//             MUL1       :   (I10)  [MUL R9,R5,R6]   Stalled
//             MUL2       :   (I9)  [MUL R10,R1,R6]   Stalled
//             DIV1       :   [NOP]
//             DIV2       :   [NOP]
//             DIV3       :   [NOP]
//             DIV4       :   [NOP]
//             MEM        :   (I8)  [DIV R8,R2,R3]
//             WB         :   (I7)  [DIV R7,R2,R3]
//             -------------------------------------------------
//             Cycle: 21
//             Data forwarding for R10 from MulFU performed!
//             -------------------------------------------------
//             Fetch      :   (I12)  [MUL R2,R10,R5]   Stalled
//             DRF        :   (I11)  [MUL R5,R10,R5]   Stalled
//             INTFU      :   [NOP]
//             MUL1       :   [NOP]
//             MUL2       :   (I10)  [MUL R9,R5,R6]
//             DIV1       :   [NOP]
//             DIV2       :   [NOP]
//             DIV3       :   [NOP]
//             DIV4       :   [NOP]
//             MEM        :   (I9)  [MUL R10,R1,R6]
//             WB         :   (I8)  [DIV R8,R2,R3]
//
//             Cycle 21 has generated incorrect output!!!
//             It is handled as below.
//             */
//            if (executeMul1.getInstruction().getType() == InstructionType.NOP
//                    && issueQueueStage.isStalled() && fetch.isStalled() && multipleMULs) {
//                issueQueueStage.setStalled(false);
//                multipleMULs = false;
//            }
//        }
//
//        if (executeMul2.getInstruction().getType() != InstructionType.NOP) {
//            execute.setStalled(true);
//        }
//
//        if (execute.isStalled() && execute.getInstruction() != null
//                && execute.getInstruction().getType() != InstructionType.NOP
//                && !issueQueueStage.getInstruction().getType().equals(InstructionType.MUL)
//                && !issueQueueStage.getInstruction().getType().equals(InstructionType.HALT)
//                && !issueQueueStage.getInstruction().getType().equals(InstructionType.DIV)) {
//            issueQueueStage.setStalled(true);
//        }
//
//        if (issueQueueStage.isStalled() && issueQueueStage.getInstruction() != null
//                && issueQueueStage.getInstruction().getType() != InstructionType.NOP) {
//            //fetch.setStalled(true);
//        }
//    }

    /**
     * This method fetches all the sources and destinations from the issueQueueStage stage's current instruction
     * and loops over each register to check if it is SET and NOT forwarded.
     * Sets boolean flag to true if the conditions are satisfied. This flag stalls the F and DRF later on.
     *
     * @param registerDependency
     * @return registerDependency
     */
    private boolean checkRegisterDependency(boolean registerDependency) {
        List<Source> allRegisters = issueQueueStage.getAllSourcesAndDestination();
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

    private boolean checkRegisterDependency2(boolean registerDependency, List<Register> allRegisters) {
        //List<Source> allRegisters = issueQueueStage.getAllSourcesAndDestination();
        if (allRegisters != null) {
            for (Register r : allRegisters) {
                if (r != null && r.isStatus() && !r.isForwarded()) {
                    registerDependency = true;
                    break;
                }
            }
        }
        return registerDependency;
    }

    //I0 --- R0, R1, R2 -> P0, P1, P2
    public InstructionInfo renameRegisters(InstructionInfo instruction) {
        if (instruction.getType().equals(InstructionType.NOP)) return instruction;
        Register destination = instruction.getDestinationRegister();

        // we check null here because destination can be null for store instruction
        if (destination != null) {
            PhysicalRegister physicalRegister = PhysicalRegisterFile.getAvailablePhysicalRegister();
            if (physicalRegister != null) {
                renameTableMap.put(destination.getAddress(), physicalRegister.getAddress());
                renameTableInstructionMap.put(destination.getAddress(), instruction);
                // Renaming the instruction's registers for display. eg R0 -> P0, so [MOV R0, #1] will be [MOV P0, #1]
                physicalRegister.setAllocated(true);
            }
        }
        return instruction;
    }

//    /**
//     * A function for register renaming; instruction is passed through decode stage.
//     * Added on 2017-11-13.
//     */
//    public InstructionInfo renameRegisters(InstructionInfo instruction, int cycleCount) {
//
//        if (instruction.getInstruction() != null
//                && instruction.getType() != InstructionType.NOP) {
//
//            instruction.setCycleCount(cycleCount); // Get the cycle count
//
//            List<Source> allRegisters = decode.getAllSourcesAndDestination();
//            int architecturalRegisterAddress = instruction.getDestinationRegister().getAddress();
//
//            int physicalRegisterAddress = 0;
//            //String physicalRegisterName = "";
//            List<Source> registers;
//
//            if (!PhysicalRegisterFile.physicalRegistersList.isEmpty()) {
//
//                    physicalRegistersAvailable = true;
//                    /**
//                     * Assigning physical register to destination architectural register in decode stage.
//                     * Iterating through all the physical registers and checking each if it is allocated or not.
//                     */
//                    for (PhysicalRegister physicalRegister : PhysicalRegisterFile.physicalRegistersList) {
//                        if (!physicalRegister.isAllocated()) {
//                            physicalRegisterAddress = physicalRegister.getAddress();
//
//                            /**
//                             * While renaming, we need to check if the destination architectural register in the instruction is already
//                             * renamed and has an entry in the rename table. This implies that a new physical register will be allotted
//                             * to this architectural register.
//                             * The renamed bit for the physical register that was previously allotted to the arch register is set,
//                             * indicating that the latest value should be picked up from the latest physical register
//                             * and not from the old physical register whose renamed bit is now set.
//                             */
//                            if (!renameTableMap.isEmpty()) {
//                                if (renameTableMap.get(architecturalRegisterAddress).equals(physicalRegisterAddress)) {
//                                    physicalRegister.setRenamed(true);
//                                }
//                            }
//                            // Assigning a physical register to the destination arch register from the current instruction
//                            // in the rename table.
//                            renameTableMap.put(architecturalRegisterAddress, physicalRegisterAddress);
//                            registers = Stage.getAllRegistersForIssueQueue(instruction);
//
//                            for (Source register : registers) {
//                                /**
//                                 * instanceof Register is used to avoid literals
//                                 * sourceIndex is used for storing a particular source's number. (src1, src2 or src3)
//                                 */
//                                if (register != null && register instanceof Register) {
//
//                                    int sourceIndex = 0;
//                                    // Code to store the particular NUMBER of a source
//                                    if (instruction.getSource1() != null
//                                            && instruction.getSource1() instanceof Register
//                                            && register == instruction.getSource1()) {
//                                        sourceIndex = 1;
//                                    }
//                                    if (instruction.getSource2() != null
//                                            && instruction.getSource2() instanceof Register
//                                            && register == instruction.getSource2()) {
//                                        sourceIndex = 2;
//                                    }
//                                    if (instruction.getSource3() != null
//                                            && instruction.getSource3() instanceof Register
//                                            && register == instruction.getSource3()) {
//                                        sourceIndex = 3;
//                                    }
//
//                                    int renameTablePREntry;   // rename table physical register entry
//                                    if (renameTableMap.get(architecturalRegisterAddress) != null) {
//                                        renameTablePREntry = (renameTableMap.get(architecturalRegisterAddress));
//                                    } else {
//                                        renameTablePREntry = 0;
//                                    }
//
//                                    /**
//                                     * If there exists an entry in the rename table for the corresponding arch register
//                                     * fetch its physical register
//                                     * renameTableAREntry basically has the name of the physical register assigned to a particular arch register
//                                     */
//                                    if (renameTablePREntry != 0) {
//                                        /**
//                                         * Rename a particular source arch register to a physical register in the instruction
//                                         */
//                                        if (sourceIndex == 1) {
//                                            ((Register) instruction.getSource1()).setAddress(renameTablePREntry);
////                                            instruction.setSrc1PAVal(renameTablePREntry);
////                                            ((Register) instruction.getSource1()).se
//                                        }
//
//                                        if (sourceIndex == 2) {
//                                            instruction.setSrc2PAVal(renameTablePREntry);
//                                            ((Register) instruction.getSource2()).setAddress(renameTablePREntry);
//                                            //((Register) instruction.getSource1()).setRegisterType("P");
//                                        }
//
//                                        if (sourceIndex == 3) {
//                                            instruction.setSrc3PAVal(renameTablePREntry);
//                                            ((Register) instruction.getSource3()).setAddress(renameTablePREntry);
//                                            //((Register) instruction.getSource1()).setRegisterType("P");
//                                        }
//                                    }
//                                }
//                            }
//
//                            physicalRegister.setAddress(instruction.getDestinationRegister().getAddress());
//                            physicalRegister.setAllocated(true);
//                            break;
//                        }
//                    }
//            }
//        }
//        return instruction;
//    }

    /**
     * After renaming the instruction, it is sent to the Issue Queue.
     * This is called dispatching an instruction (DRF to IQ).
     *
     * @param instruction
     * @return instruction
     */
    public InstructionInfo dispatchInstruction(InstructionInfo instruction) {

        if (instruction.getInstruction() != null && instruction.getType() != InstructionType.NOP) {

            // IQ
            if (instruction.getType() != InstructionType.HALT) {
                if (issueQueueList.size() <= iqLimit) {
                    issueQueueList.add(instruction);
                } else {
                    issueQueueFull = true;
                }
            }

            // ROB
            if (ROB.size() <= robLimit) {
                Register dest = instruction.getDestinationRegister();
                RobSlot slot;
                if (dest != null) {
                    int arfRegisterAddress = dest.getAddress();
                    slot = new RobSlot(instruction, arfRegisterAddress);
                } else {
                    slot = new RobSlot(instruction, -1);
                }

                ROB.add(slot);
            } else {
                robFull = true;
            }

            // LSQ
            if ((instruction.getType() == InstructionType.LOAD || instruction.getType() == InstructionType.STORE)) {
                LoadStoreQueueElement element;
                if (instruction.getType() == InstructionType.LOAD) {
                    element = new LoadStoreQueueElement();
                    element.setInstruction(instruction);
                    // Operations to be performed for LOAD instruction.
                    element.setInstructionType(InstructionType.LOAD);
                    int phyRegisterAddr = renameTableMap.get(instruction.getDestinationRegister().getAddress());
                    Register register = PhysicalRegisterFile.getByAddress(phyRegisterAddr);
                    element.setDestinationRegister(register);
                } else {
                    element = new LoadStoreQueueElement();
                    element.setInstruction(instruction);
                    // Operations if instruction is a STORE.
                    // Value to be STOREd
                    element.setInstructionType(InstructionType.STORE);
                    element.setValueToBeStored(instruction.getSource1().getValue());
                }
                // Add to the LSQ
                if (LSQ.size() < lsqLimit) {
                    LSQ.add(element);
                } else {
                    lsqFull = true;
                }
            }
        }

        return instruction;
    }


    /**
     * So, instead of figuring out how to fetch instruction into each unique FU (IntFU, MulFU, DivFU) in the methods for
     * those stages, we can use the issueQueueList list. Issuing an instruction (IQ to EX) can be done here.
     * It will be easier ahead to just pull out the compatible instruction for the desired FU in their respective stages.
     * This method will be called in each respective function unit variant where the instruction enters execution.
     * (IntFU, Mul1, Div1)
     *
     * @return instruction
     */
    public InstructionInfo issueInstruction(InstructionInfo instruction) {


        List<Source> registers;
        boolean sourcesValidityInIQ = false;    // For source registers
        boolean destinationValidityInIQ = false;    // For destination registers

        /**
         * For each instruction in the issue queue, check if all registers are valid or not.
         * Note: isStatus = false, means that the register is valid.
         */
        for (InstructionInfo instructionInIQ : issueQueueList) {

            /**
             * A new method is defined in the Stage class to fetch all registers.
             * A list is returned. This method is a copy of getAllSourcesAndDestinations method, the only
             * difference is that this is a static method.
             * This duplicate method is created due to the fact that when we use getAllSourcesAndDestinations,
             * we use it with a particular STAGE. So to avoid defining a dummy stage just to fetch registers,
             * this workaround is implemented.
             */
            registers = Stage.getAllRegistersForIssueQueue(instructionInIQ);

            // For all sources
            for (Source register : registers) {
                if (register != null && register.isStatus()) {
                    sourcesValidityInIQ = true;
                }
            }

            // For destination register
            if (instructionInIQ.getDestinationRegister().isStatus()) {
                destinationValidityInIQ = true;
            }

            if (sourcesValidityInIQ && destinationValidityInIQ) {
                instructionInIQIsReady = true;
            }

            if (instructionInIQIsReady) {
                List<InstructionInfo> readyInstructions = new ArrayList<InstructionInfo>();
                readyInstructions.add(instructionInIQ);
            }
        }
        return instruction;
    }

    /**
     * Fetch the current instruction from the EX stage and check if it is BZ or BNZ.
     * If BZ, check if Zero flag is set
     * and check if it isn't set for BNZ.
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
     * Note that if all the stages are empty the program should instead of printing empty cycles. This is handled by a flag.
     *
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
        sb.append("[Rename Table]:    ");
        for (Map.Entry entry : renameTableMap.entrySet()) {
            sb.append('\n');
            sb.append("R" + entry.getKey() + "-> P" + entry.getValue());
        }

        sb.append('\n');
        sb.append("[Issue Queue]:    ");
        for (InstructionInfo instruction : issueQueueList) {
            sb.append('\n');
            sb.append(instruction.getInstruction());
        }

        sb.append('\n');
        sb.append("[ROB]:    ");
        for (RobSlot robSlot : ROB) {
            sb.append('\n');
            sb.append(robSlot.getRobInstruction().getInstruction());
        }

        sb.append('\n');
        sb.append("[Commit]:");

        if (!committedInstructionsList.isEmpty()) {
            for (RobSlot committedIns : committedInstructionsList) {
                sb.append('\n');
                sb.append(committedIns.getRobInstruction().getInstruction());
            }
        }

        sb.append('\n');
        sb.append("[LSQ]:    ");
        for (LoadStoreQueueElement lsqElement : LSQ) {
            sb.append('\n');
            sb.append(lsqElement.getInstruction().getInstruction());
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

        if (fetch.getInstruction().getType() == InstructionType.NOP
                && decode.getInstruction().getType() == InstructionType.NOP
                && execute.getInstruction().getType() == InstructionType.NOP
                && executeMul1.getInstruction().getType() == InstructionType.NOP
                && executeMul2.getInstruction().getType() == InstructionType.NOP
                && executeDiv1.getInstruction().getType() == InstructionType.NOP
                && executeDiv2.getInstruction().getType() == InstructionType.NOP
                && executeDiv3.getInstruction().getType() == InstructionType.NOP
                && executeDiv4.getInstruction().getType() == InstructionType.NOP
                && memory.getInstruction().getType() == InstructionType.NOP
                && issueQueueStage.getInstruction().getType() == InstructionType.NOP
                && ROB.size() == 0
                && LSQ.size() == 0
                && issueQueueList.size() == 0
                && committedInstructionsList.isEmpty()) {
            allStagesEmpty = true;
        } else {
            System.out.print(sb.toString());
        }
        return sb.toString();
    }
}
