package analyzer;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import analyzer.tests.*;

@RunWith(Suite.class)

@Suite.SuiteClasses({
        SemantiqueTest.class,
})

public class TestSuite {
}
