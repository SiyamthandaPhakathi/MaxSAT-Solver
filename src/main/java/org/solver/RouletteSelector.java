package org.solver;

public final class RouletteSelector {

    private RouletteSelector(){}


    /**
     * 
     * @param candidateVars The candidate variables 
     * @param weights 
     * @param rng Random long which can either use XOR shift or Javas random class
     * @return The candidate variable to select
     */
    public static int select(int[] candidateVars, double[] weights, long rng){
        
        double sum = 0;

        for (double w: weights){
            sum += w;
        }

        double r = toUnitDouble(rng) * sum;
        double acc = 0.0;
        for (int i = 0; i < weights.length; i++){
            acc += weights[i];
            if (r <= acc) return candidateVars[i];
        }
        return candidateVars[weights.length -1];
        
    }

    /**
     * Generates a random double from [0,1) from a random long 
     * Used ChatGPT to helo with the bit shifting logic 
     * @param randomLong
     * @return Random double from [0,1)
     */
    private static double toUnitDouble(long randomLong){
        return (randomLong>>>11)* (1.0/(1L<<53));
    }



    
}
