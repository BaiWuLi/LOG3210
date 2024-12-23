package analyzer.tests;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.util.*;

import analyzer.ast.ParserVisitor;
import analyzer.visitors.PrintMachineCodeVisitor;

@RunWith(Parameterized.class)
public class PrintMachineCodeTest extends BaseTest {

    private static String m_test_suite_path = "./test-suite/PrintMachineCodeTest/data";

    public PrintMachineCodeTest(File file) {
        super(file);
    }

    @Test
    public void run() throws Exception {
        ParserVisitor algorithm = new PrintMachineCodeVisitor(m_output);
        runAndAssert(algorithm);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getFiles() {
        return getFiles(m_test_suite_path);
    }

}
