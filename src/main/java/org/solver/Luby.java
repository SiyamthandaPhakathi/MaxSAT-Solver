package org.solver;

/**
 * Generates the Luby restart sequence
 * 
 * Sequence is used to schedule restarts for stochastic local search
 * Luby
 */
public final class Luby {
    private Luby(){}; 

    /**
     * Computes i-th value of the Luby sequence 
     * @param i index of luby sequence
     * @return Luby sequence value at i
     */
    public static long sequence(long i){
        int k = 1;
        while ((1L << k) - 1 <= i)k++;
        k--;
        if (i == (1L<<k)-1){
            return 1L<< (k-1);
        }
        return sequence(i-(1L<<k)+1);

    }

    /**
     * Computes number of flips permitted for a restart using Luby
     * @param i ith index
     * @param unit base number of flips associated with 1 luby unit
     * @return flip budget for specified restart
     */
    public static long flipBudget(int i, long unit){
        return sequence(i)*unit;
    }

    
}
