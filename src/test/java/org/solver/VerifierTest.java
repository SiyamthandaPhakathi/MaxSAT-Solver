package org.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VerifierTest {

    // Test verify cnf

    @Test
    void verifyCnfThrowsExceptionOnNullAssignment() {
        int[] formula = {1, 0};
        assertThrows(IllegalArgumentException.class, () -> Verifier.verifyCNF(null, formula));
    }

    @Test
    void verifyCnfThrowsExceptionOnNullFormula() {
        boolean[] assignment = {false, true};
        assertThrows(IllegalArgumentException.class, () -> Verifier.verifyCNF(assignment, null));
    }

   
    @Test
    void verifyCnfThrowsExceptionOnOutOfBoundsVariableId() {
        boolean[] assignment = {false, true}; // length 2 (indices 0, 1)
        int[] formula = {2, 0}; // varID 2 is >= length 2
        assertThrows(IllegalArgumentException.class, () -> Verifier.verifyCNF(assignment, formula));
    }

    @Test
    void verifyCnfReturnsFalseOnEmptyClause() {
        boolean[] assignment = {false, true};
        int[] formula = {0}; // Empty clause
        assertFalse(Verifier.verifyCNF(assignment, formula));
    }

    @Test
    void verifyCnfReturnsFalseOnUnsatisfiedClause() {
        boolean[] assignment = {false, false}; // var 1 is false
        int[] formula = {1, 0}; // positive 1 requires var 1 to be true
        assertFalse(Verifier.verifyCNF(assignment, formula));
    }

    @Test
    void verifyCnfReturnsFalseOnMissingZeroDelimiter() {
        boolean[] assignment = {false, true};
        int[] formula = {1}; // Missing terminating 0
        assertFalse(Verifier.verifyCNF(assignment, formula));
    }

    @Test
    void verifyCnfReturnsTrueWhenAllClausesSatisfied() {
        boolean[] assignment = {false, true, false}; // var 1 = T, var 2 = F
        int[] formula = {1, 2, 0, -2, 0}; // Clause 1: (1 v 2) -> T v F = T. Clause 2: (-2) -> !F = T.
        assertTrue(Verifier.verifyCNF(assignment, formula));
    }

    // test verify woorks for cnf

    @Test
    void verifyAliasWorksSameAsVerifyCnf() {
        boolean[] assignment = {false, true};
        int[] formula = {1, 0};
        assertTrue(Verifier.verify(assignment, formula));
    }

    // verify wcnf

    @Test
    void verifyWcnfThrowsExceptionOnNullAssignment() {
        assertThrows(IllegalArgumentException.class, () -> Verifier.verifyWCNF(null, new int[]{1, 0}, new int[]{2, 0}, new long[]{5}));
    }

    @Test
    void verifyWcnfThrowsExceptionOnNullHardFormula() {
        assertThrows(IllegalArgumentException.class, () -> Verifier.verifyWCNF(new boolean[]{false, true}, null, new int[]{2, 0}, new long[]{5}));
    }

    @Test
    void verifyWcnfReturnsFalseIfHardFormulaNotSatisfied() {
        boolean[] assignment = {false, false}; // var 1 = F
        int[] hardFormula = {1, 0}; // expects var 1 = T
        int[] softFormula = {};
        long[] softWeights = {};
        
        assertFalse(Verifier.verifyWCNF(assignment, hardFormula, softFormula, softWeights));
    }

    @Test
    void verifyWcnfReturnsTrueIfHardFormulaSatisfied() {
        boolean[] assignment = {false, true}; // var 1 = T
        int[] hardFormula = {1, 0}; 
        int[] softFormula = {-1, 0}; // violated soft clause
        long[] softWeights = {10L};
        
        // As long as hard formula is satisfied, verifyWCNF returns true
        assertTrue(Verifier.verifyWCNF(assignment, hardFormula, softFormula, softWeights));
    }

    // test the soft cost calc

    @Test
    void calculateSoftCostThrowsExceptionOnNullAssignment() {
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(null, new int[]{}, new long[]{}));
    }

    @Test
    void calculateSoftCostReturnsZeroOnNullSoftFormula() {
        boolean[] assignment = {false, true};
        assertEquals(0L, Verifier.calculateSoftCost(assignment, null, new long[]{}));
    }

    @Test
    void calculateSoftCostThrowsExceptionOnNullWeightsWithExistingFormula() {
        boolean[] assignment = {false, true};
        int[] softFormula = {1, 0};
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(assignment, softFormula, null));
    }

    @Test
    void calculateSoftCostThrowsExceptionOnMismatchedClauseAndWeightCount() {
        boolean[] assignment = {false, true};
        int[] softFormula = {1, 0, -1, 0}; // 2 clauses
        long[] softWeights = {5L}; // 1 weight
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }

    @Test
    void calculateSoftCostThrowsExceptionOnMissingZeroDelimiter() {
        boolean[] assignment = {false, true};
        int[] softFormula = {1}; // missing 0
        long[] softWeights = {5L}; 
        // 0 clauses counted by countClauses, but weight array has length 1 -> triggers mismatch exception first
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(assignment, softFormula, softWeights));
        
        // To bypass mismatch and hit the delimiter check:
        int[] softFormulaNoDelim = {1, 0, 1}; // 1 valid clause + trailing literal
        long[] softWeightsTwo = {5L}; 
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(assignment, softFormulaNoDelim, softWeightsTwo));
    }

    @Test
    void calculateSoftCostAddsZeroForSatisfiedClauses() {
        boolean[] assignment = {false, true, false}; // var 1=T, var 2=F
        int[] softFormula = {1, 0, -2, 0}; // Clause 1 satisfied, Clause 2 satisfied
        long[] softWeights = {10L, 20L};
        
        assertEquals(0L, Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }

    @Test
    void calculateSoftCostAddsWeightForUnsatisfiedClauses() {
        boolean[] assignment = {false, false, true}; // var 1=F, var 2=T
        int[] softFormula = {1, 0, -2, 0}; // Clause 1 violated, Clause 2 violated
        long[] softWeights = {10L, 20L};
        
        assertEquals(30L, Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }

    @Test
    void calculateSoftCostAddsWeightForEmptyClauses() {
        boolean[] assignment = {false, true}; 
        int[] softFormula = {0}; // Empty clause always violated
        long[] softWeights = {15L};
        
        assertEquals(15L, Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }

    @Test
    void calculateSoftCostThrowsExceptionOnNegativeWeight() {
        boolean[] assignment = {false, false}; 
        int[] softFormula = {1, 0}; // Violated
        long[] softWeights = {-5L};
        
        assertThrows(IllegalArgumentException.class, () -> Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }

    @Test
    void calculateSoftCostThrowsExceptionOnOverflow() {
        boolean[] assignment = {false, false, false}; 
        int[] softFormula = {1, 0, 2, 0}; // Both violated
        long[] softWeights = {Long.MAX_VALUE - 10, 15L}; // Sum exceeds Long.MAX_VALUE
        
        assertThrows(ArithmeticException.class, () -> Verifier.calculateSoftCost(assignment, softFormula, softWeights));
    }
}