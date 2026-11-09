package org.solver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

public class ProbSATTest {

    @BeforeEach
    void resetGlobalState() throws Exception {
        invokePrivate("reset", new Class[0]);
        BitPacking.hardLiterals = null;
        BitPacking.hardClauseStart = null;
        BitPacking.softLiterals = null;
        BitPacking.softClauseStart = null;
        BitPacking.softWeights = null;
        BitPacking.varData = null;
        BitPacking.blockingVariable = null;
        BitPacking.originalVariableID = null;
        BitPacking.solverNumVariables = 0;
        BitPacking.totalOriginalVariables = 0;
    }

    private void mockBitPacking(int numVars, int[] hardLits, int[] hardStarts, int[] softLits, int[] softStarts, long[] softWeights) throws Exception {
        BitPacking.solverNumVariables = numVars;
        BitPacking.totalOriginalVariables = numVars;
        BitPacking.originalVariableID = new int[numVars + 1];
        BitPacking.blockingVariable = new boolean[numVars + 1];
        BitPacking.varData = new int[numVars + 1];
        
        for (int i = 1; i <= numVars; i++) {
            BitPacking.originalVariableID[i] = i;
        }
        
        BitPacking.hardLiterals = hardLits;
        BitPacking.hardClauseStart = hardStarts;
        BitPacking.softLiterals = softLits;
        BitPacking.softClauseStart = softStarts;
        BitPacking.softWeights = softWeights;

        // Fixes the IndexOutOfBounds / Bookkeeping bugs
        if (hardStarts != null) {
            setPrivateField("numHardClauses", hardStarts.length - 1);
        }
        if (softStarts != null) {
            setPrivateField("numSoftClauses", softStarts.length - 1);
        }
    }

    private Object invokePrivate(String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method m = ProbSAT.class.getDeclaredMethod(methodName, argTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private Object getPrivateField(String fieldName) throws Exception {
        Field f = ProbSAT.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(null);
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field f = ProbSAT.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, value);
    }

    private int posLit(int id) { return id << 1; }
    private int negLit(int id) { return (id << 1) | 1; }

    // =========================================================================
    // Core solve() Integration Tests
    // =========================================================================

    @Test
    void testSolveThrowsIfBitPackingNotLoaded() {
        BitPacking.hardLiterals = null;
        assertThrows(IllegalStateException.class, () -> ProbSAT.solve(100));
    }

    @Test
    void testSolveHardUnsatTimeout() throws Exception {
        // Formula: x AND -x -> UNSAT
        mockBitPacking(1, new int[]{posLit(1), negLit(1)}, new int[]{0, 1, 2}, null, null, null);
        
        ProbSAT.solve(0); // 0ms timeout hits TIME_CHECK_INTERVAL immediately
        
        assertNull(ProbSAT.bestAssignment, "Assignment should be null if hard clauses are never satisfied");
        assertTrue(ProbSAT.bestUnsatHardCount > 0, "Unsat hard count should be > 0");
    }

    @Test
    void testSolveHardSatNoSoftNullArray() throws Exception {
        // Formula: x. Soft: null
        mockBitPacking(1, new int[]{posLit(1)}, new int[]{0, 1}, null, null, null);
        BitPacking.setState(1, false); // Start F so it has to flip
        
        ProbSAT.solve(1000);
        
        assertEquals(0, ProbSAT.bestUnsatHardCount);
        assertEquals(0, ProbSAT.bestSoftCost);
        assertTrue(ProbSAT.bestAssignment[1]);
    }

    @Test
    void testSolveHardSatNoSoftEmptyArray() throws Exception {
        // Formula: x. Soft: empty array
        mockBitPacking(1, new int[]{posLit(1)}, new int[]{0, 1}, new int[0], new int[]{0}, new long[0]);
        BitPacking.setState(1, false);
        
        ProbSAT.solve(1000);
        
        assertEquals(0, ProbSAT.bestUnsatHardCount);
        assertEquals(0, ProbSAT.bestSoftCost);
    }

    @Test
    void testSolveHardSatSoftSat() throws Exception {
        // Formula: Hard (x), Soft (y)
        mockBitPacking(2, new int[]{posLit(1)}, new int[]{0, 1}, new int[]{posLit(2)}, new int[]{0, 1}, new long[]{10});
        BitPacking.setState(1, false);
        BitPacking.setState(2, false);
        
        ProbSAT.solve(1000);
        
        assertEquals(0, ProbSAT.bestUnsatHardCount);
        assertEquals(0, ProbSAT.bestSoftCost);
        assertTrue(ProbSAT.bestAssignment[1]);
        assertTrue(ProbSAT.bestAssignment[2]);
    }

    @Test
    void testSolveHardSatSoftTimeout() throws Exception {
        // Formula: Hard (x), Soft (y) AND (-y) -> Soft is UNSAT
        mockBitPacking(2, new int[]{posLit(1)}, new int[]{0, 1}, new int[]{posLit(2), negLit(2)}, new int[]{0, 1, 2}, new long[]{10, 10});
        
        // Setup Hard SAT
        BitPacking.setState(1, true); 
        // LOCK variable 1 so randomizeFreeAssignments() doesn't overwrite it with false and fail the hard phase
        BitPacking.varData[1] |= 2; 
        
        ProbSAT.solve(0); // timeouts during soft phase
        
        assertEquals(0, ProbSAT.bestUnsatHardCount);
        assertEquals(10, ProbSAT.bestSoftCost); // Best cost possible is 10 (one of the soft clauses must be broken)
        assertNotNull(ProbSAT.bestAssignment);
    }

    // =========================================================================
    // Private Method & Branch Coverage Tests
    // =========================================================================

    @Test
    void testRandomizeFreeAssignments() throws Exception {
        mockBitPacking(3, new int[0], new int[0], null, null, null);
        
        BitPacking.blockingVariable[1] = true;
        BitPacking.varData[1] |= 1; // Var 1 (blocking): state = true initially
        BitPacking.varData[2] = 2;  // Var 2 (locked): locked bit set, state = false initially
        // Var 3 is normal, starts false.

        invokePrivate("randomizeFreeAssignments", new Class[0]);

        assertFalse(BitPacking.getState(1), "Blocking variable must always be forced to false");
        assertFalse(BitPacking.getState(2), "Locked variable must not be randomized/flipped");
        // Var 3 is randomized, we just care that the loop processed it without crashing.
    }

    @Test
    void testDoHardFlipNoCandidates() throws Exception {
        // Formula: Hard (x), x is locked.
        mockBitPacking(1, new int[]{posLit(1)}, new int[]{0, 1}, null, null, null);
        BitPacking.varData[1] = 2; // Locked

        // Initialize occurrences so we don't crash before candidate check
        invokePrivate("buildOccurrences", new Class[]{boolean.class, int.class, int[].class}, true, 1, BitPacking.hardLiterals);
        
        // This should return immediately because candidates.length == 0
        invokePrivate("doHardFlip", new Class[]{int.class, int[].class, int[].class}, 0, BitPacking.hardLiterals, BitPacking.hardClauseStart);
    }

    @Test
    void testDoSoftFlipNoSafeCandidates() throws Exception {
        // Formula: Hard (x), Soft (-x). 
        // Flipping x to satisfy the soft clause would break the hard clause.
        mockBitPacking(1, new int[]{posLit(1)}, new int[]{0, 1}, new int[]{negLit(1)}, new int[]{0, 1}, new long[]{10});
        BitPacking.setState(1, true); // x=T (Hard SAT, Soft UNSAT)
        
        invokePrivate("buildOccurrences", new Class[]{boolean.class, int.class, int[].class}, true, 1, BitPacking.hardLiterals);
        setPrivateField("clauseIndexOfHardLit", invokePrivate("buildClauseIndexOf", new Class[]{int.class, int[].class}, 1, BitPacking.hardClauseStart));
        invokePrivate("rebuildHardBookkeeping", new Class[]{int[].class, int[].class}, BitPacking.hardLiterals, BitPacking.hardClauseStart);
        
        invokePrivate("buildOccurrences", new Class[]{boolean.class, int.class, int[].class}, false, 1, BitPacking.softLiterals);
        
        // Should return early because safe candidates n == 0
        invokePrivate("doSoftFlip", new Class[]{int.class, int[].class, int[].class}, 0, BitPacking.softLiterals, BitPacking.softClauseStart);
        
        assertTrue(BitPacking.getState(1), "Variable should not be flipped if it breaks a hard clause");
    }

    @Test
    void testBookkeepingTransitionsAndBreakCosts() throws Exception {
        // Formula: Hard (x v y), Soft (x v y)
        mockBitPacking(2, new int[]{posLit(1), posLit(2)}, new int[]{0, 2}, 
                          new int[]{posLit(1), posLit(2)}, new int[]{0, 2}, new long[]{10});
                          
        BitPacking.setState(1, false);
        BitPacking.setState(2, false);
        
        // Build Hard Structures
        invokePrivate("buildOccurrences", new Class[]{boolean.class, int.class, int[].class}, true, 2, BitPacking.hardLiterals);
        setPrivateField("clauseIndexOfHardLit", invokePrivate("buildClauseIndexOf", new Class[]{int.class, int[].class}, 2, BitPacking.hardClauseStart));
        invokePrivate("rebuildHardBookkeeping", new Class[]{int[].class, int[].class}, BitPacking.hardLiterals, BitPacking.hardClauseStart);

        // Build Soft Structures
        invokePrivate("buildOccurrences", new Class[]{boolean.class, int.class, int[].class}, false, 2, BitPacking.softLiterals);
        setPrivateField("clauseIndexOfSoftLit", invokePrivate("buildClauseIndexOf", new Class[]{int.class, int[].class}, 2, BitPacking.softClauseStart));
        invokePrivate("rebuildSoftBookkeeping", new Class[]{int[].class, int[].class}, BitPacking.softLiterals, BitPacking.softClauseStart);

        // Initial State Validation: UNSAT
        assertEquals(1, getPrivateField("unsatHardCount"));
        assertEquals(1, getPrivateField("unsatSoftCount"));
        assertEquals(10L, getPrivateField("currentSoftCost"));
        
        // Flip Var 1 -> T
        invokePrivate("applyFlip", new Class[]{int.class}, 1);
        
        // Clause should now be SAT (trueLitCount = 1)
        assertEquals(0, getPrivateField("unsatHardCount"));
        assertEquals(0, getPrivateField("unsatSoftCount"));
        assertEquals(0L, getPrivateField("currentSoftCost"));
        
        int[] trueLitHard = (int[]) getPrivateField("trueLitCountHard");
        assertEquals(1, trueLitHard[0]);

        // Validate Break Costs (since trueLitCount == 1, flipping 1 back to F will break the clause)
        int hardBreakCost = (int) invokePrivate("computeHardBreakCost", new Class[]{int.class}, 1);
        long softBreakCost = (long) invokePrivate("computeSoftBreakCost", new Class[]{int.class}, 1);
        assertEquals(1, hardBreakCost);
        assertEquals(10L, softBreakCost);
        
        // Flip Var 2 -> T
        invokePrivate("applyFlip", new Class[]{int.class}, 2);
        
        // Clause should remain SAT (trueLitCount = 2)
        assertEquals(0, getPrivateField("unsatHardCount"));
        assertEquals(2, trueLitHard[0]);
        
        // Break costs should now be 0 because trueLitCount > 1
        hardBreakCost = (int) invokePrivate("computeHardBreakCost", new Class[]{int.class}, 1);
        assertEquals(0, hardBreakCost);

        // Flip Var 1 -> F
        invokePrivate("applyFlip", new Class[]{int.class}, 1);
        
        // Clause should remain SAT (trueLitCount = 1)
        assertEquals(0, getPrivateField("unsatHardCount"));
        assertEquals(1, trueLitHard[0]);
        
        // Flip Var 2 -> F
        invokePrivate("applyFlip", new Class[]{int.class}, 2);
        
        // Clause should become UNSAT (trueLitCount = 0)
        assertEquals(1, getPrivateField("unsatHardCount"));
        assertEquals(1, getPrivateField("unsatSoftCount"));
        assertEquals(10L, getPrivateField("currentSoftCost"));
        assertEquals(0, trueLitHard[0]);
    }
}