package analyzer.tests;

import analyzer.SemantiqueError;
import analyzer.ast.ParserVisitor;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.fail;

public class BaseTest {

    protected FileInputStream m_input;
    protected PrintWriter m_output;
    protected String m_expected;

    private final File m_file;

    public BaseTest(File file) {
        m_file = file;
    }

    // Get all the files from the base path and insert it in a collection
    // to be sent as parameters for the tests constructors
    public static Collection<Object[]> getFiles(String basePath) {
        Collection<Object[]> paramsForAllTests = new ArrayList<>();
        for (File file : new File(basePath).listFiles()) {
            paramsForAllTests.add(new Object[] { file });
        }
        return paramsForAllTests;
    }

    // At the creation of the test, this function prepare all the files
    @Before
    public void prepare() throws Exception {
        // Prepare
        String name = m_file.getName();
        String path = m_file.getParentFile().getParent();

        Path expectedPath = Paths.get(path + "/expected/" + name);
        Path resultPath = Paths.get(path + "/result/" + name);

        Assume.assumeTrue("Expected " + expectedPath + " does not exist",
                Files.exists(expectedPath));

        m_input = new FileInputStream(m_file);
        m_output = new PrintWriter(resultPath.toString());

        m_expected = new String(Files.readAllBytes(expectedPath));
        m_expected = m_expected.replaceAll("\\r", "");
    }

    // This is magic function which run algorithm on the input file,
    // print the output in the output file and assert if it's matching
    // the expect file
    public void runAndAssert(ParserVisitor algorithm) throws Exception {
        // Run
        try{
            analyzer.Main.Run(algorithm, m_input, m_output);
            m_output.flush();
        }

        // Assert
        catch (Exception ex) {
            // If we didn't expected this test to crash

            m_output.flush();

            if(!ex.getMessage().contains(m_expected))
            {
                ex.printStackTrace();
                fail(ex.getMessage());
            }

            return;
        } catch (SemantiqueError ex) {
            // Print the semantic error in result file
            m_output.flush();


            String resultFilePath = m_file.getParentFile().getParent() + "/result/" + m_file.getName();
            File resultFile = new File(resultFilePath);
            PrintWriter writer = new PrintWriter(resultFile);
            writer.println(ex.getMessage());
            writer.close();

            if(!ex.getMessage().contains(m_expected))
            {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
            return ;
        }

        String name = m_file.getName();
        String path = m_file.getParentFile().getParent();

        Path resultPath = Paths.get(path + "/result/" + name);

        String result = new String(Files.readAllBytes(resultPath));

        result = result.replaceAll("\\r", "");

        String[] splitResult = m_expected.split("\n", 2);
        if(splitResult.length > 0 && splitResult.length >= 2 && splitResult[0].substring(0, 2).equals("!~")) {
            String firstLine = splitResult[0];
            String expectedData = splitResult[1];

            if(firstLine.equals("!~Compile")) {
                return;
            } else if(firstLine.equals("!~Not Compile")) {

            } if(firstLine.equals("!~Compare")){
                Assert.assertEquals(expectedData, result);
            } else {
                throw new Error("unexpected Command : " + firstLine);
            }
        } else {
            Assert.assertEquals(m_expected, result);
        }



    }

}
