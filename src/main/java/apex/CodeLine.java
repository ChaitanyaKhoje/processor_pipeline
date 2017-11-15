package main.java.apex;

/**
 * This class is description of a single line of code in the input file.
 */
public class CodeLine {

    private int fileLineNumber;
    private int instrAddr;
    private String instr;

    public CodeLine(int fileLnNum, int instrAddr, String instr){
        this.fileLineNumber = fileLnNum;
        this.instrAddr = instrAddr;
        this.instr = instr;
    }

    @Override
    public String toString() {
        return "CodeLine{" +
                "fileLineNumber=" + fileLineNumber +
                ", instrAddr=" + instrAddr +
                ", instr='" + instr + '\'' +
                '}';
    }
}
