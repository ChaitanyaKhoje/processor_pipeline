package main.java.apex;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single stage in the pipeline. Each Stage in the pipeline is an instance of this class.
 */
public class Stage {
    private InstructionInfo instruction = InstructionInfo.createNOPInstruction();
    private boolean stalled;

    /**
     * Returns a list of all the sources (Registers and literals) for the instruction.
     * For eg. ADD R1, R2, R3 will return a list of [R1, R2, R3]
     * @return List of sources which can be registers or literals
     */
    public List<Source> getAllSourcesAndDestination() {
        Source source1 = instruction.getSource1();
        Source source2 = instruction.getSource2();
        Source source3 = instruction.getSource3();
        Register destination = instruction.getDestinationRegister();

        List<Source> sources = new ArrayList<Source>();
        sources.add(source1);
        sources.add(source2);
        sources.add(source3);
        sources.add(destination);
        return sources;
    }

    /**
     * Returns a list of all the sources (Registers and literals) for the instruction.
     * For eg. ADD R1, R2, R3 will return a list of [R1, R2, R3]
     * @return List of sources which can be registers or literals
     */
    public List<Source> getAllSources() {
        Source source1 = instruction.getSource1();
        Source source2 = instruction.getSource2();
        Source source3 = instruction.getSource3();

        List<Source> sources = new ArrayList<Source>();
        if(source1 instanceof Register){
            sources.add(source1);
        }

        if(source2 instanceof Register) {
            sources.add(source2);
        }

        if(source3 instanceof Register) {
            sources.add(source3);
        }

        return sources;
    }

    /**
     * Returns a list of all the sources (Registers and literals) for the instruction.
     * For eg. ADD R1, R2, R3 will return a list of [R1, R2, R3]
     * @return List of sources which can be registers or literals
     */
    public Source getDestination() {
        Register destination = instruction.getDestinationRegister();
        return destination;
    }
    public InstructionInfo getInstruction() {
        return instruction;
    }

    public Stage setInstruction(InstructionInfo instruction) {
        this.instruction = instruction;
        return this;
    }

    public boolean isStalled() {
        return stalled;
    }

    public Stage setStalled(boolean stalled) {
        this.stalled = stalled;
        return this;
    }

    @Override
    public String toString() {
        return "[" + instruction + "]";
    }
}
