package chalkbox.api.common.java;

import chalkbox.stages.Visibility;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class JUnitListener extends RunListener {

    private static class TestResult {

        private final String testName;
        private boolean visible = false;
        private boolean passed = true;
        private String output = "";
        private int weighting = 1;
        private double classWeighting = 1;

        public TestResult(String testName) {
            this.testName = testName;
        }
    }

    private List<TestResult> results;
    private TestResult currentResult;
    private int numFailed = 0;
    private StringBuilder output;
    private boolean couldNotFindClass = false;

    public JUnitListener() {
        this.results = new ArrayList<>();
        this.output = new StringBuilder();
    }

    public void classMissing() {
        this.couldNotFindClass = true;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        super.testStarted(description);

        this.currentResult =
        new TestResult(
            description.getTestClass().getSimpleName() +
            "." +
            description.getMethodName()
        );

        /* Results of this test should be immediately visible */
        // TODO create a library containing a custom annotation for this
        if (description.getAnnotation(Deprecated.class) != null) {
            this.currentResult.visible = true;
        }

        int testWeighting;
        var testAnnotation = description.getAnnotation(Test.class);
        if (testAnnotation != null) {
            /* If no timeout was specified, then give this test a weighting of 1 (standard) */
            if (testAnnotation.timeout() == 0) {
                testWeighting = 1;
            } else {
                /*
                 * Otherwise, use the timeout modulo 10 as the weighting, ensuring that the test
                 * weighting can never be zero.
                 *
                 * This allows a weighting (marks multiplier) to be specified by adding an integer
                 * between 2 and 9 to the timeout for a JUnit test. For example,
                 * @Test(timeout = 100000 + 5)
                 * means that this test has a weighting of 5 times that of a standard test.
                 *
                 * A large number of milliseconds should be used for the timeout, so that the actual
                 * timeout is not likely to be reached (since global timeout should be used instead).
                 */
                testWeighting =
                Math.max(1, (int) (testAnnotation.timeout() % 10));
            }
            this.currentResult.weighting = testWeighting;
        }

        double classWeighting = 1.0;

        if (hasField(description.getTestClass().getFields(), "testWeight")) {
            classWeighting =
            description.getTestClass().getField("testWeight").getDouble(null);
        }

        this.currentResult.classWeighting = classWeighting;
    }

    private boolean hasField(Field[] fields, String fieldName) {
        for (Field field : fields) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void testFinished(Description description) throws Exception {
        super.testFinished(description);

        if (this.currentResult != null) {
            this.results.add(this.currentResult);
        }
        this.currentResult = null;
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        super.testFailure(failure);

        String failureString = failure.toString();
        if (failureString.equals(failure.getTestHeader() + ": null")) {
            failureString =
            failureString.substring(0, failureString.length() - 4);
            failureString += "No message given, refer to stack trace.";
        }

        if (this.currentResult != null) {
            this.output.append(failureString);
            this.output.append("\n");
            this.currentResult.output =
            failureString +
            "\n\n```text\n" +
            failure.getTrace().replaceAll("\r\n", "\n") +
            "```";
            this.currentResult.passed = false;
        }
        this.numFailed++;
    }

    public JUnitResult getResultsForClass() {
        return new JUnitResult(
            this.results.size() - this.numFailed,
            this.numFailed,
            this.results.size(),
            this.output.toString(),
            couldNotFindClass
        );
    }

    public List<JUnitIndividualResult> getIndividualResults() {
        var results = new ArrayList<JUnitIndividualResult>();
        for (TestResult result : this.results) {
            var data = new JUnitIndividualResult(
                result.passed ? 1 : 0,
                result.passed ? 0 : 1,
                1,
                result.output,
                "md",
                result.testName,
                result.weighting,
                result.classWeighting,
                result.visible ? Visibility.VISIBLE : Visibility.AFTER_PUBLISHED
            );
            results.add(data);
        }
        return results;
    }
}
