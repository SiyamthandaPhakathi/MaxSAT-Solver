package org.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RouletteSelectorTest {

    @Test
    void selectPicksFirstCandidateWhenRngIsZero() {
        int[] candidates = {10, 20, 30};
        double[] weights = {1.5, 2.0, 1.5};
        
        // rng = 0L produces 0.0 from toUnitDouble.
        // r = 0.0 * sum = 0.0. 
        // 0.0 <= weights[0] (1.5) is true, so it should pick the first element.
        int selected = RouletteSelector.select(candidates, weights, 0L);
        
        assertEquals(10, selected, "Should pick the first candidate when RNG yields 0.0");
    }

    @Test
    void selectPicksLastCandidateWhenRngIsMax() {
        int[] candidates = {10, 20, 30};
        double[] weights = {1.5, 2.0, 1.5};
        
        // rng = -1L (all bits set to 1) produces ~0.9999999999999999 from toUnitDouble.
        // r will be just barely less than the sum (5.0).
        // It will bypass the first two elements and hit the final element.
        int selected = RouletteSelector.select(candidates, weights, -1L);
        
        assertEquals(30, selected, "Should pick the last candidate when RNG yields ~1.0");
    }

    @Test
    void selectPicksMiddleCandidateWithHalfRng() {
        int[] candidates = {10, 20, 30};
        double[] weights = {1.0, 2.0, 1.0};
        
        // Long.MIN_VALUE (1L << 63) shifted right by 11 gives exactly 0.5 in the toUnitDouble logic.
        // sum = 4.0. r = 0.5 * 4.0 = 2.0.
        // i=0: acc=1.0 (2.0 <= 1.0 -> false)
        // i=1: acc=3.0 (2.0 <= 3.0 -> true) -> picks 20
        int selected = RouletteSelector.select(candidates, weights, Long.MIN_VALUE);
        
        assertEquals(20, selected, "Should pick the middle candidate when RNG yields 0.5");
    }

    @Test
    void selectHandlesSingleCandidateCorrectly() {
        int[] candidates = {42};
        double[] weights = {100.0};
        
        assertEquals(42, RouletteSelector.select(candidates, weights, 0L));
        assertEquals(42, RouletteSelector.select(candidates, weights, -1L));
        assertEquals(42, RouletteSelector.select(candidates, weights, Long.MIN_VALUE));
    }

    @Test
    void selectSkipsZeroWeightCandidates() {
        int[] candidates = {10, 20, 30};
        // Candidate 10 and 30 have 0 weight, so 20 is the only viable choice 
        // for any RNG value that produces a non-zero unit double.
        double[] weights = {0.0, 5.0, 0.0};
        
        // We use Long.MIN_VALUE to get 0.5. r = 0.5 * 5.0 = 2.5
        // i=0: acc=0.0 (2.5 <= 0.0 -> false)
        // i=1: acc=5.0 (2.5 <= 5.0 -> true) -> picks 20
        int selected = RouletteSelector.select(candidates, weights, Long.MIN_VALUE);
        
        assertEquals(20, selected, "Should skip candidates with zero weight");
    }

    @Test
    void selectDefaultsToFirstWhenAllWeightsAreZero() {
        int[] candidates = {10, 20, 30};
        double[] weights = {0.0, 0.0, 0.0};
        
        // sum = 0.0, so r = 0.0 regardless of rng.
        // On the first iteration, acc = 0.0, and (0.0 <= 0.0) is true.
        // It should return the first candidate.
        int selected = RouletteSelector.select(candidates, weights, -1L);
        
        assertEquals(10, selected, "Should return the first candidate if all weights are zero");
    }
}