package main.java.apex;

public class Source {

    private int value;

    public Source(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public boolean isStatus() {
        return false;
    }

    public boolean isForwarded() { return false;}

}
