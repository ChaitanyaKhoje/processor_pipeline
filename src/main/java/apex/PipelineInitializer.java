package main.java.apex;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import java.util.Scanner;

/**
 * Helper class to initialize the pipeline
 */
public class PipelineInitializer {

    private ArrayList<CodeLine> codeLines = new ArrayList<CodeLine>();

    public void initialize(File file) {
        initCodeMemory(file);
        initRegisters();
        Stats.clear();
        Flags.resetFlags();
        PipelineManager.programCounter = 4000;
        PipelineManager.fetch = new Stage();
        PipelineManager.decode = new Stage();
        PipelineManager.execute = new Stage();
        PipelineManager.executeMul1 = new Stage();
        PipelineManager.executeMul2 = new Stage();
        PipelineManager.executeDiv1 = new Stage();
        PipelineManager.executeDiv2 = new Stage();
        PipelineManager.executeDiv3 = new Stage();
        PipelineManager.executeDiv4 = new Stage();
        PipelineManager.memory = new Stage();
        PipelineManager.writeback = new Stage();
    }

    /**
     * Initialize code memory.
     */
    private void initCodeMemory(File file) {
        Scanner scanner;
        try {
            scanner = new Scanner(file);
            int fileLineNumber = 0;
            int pc = 4000;
            while (scanner.hasNextLine()) {
                String fileLine = scanner.nextLine();
                CodeLine cL = new CodeLine(fileLineNumber, pc, fileLine.trim());
                codeLines.add(cL);
                //    Populating pc and instruction string into InstructionInfo class for future.
                InstructionInfo instructionInfo = new InstructionInfo();
                instructionInfo.setPc(pc);
                instructionInfo.setInstruction(fileLine.trim());
                PipelineManager.instructionInfoMap.put(pc, instructionInfo);
                PipelineManager.instructionPrefixInDisplayMap.put(pc, fileLineNumber);
                fileLineNumber++;
                pc = pc + 4;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize registers.
     */
    private void initRegisters() {


        for (int j = 0; j < 16; j++) {
            Register register = new Register(0, j, false, "R" + j);
            RegisterFile.registerMap.put("R" + j, register);
        }

        for (int k = 0; k < 32; k++) {
            PhysicalRegister physicalRegister = new PhysicalRegister(0, k, "P" + k,false,false);
            PhysicalRegisterFile.physicalRegistersList.add(physicalRegister);
        }
    }
}
