package main.java.apex;

public class Register extends Source {

    private int address;
    private boolean status; //  OR string VALID INVALID?    valid = 0 and invalid = 1
    private String name;
    private boolean isForwarded;

    public boolean isForwarded() {
        return isForwarded;
    }

    public void setForwarded(boolean forwarded) {
        isForwarded = forwarded;
    }

    public Register(int value, int address, boolean status, String name) {
        super(value);
        this.address = address;
        this.status = status;
        this.name = name;
    }

    @Override
    public String toString() {
        return "Register{" +
                "address=" + address +
                ", value=" + getValue() +
                ", name='" + name + '\'' +
                '}';
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    @Override
    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
