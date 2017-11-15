package main.java.apex;

public class PhysicalRegister {

    private int value;
    private int address;
    private String name;
    private boolean isRenamed;
    private boolean isAllocated;

    public PhysicalRegister(int value, int address, String name, boolean isRenamed, boolean isAllocated) {
        this.value = value;
        this.address = address;
        this.name = name;
        this.isRenamed = isRenamed;
        this.isAllocated = isAllocated;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
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
        return "PhysicalRegister{" +
                "value=" + value +
                ", address=" + address +
                ", name='" + name + '\'' +
                '}';
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
