package main.java.apex;

/**
 * This class is used to track which instruction is the nearest to BZ or BNZ. Each arithmetic instruction will obtain
 * this lock in decode stage and release the lock if owned by self in writeback stage. Bnz or Bz will only proceed
 * when this lock is released and is not busy.
 */
public class BzLock {
    public static boolean IS_BUSY;
    public static InstructionInfo INSTRUCTION;
}
