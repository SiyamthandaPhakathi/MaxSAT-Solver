package org.solver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;


/**
 * 
 * Parser
 */
public class Parser{
    static int numVariables;
    static int numClauses;
    static int topWeight;
    static boolean isWCNF;

    public static int[] parse(String filename) throws IOException{ 

         int[] buffer = new int[10000]; // Initial buffer that stores 10000 objects
         int size = 0; // keep track of size used
         int clauseCount = 0; // count of terminal 0s
         boolean headerSeen = false;
         int lineNumber = 0;
         boolean clauseOpen = false;
         
         isWCNF = false; //Reset between calls
         

         try (BufferedReader reader = new BufferedReader(new FileReader(filename))){
            String line;

            while ((line = reader.readLine() )!= null){

                lineNumber++;
                line = line.trim(); // remmove trailing whitespaces,tabs, newlines

                // Ignore comment lines and empty lines
                if (line.isEmpty() || line.startsWith("c") || line.startsWith("%")){
                    if (line.startsWith("%")) {break;}
                    continue;
                }

                // problem lines 
                if (line.startsWith("p")){
                    String[] parts = line.split("\\s+");
                    if (!parts[0].equals("p") || !parts[1].equals("cnf") && !parts[1].equals("wcnf") ){ 
                        throw new IOException("Invalid header: Expected format: p cnf <numVariables> <numClauses> or p wcnf <numVariables> <numClauses> <topWeight>");
                    }

                    if (parts[1].equals("cnf") && parts.length != 4) {
                        throw new IOException("Invalid header: Expected format: p cnf <numVariables> <numClauses>");
                    }

                    if (parts[1].equals("wcnf") && parts.length != 5) {
                        throw new IOException("Invalid header: Expected format: p wcnf <numVariables> <numClauses> <topWe");
                    }

                    isWCNF = parts[1].equals("wcnf");


                    try{
                    
                    numVariables = Integer.parseInt(parts[2]); // Have to call parse first before calling numVariables
                    numClauses = Integer.parseInt(parts[3]);
                    if (isWCNF){
                        topWeight = Integer.parseInt(parts[4]);
                    }
                    }catch(NumberFormatException e){
                        throw new IOException("Invalid header: <numVariables> <numClauses> <topWeight> must be integers", e);
                    }

                    if (numVariables < 0 || numClauses < 0 ){
                        throw new IOException("Invalid header: <numVariables> <numClauses> must be non negative");
                    }

                    if (isWCNF && topWeight < 0){
                        throw new IOException("Invalid header: <topWeight> must be non negative");
                    }

                    headerSeen = true;
                    continue;

                }

                if (!headerSeen){
                    throw new IOException("Clause on line " + lineNumber + ": before problem line - p cnf or p wcnf");
                }

                String[] tokens = line.split("\\s+");
                int startIdx = 0;
                

                if (isWCNF && !clauseOpen){
                    if (tokens.length == 0 || tokens[0].isEmpty()){
                        throw new IOException("Missing weight on clause at line" + lineNumber);
                    }
                    int weight;
                    try {
                        weight = Integer.parseInt(tokens[0]);
                    } catch(NumberFormatException e){
                        throw new IOException("Invalid weight " + tokens[0] + "at " + lineNumber, e);
                    }
                    if (weight <= 0){
                        throw new IOException("Invalid weight at " + lineNumber + "must be positive");
                    }

                    buffer = ensureCapacity(buffer, size);
                    buffer[size++] = weight;

                    startIdx = 1;


                }


                

                for (int i = startIdx; i < tokens.length; i++){
                    String token = tokens[i];
                    int literal;

                    try {
                         literal = Integer.parseInt(token);
                    }catch(NumberFormatException e){
                        throw new IOException("Invalid literal <" + token + "> at line" + lineNumber, e);
                    }
                    // terminating 0
                    if (literal == 0){ 
                        
                        buffer = ensureCapacity(buffer, size);
                        buffer[size++] = 0;
                        clauseCount++;
                        clauseOpen = false;
                        
                        break;
                        
                        
                    }

                    if (Math.abs(literal) > numVariables) {
                        throw new IOException("Literal " + literal + " at line " + lineNumber
                                + " exceeds declared number of variables (" + numVariables + ")");
                    }

                    buffer = ensureCapacity(buffer, size);
                    buffer[size++] = literal;
                    clauseOpen = true;
                    

                }

            }

         }

         if (!headerSeen){
            throw new IOException("File has no problem header: p cnf <numVariables> <numClauses");
         }

         if (clauseOpen){
            throw new IOException("File ended before clause terminated with 0");
         }

         if (clauseCount != numClauses){
            throw new IOException("Clause count mismatch: header declares " + numClauses + " clause(s) but " + clauseCount + " were found");
        }

        

         // Trim the array to its actual size 
         return Arrays.copyOf(buffer, size);
    }

    private static int[] ensureCapacity(int[] buffer, int size){
        if (size >= buffer.length){
            buffer = Arrays.copyOf(buffer, buffer.length * 2);
        }
        return buffer;
    }

}

