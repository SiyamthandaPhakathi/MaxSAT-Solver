package org.solver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BitPacking's dense-ID remapping, literal packing, clause offsets,
 * soft-weight handling, locked/blocking-variable initialization, and the
 * packed state/locked bit accessors.
 *
 * Every test calls createDataBlocks (or loadFromPreprocessed) itself before
 * making assertions, so tests are independent of execution order despite
 * BitPacking's fields being static.
 */
public class BitPackingTest {

    @BeforeEach
    void resetBetweenTests() {
        //reset so a failed test can't leak state.
        BitPacking.hardLiterals = null;
        BitPacking.hardClauseStart = null;
        BitPacking.softLiterals = null;
        BitPacking.softClauseStart = null;
        BitPacking.softWeights = null;
        BitPacking.varData = null;
        BitPacking.originalVariableID = null;
        BitPacking.blockingVariable = null;
        BitPacking.solverNumVariables = 0;
        BitPacking.totalOriginalVariables = 0;
        BitPacking.unsat = false;
    }

   //validating memory creation

    @Test
    void createDataBlocksRejectsZeroVariables() {
        assertThrows(IllegalArgumentException.class,
            () -> BitPacking.createDataBlocks(0, new int[]{1, 0}, null, null, null, null));
    }

    @Test
    void createDataBlocksRejectsNegativeVariables() {
        assertThrows(IllegalArgumentException.class,
            () -> BitPacking.createDataBlocks(-3, new int[]{1, 0}, null, null, null, null));
    }

    @Test
    void createDataBlocksRejectsWhenBothFormulasNull() {
        assertThrows(IllegalArgumentException.class,
            () -> BitPacking.createDataBlocks(3, null, null, null, null, null));
    }

    @Test
    void createDataBlocksRejectsVariableIdExceedingNumVariables() {
        // literal 5 with numVariables = 2
        assertThrows(IllegalArgumentException.class,
            () -> BitPacking.createDataBlocks(2, new int[]{5, 0}, null, null, null, null));
    }

    @Test
    void createDataBlocksRejectsMismatchedSoftWeightCount() {
        int[] hard = {1, 0};
        int[] soft = {2, 0, -1, 0}; // 2 soft clauses
        long[] badWeights = {5L}; // only 1 weight
        assertThrows(IllegalArgumentException.class,
            () -> BitPacking.createDataBlocks(2, hard, soft, badWeights, null, null));
    }

   //Testing dense variable mapping and literal packing

    @Test
    void denseMappingAssignsIdsInFirstSeenOrderAndPacksLiteralsCorrectly() {
        // Hard formula: (5 v -9) ^ (9 v -1), numVariables = 10 
        int[] hardFormula = {5, -9, 0, 9, -1, 0};

        BitPacking.createDataBlocks(10, hardFormula, null, null, null, null);

        // First seen order while scanning literals: 5 -> dense 1, 9 -> dense 2, 1 -> dense 3
        assertEquals(3, BitPacking.solverNumVariables);
        assertEquals(10, BitPacking.totalOriginalVariables);

        assertEquals(5, BitPacking.getOriginalVariableID(1));
        assertEquals(9, BitPacking.getOriginalVariableID(2));
        assertEquals(1, BitPacking.getOriginalVariableID(3));

        // Clause offsets: 2 clauses of 2 literals each -> [0, 2, 4]
        assertArrayEquals(new int[]{0, 2, 4}, BitPacking.hardClauseStart);

        // Expected packed literals:
        // clause 1: 5 (dense 1, +)  -> (1<<1)|0 = 2 ;  -9 (dense 2, -) -> (2<<1)|1 = 5
        // clause 2: 9 (dense 2, +)  -> (2<<1)|0 = 4 ;  -1 (dense 3, -) -> (3<<1)|1 = 7
        assertArrayEquals(new int[]{2, 5, 4, 7}, BitPacking.hardLiterals);

        // Round-trip getVarId/getSign against the packed values
        assertEquals(1, BitPacking.getVarId(2));
        assertEquals(0, BitPacking.getSign(2));
        assertEquals(2, BitPacking.getVarId(5));
        assertEquals(1, BitPacking.getSign(5));
        assertEquals(2, BitPacking.getVarId(4));
        assertEquals(0, BitPacking.getSign(4));
        assertEquals(3, BitPacking.getVarId(7));
        assertEquals(1, BitPacking.getSign(7));
    }

    @Test
    void createDataBlocksWithNullHardFormulaProducesEmptyHardArrays() {
        int[] soft = {1, 0};
        long[] weights = {3L};

        BitPacking.createDataBlocks(1, null, soft, weights, null, null);

        assertEquals(0, BitPacking.hardLiterals.length);
        assertArrayEquals(new int[]{0}, BitPacking.hardClauseStart);
    }

    @Test
    void createDataBlocksWithNullSoftFormulaProducesEmptySoftArrays() {
        int[] hard = {1, 0};

        BitPacking.createDataBlocks(1, hard, null, null, null, null);

        assertEquals(0, BitPacking.softLiterals.length);
        assertArrayEquals(new int[]{0}, BitPacking.softClauseStart);
        assertEquals(0, BitPacking.softWeights.length);
    }

    @Test
    void createDataBlocksGeneratesDefaultZeroWeightsWhenIncomingWeightsNull() {
        int[] hard = {1, 0};
        int[] soft = {2, 0, -2, 0}; // 2 soft clauses

        BitPacking.createDataBlocks(2, hard, soft, null, null, null);

        assertEquals(2, BitPacking.softWeights.length);
        assertArrayEquals(new long[]{0L, 0L}, BitPacking.softWeights);
    }

    @Test
    void createDataBlocksCopiesIncomingSoftWeights() {
        int[] hard = {1, 0};
        int[] soft = {2, 0, -2, 0};
        long[] weights = {7L, 42L};

        BitPacking.createDataBlocks(2, hard, soft, weights, null, null);

        assertArrayEquals(new long[]{7L, 42L}, BitPacking.softWeights);
        // Defensive copy: mutating the caller's array shouldn't affect stored weights
        weights[0] = 999L;
        assertEquals(7L, BitPacking.softWeights[0]);
    }

    @Test
    void multiLiteralClausesAreCountedAndPackedCorrectly() {
        // Hard: (1 v 2 v 3), Soft: (-1 v -2)
        int[] hard = {1, 2, 3, 0};
        int[] soft = {-1, -2, 0};
        long[] weights = {10L};

        BitPacking.createDataBlocks(3, hard, soft, weights, null, null);

        assertEquals(3, BitPacking.hardLiterals.length);
        assertArrayEquals(new int[]{0, 3}, BitPacking.hardClauseStart);

        assertEquals(2, BitPacking.softLiterals.length);
        assertArrayEquals(new int[]{0, 2}, BitPacking.softClauseStart);
    }

   //Testing forced variables from preprocessor

    @Test
    void forcedVariablesBecomeLockedWithForcedValue() {
        // numVariables = 3, original variable 2 is forced to true
        int[] hard = {1, 2, 3, 0};
        boolean[] forced = new boolean[]{false, false, true, false}; // index by original ID
        boolean[] forcedValue = new boolean[]{false, false, true, false};

        BitPacking.createDataBlocks(3, hard, null, null, forced, forcedValue);

        // Find dense ID mapped to original variable 2
        int denseIdOfVar2 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 2) denseIdOfVar2 = d;
        }
        assertTrue(denseIdOfVar2 > 0, "Variable 2 should be present in the dense mapping");

        assertTrue(BitPacking.isLocked(denseIdOfVar2));
        assertTrue(BitPacking.getState(denseIdOfVar2));
    }

    @Test
    void forcedVariableFalseLocksStateFalse() {
        int[] hard = {1, 2, 0};
        boolean[] forced = new boolean[]{false, true, false}; // original var 1 forced
        boolean[] forcedValue = new boolean[]{false, false, false}; // forced to false

        BitPacking.createDataBlocks(2, hard, null, null, forced, forcedValue);

        int denseIdOfVar1 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 1) denseIdOfVar1 = d;
        }
        assertTrue(denseIdOfVar1 > 0);
        assertTrue(BitPacking.isLocked(denseIdOfVar1));
        assertFalse(BitPacking.getState(denseIdOfVar1));
    }

    @Test
    void unforcedVariablesAreNotLocked() {
        int[] hard = {1, 2, 0};

        BitPacking.createDataBlocks(2, hard, null, null, null, null);

        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            assertFalse(BitPacking.isLocked(d));
        }
    }

    // Blocking variable detection

    @Test
    void blockingVariableDetectedFromPositiveHardLiteralAndNegativeSoftUnit() {
        // Hard: (1 v 2)   -- variable 2 appears positively here
        // Soft: (-2)      -- unit clause negating variable 2 => classic relaxation pattern
        int[] hard = {1, 2, 0};
        int[] soft = {-2, 0};
        long[] weights = {5L};

        BitPacking.createDataBlocks(2, hard, soft, weights, null, null);

        int denseIdOfVar2 = -1;
        int denseIdOfVar1 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 2) denseIdOfVar2 = d;
            if (BitPacking.getOriginalVariableID(d) == 1) denseIdOfVar1 = d;
        }

        assertTrue(BitPacking.isBlockingVariable(denseIdOfVar2),
            "Variable 2 matches the relaxation pattern and should be flagged as blocking");
        assertFalse(BitPacking.isBlockingVariable(denseIdOfVar1),
            "Variable 1 never appears as a negative soft unit clause, should not be blocking");

        // Blocking variables must start false regardless of the fixed RNG seed
        assertFalse(BitPacking.getState(denseIdOfVar2));
    }

    @Test
    void nonUnitSoftClauseDoesNotTriggerBlockingDetection() {
        // Hard: (1 v 2). Soft: (-2 v 3) -- NOT a unit clause, so 2 should not be flagged.
        int[] hard = {1, 2, 0};
        int[] soft = {-2, 3, 0};
        long[] weights = {5L};

        BitPacking.createDataBlocks(3, hard, soft, weights, null, null);

        int denseIdOfVar2 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 2) denseIdOfVar2 = d;
        }
        assertTrue(denseIdOfVar2 > 0);
        assertFalse(BitPacking.isBlockingVariable(denseIdOfVar2));
    }

    @Test
    void negativeUnitSoftClauseWithoutPositiveHardOccurrenceIsNotBlocking() {
        // Soft has unit clause (-1), but variable 1 never appears positively in the hard formula.
        int[] hard = {-1, 2, 0}; // 1 appears only negatively
        int[] soft = {-1, 0};
        long[] weights = {5L};

        BitPacking.createDataBlocks(2, hard, soft, weights, null, null);

        int denseIdOfVar1 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 1) denseIdOfVar1 = d;
        }
        assertTrue(denseIdOfVar1 > 0);
        assertFalse(BitPacking.isBlockingVariable(denseIdOfVar1));
    }

    @Test
    void isBlockingVariableIsSafeBeforeAnyLoad() {
        // blockingVariable is null at class (re)initialisation via our @BeforeEach reset
        assertFalse(BitPacking.isBlockingVariable(1));
        assertFalse(BitPacking.isBlockingVariable(0));
        assertFalse(BitPacking.isBlockingVariable(-5));
    }

    // Packed state/locked bit accessors

    @Test
    void setStateAndGetStateRoundTrip() {
        BitPacking.createDataBlocks(2, new int[]{1, 2, 0}, null, null, null, null);

        BitPacking.setState(1, true);
        assertTrue(BitPacking.getState(1));

        BitPacking.setState(1, false);
        assertFalse(BitPacking.getState(1));
    }

    @Test
    void flipVarTogglesStateAndDoubleFlipRestoresOriginal() {
        BitPacking.createDataBlocks(1, new int[]{1, 0}, null, null, null, null);

        boolean original = BitPacking.getState(1);
        BitPacking.flipVar(1);
        assertEquals(!original, BitPacking.getState(1));

        BitPacking.flipVar(1);
        assertEquals(original, BitPacking.getState(1));
    }

    @Test
    void stateAndLockedBitsAreIndependent() {
        // Force variable 1 locked+true, ensure setState/flipVar for another variable
        // doesn't disturb its locked bit, and vice versa.
        int[] hard = {1, 2, 0};
        boolean[] forced = {false, true, false};
        boolean[] forcedValue = {false, true, false};

        BitPacking.createDataBlocks(2, hard, null, null, forced, forcedValue);

        int denseIdOfVar1 = -1;
        for (int d = 1; d <= BitPacking.solverNumVariables; d++) {
            if (BitPacking.getOriginalVariableID(d) == 1) denseIdOfVar1 = d;
        }

        assertTrue(BitPacking.isLocked(denseIdOfVar1));
        assertTrue(BitPacking.getState(denseIdOfVar1));

        // Flip the (locked) variable's state bit directly; locked bit must be unaffected.
        BitPacking.flipVar(denseIdOfVar1);
        assertFalse(BitPacking.getState(denseIdOfVar1));
        assertTrue(BitPacking.isLocked(denseIdOfVar1), "Flipping state must not clear the locked bit");
    }

    // isSAT literal evaluation

    @Test
    void isSATEvaluatesPositiveAndNegativeLiteralsCorrectly() {
        BitPacking.createDataBlocks(1, new int[]{1, 0}, null, null, null, null);

        BitPacking.setState(1, true);
        // Correct packed literal creation directly instead of ternary
        int positiveLit = (1 << 1); // (denseID=1, sign=0)
        int negativeLit = (1 << 1) | 1; // (denseID=1, sign=1)

        assertTrue(BitPacking.isSAT(positiveLit), "state=true, positive literal should be SAT");
        assertFalse(BitPacking.isSAT(negativeLit), "state=true, negative literal should be UNSAT");

        BitPacking.setState(1, false);
        assertFalse(BitPacking.isSAT(positiveLit), "state=false, positive literal should be UNSAT");
        assertTrue(BitPacking.isSAT(negativeLit), "state=false, negative literal should be SAT");
    }

    // split() / getSplitView() -- CNF and WCNF handling

    @Test
    void splitPlainCNFTreatsEveryClauseAsHard() {
        Parser.isWCNF = false;
        Parser.numClauses = 2;

        int[] parserOutput = {1, 2, 0, -1, 0};
        BitPacking.split(parserOutput);

        assertArrayEquals(parserOutput, BitPacking.combinedFormulaSplit);
        assertEquals(2, BitPacking.clauseWeightSplit.length);
        for (long w : BitPacking.clauseWeightSplit) {
            assertEquals(BitPacking.HARD, w);
        }

        BitPacking.SplitView view = BitPacking.getSplitView();
        assertArrayEquals(parserOutput, view.hardFormula);
        assertEquals(0, view.softFormula.length);
        assertEquals(0, view.softWeights.length);
    }

    @Test
    void splitWcnfSeparatesHardAndSoftByTopWeight() {
        Parser.isWCNF = true;
        Parser.topWeight = 1000;
        Parser.numClauses = 2;

        // format per clause: weight, literals..., 0
        int[] parserOutput = {
            1000, 1, 2, 0,   // hard clause (1 v 2)
            5, -1, 0         // soft clause (-1), weight 5
        };
        BitPacking.split(parserOutput);
        BitPacking.SplitView view = BitPacking.getSplitView();

        assertArrayEquals(new int[]{1, 2, 0}, view.hardFormula);
        assertArrayEquals(new int[]{-1, 0}, view.softFormula);
        assertArrayEquals(new long[]{5L}, view.softWeights);
    }

    @Test
    void splitNullProducesNullSplitData() {
        BitPacking.split(null);
        assertNull(BitPacking.combinedFormulaSplit);
        assertNull(BitPacking.clauseWeightSplit);

        BitPacking.SplitView view = BitPacking.getSplitView();
        assertNull(view.hardFormula);
        assertNull(view.softFormula);
        assertNull(view.softWeights);
    }
    // loadFromPreprocessed: UNSAT path clears all solver data

    @Test
    void loadFromPreprocessedUnsatResultClearsAllSolverData() {
        // First, load something valid so there IS state to clear.
        BitPacking.createDataBlocks(2, new int[]{1, 2, 0}, null, null, null, null);
        assertNotNull(BitPacking.hardLiterals);
        assertTrue(BitPacking.solverNumVariables > 0);

        Preprocessor.Result unsatResult =
            new Preprocessor.Result(null, 0, new boolean[3], new boolean[3], true);

        BitPacking.loadFromPreprocessed(2, unsatResult, null, null);

        assertTrue(BitPacking.unsat);
        assertEquals(0, BitPacking.solverNumVariables);
        assertNull(BitPacking.hardLiterals);
        assertNull(BitPacking.hardClauseStart);
        assertNull(BitPacking.softLiterals);
        assertNull(BitPacking.softClauseStart);
        assertNull(BitPacking.softWeights);
        assertNull(BitPacking.varData);
        assertNull(BitPacking.originalVariableID);
        assertNull(BitPacking.blockingVariable);
    }

    @Test
    void loadFromPreprocessedSatResultPopulatesDataAndSetsUnsatFalse() {
        Preprocessor.Result satResult =
            new Preprocessor.Result(new int[]{1, 2, 0}, 1, new boolean[3], new boolean[3], false);

        BitPacking.loadFromPreprocessed(2, satResult, null, null);

        assertFalse(BitPacking.unsat);
        assertNotNull(BitPacking.hardLiterals);
        assertEquals(2, BitPacking.solverNumVariables);
    }

    // originalVariableID / totalOriginalVariables bookkeeping

    @Test
    void totalOriginalVariablesReflectsInputNumVariablesEvenWhenSparse() {
        // numVariables = 100 but only variables 1 and 2 actually appear
        int[] hard = {1, 2, 0};

        BitPacking.createDataBlocks(100, hard, null, null, null, null);

        assertEquals(100, BitPacking.totalOriginalVariables);
        assertEquals(2, BitPacking.solverNumVariables); // only 2 variables actually mapped
    }

    @Test
    void originalVariableIDIndexZeroIsUnused() {
        BitPacking.createDataBlocks(1, new int[]{1, 0}, null, null, null, null);
        // originalVariableID has length solverNumVariables + 1; index 0 reserved/unused
        assertEquals(0, BitPacking.originalVariableID[0]);
    }
}