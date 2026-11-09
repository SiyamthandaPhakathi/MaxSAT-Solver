package org.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LubyTest {

    @Test
    void testSequenceGeneratesStandardLubyValues() {
        // The standard Luby sequence starts as: 
        // 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8...
        long[] expectedSequence = {
            1, // i = 1
            1, // i = 2
            2, // i = 3
            1, // i = 4
            1, // i = 5
            2, // i = 6
            4, // i = 7
            1, // i = 8
            1, // i = 9
            2, // i = 10
            1, // i = 11
            1, // i = 12
            2, // i = 13
            4, // i = 14
            8  // i = 15
        };

        for (int i = 0; i < expectedSequence.length; i++) {
            assertEquals(expectedSequence[i], Luby.sequence(i + 1), 
                "Mismatch at Luby sequence index " + (i + 1));
        }
    }

    @Test
    void testSequenceAtPowersOfTwoMinusOne() {
        // Mathematically, the Luby sequence at index (2^k - 1) always equals 2^(k-1)
        assertEquals(1, Luby.sequence(1));       // 2^1 - 1
        assertEquals(2, Luby.sequence(3));       // 2^2 - 1
        assertEquals(4, Luby.sequence(7));       // 2^3 - 1
        assertEquals(8, Luby.sequence(15));      // 2^4 - 1
        assertEquals(16, Luby.sequence(31));     // 2^5 - 1
        assertEquals(32, Luby.sequence(63));     // 2^6 - 1
        assertEquals(1024, Luby.sequence(2047)); // 2^11 - 1
    }

    @Test
    void testFlipBudgetAppliesUnitMultiplier() {
        long unit = 500;
        
        // Luby(1) = 1 -> budget = 1 * 500
        assertEquals(500, Luby.flipBudget(1, unit));
        
        // Luby(2) = 1 -> budget = 1 * 500
        assertEquals(500, Luby.flipBudget(2, unit));
        
        // Luby(3) = 2 -> budget = 2 * 500
        assertEquals(1000, Luby.flipBudget(3, unit));
        
        // Luby(7) = 4 -> budget = 4 * 500
        assertEquals(2000, Luby.flipBudget(7, unit));
        
        // Luby(15) = 8 -> budget = 8 * 500
        assertEquals(4000, Luby.flipBudget(15, unit));
    }
    
    @Test
    void testFlipBudgetWithZeroUnit() {
        // If the base unit is 0, the budget should always be 0
        assertEquals(0, Luby.flipBudget(1, 0));
        assertEquals(0, Luby.flipBudget(7, 0));
        assertEquals(0, Luby.flipBudget(15, 0));
    }
}