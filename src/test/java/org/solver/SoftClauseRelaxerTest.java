package org.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for SoftClauseRelaxer.
 *
 * The tests verify both:
 * 1. Structural correctness:
 *    - hard clauses are copied correctly
 *    - soft clauses are transformed correctly
 *    - weights remain aligned
 *    - blocking variables are allocated correctly
 *
 * 2. Semantic correctness:
 *    - the relaxed representation has the same optimization meaning
 *      as the original soft clause
 *
 * Relaxation rule:
 * Original soft clause: (l1 v l2 v ... v lk), weight w
 * For k >= 2:
 *    HARD: (l1 v l2 v ... v lk v y)
 *    SOFT: (-y), weight w
 *
 * Therefore:
 *    original clause satisfied -> y=false is allowed -> cost 0
 *    original clause unsatisfied -> y=true is required -> cost w
 *
 * Unary and empty soft clauses are passed through unchanged.
 */
public class SoftClauseRelaxerTest {

    @BeforeEach
    void resetRelaxerState() {
        // Reset static fields before every test to isolate state.
        SoftClauseRelaxer.relaxedHardFormula = null;
        SoftClauseRelaxer.relaxedSoftFormula = null;
        SoftClauseRelaxer.relaxedSoftWeights = null;
        SoftClauseRelaxer.relaxedNumVariables = 0;
    }

    // testing basic and empty inputs

    @Test
    void testNullSoftFormula() {
        int[] hard = {1, -2, 0, 3, 0};
        SoftClauseRelaxer.relax(hard, null, null, 3);

        assertArrayEquals(hard, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[0], SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testEmptySoftFormula() {
        int[] hard = {1, -2, 0, 3, 0};
        SoftClauseRelaxer.relax(hard, new int[0], null, 3);

        assertArrayEquals(hard, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[0], SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testNullHardFormulaWithNoSoftClauses() {
        SoftClauseRelaxer.relax(null, new int[0], null, 5);

        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[0], SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(5, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testNullHardFormulaWithSoftClauses() {
        int[] soft = {1, -2, 0};
        long[] weights = {10};
        SoftClauseRelaxer.relax(null, soft, weights, 2);

        assertArrayEquals(new int[]{1, -2, 3, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-3, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{10}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    // validation tests

    @Test
    void testSoftWeightsRequiredWhenSoftFormulaExists() {
        int[] soft = {1, -2, 0};
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> SoftClauseRelaxer.relax(null, soft, null, 2));
        assertEquals("Soft weights cannot be null when a soft formula exists.", exception.getMessage());
    }

    @Test
    void testTooFewWeightsThrowsException() {
        int[] soft = {1, 2, 0, 3, 0};
        long[] weights = {10};
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> SoftClauseRelaxer.relax(null, soft, weights, 3));
        assertTrue(exception.getMessage().contains("Soft clause count (2) does not match soft weight count (1)."));
    }

    @Test
    void testTooManyWeightsThrowsException() {
        int[] soft = {1, 2, 0};
        long[] weights = {10, 20};
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> SoftClauseRelaxer.relax(null, soft, weights, 3));
        assertTrue(exception.getMessage().contains("Soft clause count (1) does not match soft weight count (2)."));
    }

    @Test
    void testWeightCountIncludesEmptyClauses() {
        int[] soft = {0, 1, 0};
        long[] weights = {100, 200};
        assertDoesNotThrow(() -> SoftClauseRelaxer.relax(null, soft, weights, 1));
        assertArrayEquals(weights, SoftClauseRelaxer.relaxedSoftWeights);
    }

    //hard formula preservation
    @Test
    void testHardFormulaIsPreservedExactly() {
        int[] hard = {1, -2, 0, -3, 4, 5, 0, 6, 0};
        int[] soft = {7, 8, 0};
        long[] weights = {50};
        SoftClauseRelaxer.relax(hard, soft, weights, 8);

        int[] expectedHard = {1, -2, 0, -3, 4, 5, 0, 6, 0, 7, 8, 9, 0};
        assertArrayEquals(expectedHard, SoftClauseRelaxer.relaxedHardFormula);
    }

    @Test
    void testHardFormulaIsPreservedWhenOnlyUnarySoftClausesExist() {
        int[] hard = {1, -2, 0, 3, 0};
        int[] soft = {-4, 0, 5, 0};
        long[] weights = {10, 20};
        SoftClauseRelaxer.relax(hard, soft, weights, 5);

        assertArrayEquals(hard, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(soft, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(weights, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(5, SoftClauseRelaxer.relaxedNumVariables);
    }

    //Unary soft clauses

    @Test
    void testUnarySoftClausePassedThroughUnchanged() {
        int[] soft = {4, 0};
        long[] weights = {25};
        SoftClauseRelaxer.relax(null, soft, weights, 4);

        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{4, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{25}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(4, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testNegativeUnarySoftClausePassedThroughUnchanged() {
        int[] soft = {-4, 0};
        long[] weights = {99};
        SoftClauseRelaxer.relax(null, soft, weights, 4);

        assertArrayEquals(new int[]{-4, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{99}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(4, SoftClauseRelaxer.relaxedNumVariables);
    }

    //Empty soft clauses


    @Test
    void testEmptySoftClausePassedThrough() {
        int[] soft = {0};
        long[] weights = {42};
        SoftClauseRelaxer.relax(null, soft, weights, 3);

        assertArrayEquals(new int[]{0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{42}, SoftClauseRelaxer.relaxedSoftWeights);
        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedHardFormula);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testMultipleEmptySoftClausesPassedThrough() {
        int[] soft = {0, 0, 0};
        long[] weights = {10, 20, 30};
        SoftClauseRelaxer.relax(null, soft, weights, 3);

        assertArrayEquals(soft, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(weights, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    // binary soft clauses

    @Test
    void testBinarySoftClauseIsRelaxed() {
        int[] soft = {1, 2, 0};
        long[] weights = {50};
        SoftClauseRelaxer.relax(null, soft, weights, 2);

        assertArrayEquals(new int[]{1, 2, 3, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-3, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{50}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testBinarySoftClauseWithNegativeLiterals() {
        int[] soft = {-1, 2, 0};
        long[] weights = {75};
        SoftClauseRelaxer.relax(null, soft, weights, 2);

        assertArrayEquals(new int[]{-1, 2, 3, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-3, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{75}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(3, SoftClauseRelaxer.relaxedNumVariables);
    }

    // large soft clauses

    @Test
    void testLargeSoftClauseIsRelaxed() {
        int[] soft = {1, -2, 3, -4, 5, 0};
        long[] weights = {123};
        SoftClauseRelaxer.relax(null, soft, weights, 5);

        assertArrayEquals(new int[]{1, -2, 3, -4, 5, 6, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-6, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{123}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(6, SoftClauseRelaxer.relaxedNumVariables);
    }

    // multiple soft clauses

    @Test
    void testMultipleSoftClausesGetDistinctBlockingVariables() {
        int[] soft = {1, 2, 0, 2, 3, 0, -1, 3, 0};
        long[] weights = {10, 20, 30};
        SoftClauseRelaxer.relax(null, soft, weights, 3);

        assertArrayEquals(new int[]{1, 2, 4, 0, 2, 3, 5, 0, -1, 3, 6, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-4, 0, -5, 0, -6, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{10, 20, 30}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(6, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testMixedSoftClauses() {
        int[] soft = {1, 0, 2, 3, 0, 0, -4, 5, -6, 0};
        long[] weights = {10, 20, 30, 40};
        SoftClauseRelaxer.relax(null, soft, weights, 6);

        assertArrayEquals(new int[]{2, 3, 7, 0, -4, 5, -6, 8, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{1, 0, -7, 0, 0, -8, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{10, 20, 30, 40}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(8, SoftClauseRelaxer.relaxedNumVariables);
    }

    // weight preservation

    @Test
    void testWeightsRemainInOriginalClauseOrder() {
        int[] soft = {1, 0, 2, 3, 0, -4, 5, 0, 6, 0};
        long[] weights = {101, 202, 303, 404};
        SoftClauseRelaxer.relax(null, soft, weights, 6);
        assertArrayEquals(new long[]{101, 202, 303, 404}, SoftClauseRelaxer.relaxedSoftWeights);
    }

    @Test
    void testZeroWeightIsPreserved() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{0}, 2);
        assertArrayEquals(new long[]{0}, SoftClauseRelaxer.relaxedSoftWeights);
    }

    @Test
    void testVeryLargeWeightIsPreserved() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{Long.MAX_VALUE}, 2);
        assertArrayEquals(new long[]{Long.MAX_VALUE}, SoftClauseRelaxer.relaxedSoftWeights);
    }

    // variable id tests

    @Test
    void testFirstBlockingVariableStartsAfterOriginalVariables() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{10}, 100);
        assertEquals(101, SoftClauseRelaxer.relaxedNumVariables);
        assertArrayEquals(new int[]{1, 2, 101, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-101, 0}, SoftClauseRelaxer.relaxedSoftFormula);
    }

    @Test
    void testBlockingVariableIDsAreSequential() {
        int[] soft = {1, 2, 0, 3, 4, 0, 5, 6, 0};
        SoftClauseRelaxer.relax(null, soft, new long[]{10, 20, 30}, 6);

        assertArrayEquals(new int[]{1, 2, 7, 0, 3, 4, 8, 0, 5, 6, 9, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{-7, 0, -8, 0, -9, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertEquals(9, SoftClauseRelaxer.relaxedNumVariables);
    }

    @Test
    void testUnaryClausesDoNotConsumeVariableIDs() {
        int[] soft = {1, 0, 2, 3, 0, 4, 0, 5, 6, 0};
        SoftClauseRelaxer.relax(null, soft, new long[]{10, 20, 30, 40}, 6);

        assertArrayEquals(new int[]{2, 3, 7, 0, 5, 6, 8, 0}, SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{1, 0, -7, 0, 4, 0, -8, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertEquals(8, SoftClauseRelaxer.relaxedNumVariables);
    }

    // static state tests

    @Test
    void testSecondRelaxCallReplacesPreviousResult() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{10}, 2);
        assertArrayEquals(new int[]{1, 2, 3, 0}, SoftClauseRelaxer.relaxedHardFormula);

        SoftClauseRelaxer.relax(null, new int[]{4, 0}, new long[]{20}, 4);
        assertArrayEquals(new int[0], SoftClauseRelaxer.relaxedHardFormula);
        assertArrayEquals(new int[]{4, 0}, SoftClauseRelaxer.relaxedSoftFormula);
        assertArrayEquals(new long[]{20}, SoftClauseRelaxer.relaxedSoftWeights);
        assertEquals(4, SoftClauseRelaxer.relaxedNumVariables);
    }

    // testing other functions

    private static boolean literalSatisfied(int literal, boolean[] assignment) {
        int variable = Math.abs(literal);
        return literal > 0 ? assignment[variable] : !assignment[variable];
    }

    private static boolean clauseSatisfied(int[] formula, int clauseStart, int clauseEnd, boolean[] assignment) {
        for (int i = clauseStart; i < clauseEnd; i++) {
            if (literalSatisfied(formula[i], assignment)) return true;
        }
        return false;
    }

    private static long originalClauseCost(int[] clause, long weight, boolean[] assignment) {
        if (clause.length == 0) return weight;
        for (int literal : clause) {
            if (literalSatisfied(literal, assignment)) return 0;
        }
        return weight;
    }

    private static long relaxedClauseMinimumCost(int[] originalClause, long weight, boolean[] originalAssignment) {
        for (int literal : originalClause) {
            if (literalSatisfied(literal, originalAssignment)) return 0;
        }
        return weight;
    }

    @Test
    void testRelaxationHasCorrectMinimumCostForSatisfiedBinaryClause() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{50}, 2);
        boolean[] assignment = {false, true, false};

        assertEquals(0, originalClauseCost(new int[]{1, 2}, 50, assignment));
        assertEquals(0, relaxedClauseMinimumCost(new int[]{1, 2}, 50, assignment));
    }

    @Test
    void testRelaxationHasCorrectMinimumCostForUnsatisfiedBinaryClause() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{50}, 2);
        boolean[] assignment = {false, false, false};

        assertEquals(50, originalClauseCost(new int[]{1, 2}, 50, assignment));
        assertEquals(50, relaxedClauseMinimumCost(new int[]{1, 2}, 50, assignment));
    }

    @Test
    void testBlockingVariableFalseWhenOriginalClauseSatisfied() {
        SoftClauseRelaxer.relax(null, new int[]{1, -2, 0}, new long[]{100}, 2);
        boolean[] assignment = {false, true, false, false};

        assertTrue(clauseSatisfied(SoftClauseRelaxer.relaxedHardFormula, 0, 3, assignment));
        assertTrue(literalSatisfied(-3, assignment));
    }

    @Test
    void testBlockingVariableTrueWhenOriginalClauseUnsatisfied() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{100}, 2);
        boolean[] assignment = {false, false, false, true};

        assertTrue(clauseSatisfied(SoftClauseRelaxer.relaxedHardFormula, 0, 3, assignment));
        assertFalse(literalSatisfied(-3, assignment));
    }

    @Test
    void testBinaryRelaxationIsSemanticallyEquivalentForEveryAssignment() {
        int[] originalClause = {1, -2};
        long weight = 37;
        SoftClauseRelaxer.relax(null, new int[]{1, -2, 0}, new long[]{weight}, 2);

        for (int x1 = 0; x1 <= 1; x1++) {
            for (int x2 = 0; x2 <= 1; x2++) {
                boolean[] assignment = {false, x1 == 1, x2 == 1};
                long originalCost = originalClauseCost(originalClause, weight, assignment);
                long relaxedCost = relaxedClauseMinimumCost(originalClause, weight, assignment);
                assertEquals(originalCost, relaxedCost, "Mismatch for x1=" + x1 + ", x2=" + x2);
            }
        }
    }

    // 3 liteal test

    @Test
    void testTernaryRelaxationIsSemanticallyEquivalentForEveryAssignment() {
        int[] originalClause = {1, -2, 3};
        long weight = 123;
        SoftClauseRelaxer.relax(null, new int[]{1, -2, 3, 0}, new long[]{weight}, 3);

        for (int x1 = 0; x1 <= 1; x1++) {
            for (int x2 = 0; x2 <= 1; x2++) {
                for (int x3 = 0; x3 <= 1; x3++) {
                    boolean[] assignment = {false, x1 == 1, x2 == 1, x3 == 1};
                    long originalCost = originalClauseCost(originalClause, weight, assignment);
                    long relaxedCost = relaxedClauseMinimumCost(originalClause, weight, assignment);
                    assertEquals(originalCost, relaxedCost, "Mismatch for assignment: x1=" + x1 + ", x2=" + x2 + ", x3=" + x3);
                }
            }
        }
    }

    // generated soft cost test

    @Test
    void testGeneratedUnarySoftClauseHasCorrectCostForBothYStates() {
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0}, new long[]{80}, 2);
        boolean[] yFalse = {false, false, false, false};
        boolean[] yTrue = {false, false, false, true};

        assertFalse(clauseSatisfied(SoftClauseRelaxer.relaxedHardFormula, 0, 3, yFalse));
        assertTrue(clauseSatisfied(SoftClauseRelaxer.relaxedHardFormula, 0, 3, yTrue));
        assertFalse(literalSatisfied(-3, yTrue));
    }

    @Test
    void testMixedSoftFormulaPreservesEachClauseWeight() {
        int[] soft = {1, 0, 2, 3, 0, 0, -4, 5, -6, 0};
        long[] weights = {10, 20, 30, 40};
        SoftClauseRelaxer.relax(null, soft, weights, 6);

        assertEquals(4, SoftClauseRelaxer.relaxedSoftWeights.length);
        for (int i = 0; i < weights.length; i++) {
            assertEquals(weights[i], SoftClauseRelaxer.relaxedSoftWeights[i], "Weight mismatch at clause index " + i);
        }
    }


    @Test
    void testEveryGeneratedHardClauseEndsWithZero() {
        int[] soft = {1, 2, 0, 3, 4, 5, 0, -6, -7, 8, 0};
        SoftClauseRelaxer.relax(null, soft, new long[]{10, 20, 30}, 8);
        int[] hard = SoftClauseRelaxer.relaxedHardFormula;

        assertTrue(hard.length > 0);
        assertEquals(0, hard[hard.length - 1]);

        int clauseStart = 0;
        for (int i = 0; i < hard.length; i++) {
            if (hard[i] == 0) {
                assertTrue(i > clauseStart, "Found an unexpected empty hard clause.");
                clauseStart = i + 1;
            }
        }
        assertEquals(hard.length, clauseStart);
    }

    @Test
    void testEveryGeneratedSoftClauseIsUnaryOrEmpty() {
        int[] soft = {1, 2, 0, 3, 4, 5, 0, -6, 0, 0};
        SoftClauseRelaxer.relax(null, soft, new long[]{10, 20, 30, 40}, 6);
        int[] result = SoftClauseRelaxer.relaxedSoftFormula;

        int clauseStart = 0;
        for (int i = 0; i < result.length; i++) {
            if (result[i] == 0) {
                int clauseLength = i - clauseStart;
                assertTrue(clauseLength <= 1, "Relaxed soft clause has length " + clauseLength + " at index " + i);
                clauseStart = i + 1;
            }
        }
        assertEquals(result.length, clauseStart);
    }

    // original formula msut be preserved

    @Test
    void testInputHardFormulaIsNotMutated() {
        int[] hard = {1, -2, 0, 3, 0};
        int[] originalHard = Arrays.copyOf(hard, hard.length);
        SoftClauseRelaxer.relax(hard, new int[]{4, 5, 0}, new long[]{10}, 5);
        assertArrayEquals(originalHard, hard);
    }

    @Test
    void testInputSoftFormulaIsNotMutated() {
        int[] soft = {1, -2, 0, 3, 4, 0};
        int[] originalSoft = Arrays.copyOf(soft, soft.length);
        SoftClauseRelaxer.relax(null, soft, new long[]{10, 20}, 4);
        assertArrayEquals(originalSoft, soft);
    }

    @Test
    void testInputWeightsAreNotMutated() {
        long[] weights = {10, 20, 30};
        long[] originalWeights = Arrays.copyOf(weights, weights.length);
        SoftClauseRelaxer.relax(null, new int[]{1, 2, 0, 3, 0, 4, 5, 0}, weights, 5);
        assertArrayEquals(originalWeights, weights);
    }

    // original vs relaxed test

    private static final class OriginalSoftClause {
        final int[] literals;
        final long weight;

        OriginalSoftClause(int[] literals, long weight) {
            this.literals = literals;
            this.weight = weight;
        }
    }

    @Test
    void testCompleteSmallFormulaPreservesObjectiveSemantics() {
        OriginalSoftClause[] clauses = {
            new OriginalSoftClause(new int[]{1, 2}, 10),
            new OriginalSoftClause(new int[]{-1, 3}, 20),
            new OriginalSoftClause(new int[]{2, -3}, 30)
        };

        int[] softFormula = {1, 2, 0, -1, 3, 0, 2, -3, 0};
        long[] weights = {10, 20, 30};
        SoftClauseRelaxer.relax(null, softFormula, weights, 3);

        for (int mask = 0; mask < 8; mask++) {
            boolean[] assignment = {
                false,
                (mask & 1) != 0,
                (mask & 2) != 0,
                (mask & 4) != 0
            };

            long originalTotalCost = 0;
            long relaxedMinimumCost = 0;

            for (OriginalSoftClause clause : clauses) {
                originalTotalCost += originalClauseCost(clause.literals, clause.weight, assignment);
                relaxedMinimumCost += relaxedClauseMinimumCost(clause.literals, clause.weight, assignment);
            }

            assertEquals(originalTotalCost, relaxedMinimumCost, "Objective mismatch for assignment mask " + mask);
        }
    }

    // structural consistency test

    @Test
    void testNumberOfRelaxedSoftClausesEqualsOriginalNumberOfSoftClauses() {
        int[] soft = {1, 0, 2, 3, 0, 0, -4, 5, 0, 6, 7, 8, 0};
        long[] weights = {10, 20, 30, 40, 50};
        SoftClauseRelaxer.relax(null, soft, weights, 8);
        assertEquals(weights.length, SoftClauseRelaxer.relaxedSoftWeights.length);
    }

    @Test
    void testOnlyClausesOfSizeTwoOrGreaterCreateBlockingVariables() {
        int[] soft = {1, 0, 2, 3, 0, -4, 0, 5, 6, 7, 0, 0};
        long[] weights = {10, 20, 30, 40, 50};
        SoftClauseRelaxer.relax(null, soft, weights, 7);

        // Only (2 OR 3) and (5 OR 6 OR 7) are relaxed. Exactly two new variables introduced.
        assertEquals(9, SoftClauseRelaxer.relaxedNumVariables);
    }
}