package org.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;



public class ParserTest {
    @TempDir
    Path tempDir;

    // Temporary file direct ory for testing
    private String writeFile(String name, String content) throws IOException{
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file.toString(); 
    }

    // Verify CNF file 
    @Test 
    void parsesValidCnfFile() throws IOException{
        String content = """
                         p cnf 3 2
                         1 -3 0
                         2 3 -1 0
                         """;
        String path = writeFile("valid.cnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{1,-3,0,2,3,-1,0}, result);
    }

    // Non existent file throws exception
    @Test 
    void nonExistentFile(){
        String path = tempDir.resolve("doesnotexist.cnf").toString();
        assertThrows(IOException.class, () -> Parser.parse(path));

        
    }


    // Verify numClauses and numVariables in header
    @Test
    void readHeaderCounts() throws IOException{
        String content = """
                         p cnf 5 1
                         1 2 3 4 5 0
                         """;
        String path = writeFile("headercounts.cnf",content);
        Parser.parse(path);

        assertEquals(5, Parser.numVariables);
        assertEquals(1, Parser.numClauses);
    }

    // Handles no clauses
    @Test 
    void noClause() throws IOException{
        String content = "p cnf 3 0\n";
        String path = writeFile("noclause.cnf", content);
        int[] result = Parser.parse(path);

        assertEquals(3, Parser.numVariables);
        assertEquals(0, Parser.numClauses);
        assertEquals(0, result.length);
    }

    // Handles and skips comment lines
    @Test
    void commentLinesTest() throws IOException{
    String content = """
                     c comment line
                     c comment line 2
                     p cnf 3 2
                     c comment line 3
                     1 -2 0
                     2 3 -1 0
                     """;
        String path = writeFile("valid.cnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{1,-2,0,2,3,-1,0}, result);
    }

    // Test that tautology are preserved
    @Test
    void tautologyPreserved() throws IOException{
        String content = """
                         p cnf 2 3
                         1 -1 0
                         1 2 0
                         -2 0
                         """;
        String path = writeFile("valid.cnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{1,-1,0,1,2,0,-2,0}, result);
    }

    // Missing headers are thrown
    @Test
    void throwsOnMissingHeader() throws IOException{
        String content = "1 2 3 0\n";
        String path = writeFile("missingheader.cnf", content);
        
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with missing tokens are thrown
    @Test
    void throwsOnHeaderWithLessTokens() throws IOException{
        String content = "p cnf 1\n1 2 0\n";
        String path = writeFile("fewertokens.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with wrong format name thrown
    @Test
    void throwsOnHeaderWithWrongformat() throws IOException{
        String content = "p sat 2 1\n1 2 0\n";
        String path = writeFile("wrongformatname.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with wrong numeric type in header thrown 
    @Test
    void throwsOnHeaderWithNonNumericType() throws IOException{
        String content = "p cnf two one\n1 2 0\n";
        String path = writeFile("nonnumeric.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with negative variable or clauses thrown
    @Test
    void throwsOnHeaderWithNegatives () throws IOException{
        String content = "p cnf 2 -1\n1 2 0\n";
        String path = writeFile("negativesinheadder.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers that have clauses before them are thrown
    @Test
    void throwsOnHeaderAfterClauses() throws IOException{
        String content = "1 2 0\np cnf 2 1\n";
        String path = writeFile("clausebeforeheader.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Missing terminator 0 at end of file are ignored
    @Test
    void throwsOnMissingTerminator() throws IOException{
        String content = "p cnf 3 1\n1 2\n3\n";
        String path = writeFile("missingterminator.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Bare line 0 should be treated as empty clause
    @Test
    void bareZeroLinePermits() throws IOException{
        String content = "p cnf 2 1\n0\n";
        String path = writeFile("barezeroline.cnf", content);
        int[] result = Parser.parse(path);

        assertArrayEquals(new int[]{0}, result);
    }

    // throws when numClauses mismatches with actual number of clauses numClause<actual
    @Test
    void throwsOnNumClauseMismatchMore() throws IOException{
        String content = "p cnf 3 1\n1 2 0\n2 3 0\n"; // 1 clause stated but two clauses present
        String path = writeFile("mismatchedMore.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // throws when numClauses mismatches with actual number of clauses numClause>actual
    @Test
    void throwsOnNumClauseMismatchLess() throws IOException{
        String content = "p cnf 2 2\n1 2 0\n"; // 2 clause stated but 1 clauses present
        String path = writeFile("mismatchedLess.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // throws when variable is out of declared range
    @Test
    void throwsOnOutOfRangeVariable() throws IOException{
        String content = "p cnf 2 1\n1 4 0\n"; // declared 2 but 4 is out of range
        String path = writeFile("outofrangevar.cnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

     // Verify CNF file 
    @Test 
    void parsesValidWcnfFile() throws IOException{
        String content = """
                         p wcnf 3 2 100
                         10 1 2 0
                         100 -2 -3 0
                         """;
        String path = writeFile("valid.wcnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{10,1,2,0,100,-2,-3,0}, result);
    }

    // Non existent file throws exception
    @Test 
    void nonExistentWcnfFile(){
        String path = tempDir.resolve("doesnotexist.wcnf").toString();
        assertThrows(IOException.class, () -> Parser.parse(path));

        
    }


    // Verify numClauses and numVariables in header
    @Test
    void readWcnfHeaderCounts() throws IOException{
        String content = """
                         p wcnf 5 1 10
                         10 1 2 3 4 5 0
                         """;
        String path = writeFile("headercounts.wcnf",content);
        Parser.parse(path);

        assertEquals(5, Parser.numVariables);
        assertEquals(1, Parser.numClauses);
    }

    // Handles no clauses
    @Test 
    void noClauseWcnf() throws IOException{
        String content = "p wcnf 3 0 3\n";
        String path = writeFile("noclause.wcnf", content);
        int[] result = Parser.parse(path);

        assertEquals(3, Parser.numVariables);
        assertEquals(0, Parser.numClauses);
        assertEquals(0, result.length);
    }

    // Handles and skips comment lines
    @Test
    void commentLinesTestWcnf() throws IOException{
    String content = """
                     c comment line
                     c comment line 2
                     p wcnf 3 2 10
                     c comment line 3
                     5 1 -2 0
                     3 2 3 -1 0
                     """;
        String path = writeFile("valid.wcnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{5,1,-2,0,3,2,3,-1,0}, result);
    }

    // Test that tautology are preserved
    @Test
    void tautologyPreservedWcnf() throws IOException{
        String content = """
                         p wcnf 2 3 50
                         20 1 -1 0
                         30 1 2 0
                         1 -2 0
                         """;
        String path = writeFile("valid.wcnf", content);
        int[] result = Parser.parse(path);
        
        assertArrayEquals(new int[]{20,1,-1,0,30,1,2,0,1,-2,0}, result);
    }

    // Missing headers are thrown
    @Test
    void throwsOnMissingHeaderWcnf() throws IOException{
        String content = "10 1 2 3 0\n";
        String path = writeFile("missingheader.wcnf", content);
        
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with missing tokens are thrown
    @Test
    void throwsOnHeaderWithLessTokensWcnf() throws IOException{
        String content = "p wcnf 1\n1 2 0\n";
        String path = writeFile("fewertokens.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with wrong format name thrown
    @Test
    void throwsOnHeaderWithWrongformatWcnf() throws IOException{
        String content = "p sat 2 1\n1 2 0\n";
        String path = writeFile("wrongformatname.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with wrong numeric type in header thrown 
    @Test
    void throwsOnHeaderWithNonNumericTypeWcnf() throws IOException{
        String content = "p cnf two one hundred\n1 2 0\n";
        String path = writeFile("nonnumeric.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers with negative variable or clauses thrown
    @Test
    void throwsOnHeaderWithNegativesTopWeight() throws IOException{
        String content = "p wcnf 2 2 -10\n1 2 0\n";
        String path = writeFile("negativesinheadder.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Headers that have clauses before them are thrown
    @Test
    void throwsOnWcnfHeaderAfterClauses() throws IOException{
        String content = "1 2 0\np wcnf 2 1 30\n";
        String path = writeFile("clausebeforeheader.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Missing terminator 0 are thrown 
    @Test
    void throwsOnMissingTerminatorWcnf() throws IOException{
        String content = "p wcnf 3 1 10\n5 1 2\n3\n";
        String path = writeFile("missingterminator.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // Bare line 0 should be treated as clause with weight attached
    @Test
    void bareZeroLinePermitsWcnf() throws IOException{
        String content = "p wcnf 2 1 10\n10 0\n";
        String path = writeFile("barezeroline.wcnf", content);
        int[] result = Parser.parse(path);

        assertArrayEquals(new int[]{10,0}, result);
    }

    // throws when numClauses mismatches with actual number of clauses numClause<actual
    @Test
    void throwsOnNumClauseMismatchMoreWcnf() throws IOException{
        String content = "p wcnf 3 1 20\n10 1 2 0\n3 2 3 0\n"; // 1 clause stated but two clauses present
        String path = writeFile("mismatchedMore.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // throws when numClauses mismatches with actual number of clauses numClause>actual
    @Test
    void throwsOnNumClauseMismatchLessWcnf() throws IOException{
        String content = "p wcnf 2 2 10\n5 1 2 0\n"; // 2 clause stated but 1 clauses present
        String path = writeFile("mismatchedLess.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }

    // throws when variable is out of declared range
    @Test
    void throwsOnOutOfRangeVariableWcnf() throws IOException{
        String content = "p wcnf 2 1 5\n5 1 4 0\n"; // declared 2 but 4 is out of range
        String path = writeFile("outofrangevar.wcnf", content);
        assertThrows(IOException.class, () -> Parser.parse(path));
    }



    







    
    
}
