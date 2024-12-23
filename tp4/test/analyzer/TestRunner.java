package analyzer;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class TestRunner {
    public static void main(String[] args) {
        Result r = JUnitCore.runClasses(TestSuite.class);

        if(r.wasSuccessful()) {
            System.out.println("All " + Integer.toString(r.getRunCount())
                    + " test have passed.");
        }
        else {
            for(Failure f : r.getFailures()) {
                System.out.println(f.toString());
            }
            System.exit(-1);
        }
    }
}
