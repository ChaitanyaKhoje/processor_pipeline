package main.java.apex;

public class PhysicalRegister extends Register {

    // MOV R0, #1 -> MOV P0, #1 ----Rename table: R0 -> P0
    // ADD R0, R0, #1 -> ADD P1, P0, #1  ----Rename table: R0 -> P1
    // ADD R1, R0, P2 ->
    private boolean isRenamed;
    private boolean isAllocated;

    public PhysicalRegister(int value, int address, String name, boolean isRenamed, boolean isAllocated, boolean status) {
        super(value, address, status, name);
        this.isRenamed = isRenamed;
        this.isAllocated = isAllocated;
    }

    public boolean isRenamed() {
        return isRenamed;
    }

    public void setRenamed(boolean renamed) {
        isRenamed = renamed;
    }

    public boolean isAllocated() {
        return isAllocated;
    }

    public void setAllocated(boolean allocated) {
        isAllocated = allocated;
    }

    @Override
    public String toString() {
        return super.toString() + "PhysicalRegister{" +
                "isRenamed=" + isRenamed +
                ", isAllocated=" + isAllocated +
                '}';
    }
}
