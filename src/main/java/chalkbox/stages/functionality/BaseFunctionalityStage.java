package chalkbox.stages.functionality;

import chalkbox.api.common.java.JUnitIndividualResult;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import chalkbox.stages.conformance.SourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class BaseFunctionalityStage implements Stage {

    protected final double maxScore;
    protected final boolean showPassing;
    protected final boolean allVisible;

    public BaseFunctionalityStage(double maxScore, boolean showPassing, boolean allVisible) {
        this.maxScore = maxScore;
        this.showPassing = showPassing;
        this.allVisible = allVisible;
    }

    @Override
    public Type getType() {
        return Type.SUBMISSION_AND_SOLUTION;
    }

    @Override
    public StageResult run(Submission submission) throws StageException {
        // Not implemented
        return null;
    }

    @Override
    public StageResult run(Submission submission, List<Solution> solutions) throws StageException {
        // Not implemented
        return null;
    }

    public abstract StageResult formatResults(Submission submission, Solution solution,
                                              Map<String, List<JUnitIndividualResult>> solutionResults,
                                              Map<String, List<JUnitIndividualResult>> submissionResults) throws IOException;

    /**
     * Run the tests on a submission.
     * <p>
     * If there were issues compiling the sample solution or the tests, or
     * the submission did not compile successfully, no action is taken.
     * <p>
     * Uses a JUnit listener to observe the passed/failed tests for each test
     * class. One Gradescope test is created for each JUnit test method, with
     * a mark of zero if the test failed, or a mark of
     * <code>stageWeighting / numTests</code> if the test passed, where
     * <code>stageWeighting</code> is the number of marks allocated to this
     * stage, and <code>numTests</code> is the total number of JUnit test
     * methods in all test classes.
     */
    @Override
    public StageResult run(Submission submission, Solution solution) throws StageException {
        // Compile the solution, tests and the submission
        try {
            var compilation = solution.compileSrc();
            if (!compilation.success()) {
                throw new StageException("Unable to compile solution: " + compilation.output());
            }
            compilation = solution.compileTest();
            if (!compilation.success()) {
                throw new StageException("Unable to compile tests: " + compilation.output());
            }
            compilation = submission.compileSrc();
            if (!compilation.success()) {
                throw new StageException("Unable to compile submission: " + compilation.output());
            }
        } catch (IOException e) {
            throw new StageException(e);
        }

        List<String> tests = null;
        try {
            tests = solution.getTestClasses();
        } catch (IOException e) {
            throw new StageException(e.toString());
        }

        // Run tests against the solution
        var classPath = solution.getClassPath() +
                File.pathSeparator + solution.getSrcBuildPath() +
                File.pathSeparator + solution.getTestBuildPath();
        var baselineResults = this.runTests(tests, classPath);

        // Path contains dependencies and the compile submission
        classPath = solution.getClassPath() +
                File.pathSeparator + submission.getSrcBuildPath() +
                File.pathSeparator + solution.getTestBuildPath();
        var submissionResults = this.runTests(tests, classPath);

        try {
            return formatResults(submission, solution, baselineResults, submissionResults);
        } catch (IOException e) {
            throw new StageException(e);
        }
    }

    protected String formatResultTable(List<ClassResult> results) {
        var table = new StringBuilder("| TestClass | Weighting | Passing Tests | Total |");
        table.append("\n| ----------- | ----------- | ----------- | ----------- |\n");
        for (var classResult : results) {
            if (classResult.count() <= 0) {
                continue;
            }
            double score = (classResult.passing() / (float) classResult.count()) * classResult.weight();
            table.append("| ").append(classResult.name())
                    .append(" | ").append(classResult.weight())
                    .append(" | ").append(classResult.passing()).append("/").append(classResult.count())
                    .append(" | ").append(String.format("%.3f", score))
                    .append("|\n");
        }
        return table.toString();
    }

    protected ClassResult testClassDetails(String className, List<JUnitIndividualResult> solutionResults, List<JUnitIndividualResult> submissionResults) {
        int classPassing = 0;

        // Use test summaries to collect information even if test fails to compile
        var classTests = solutionResults.size();
        var classWeighting = solutionResults.getFirst().classWeight();

        for (JUnitIndividualResult unit : submissionResults) {
            classPassing += unit.passes() == 1 ? 1 : 0;
        }
        return new ClassResult(className, classTests, classPassing, classWeighting, submissionResults.size());
    }

    protected List<Result> formatTestClass(String className, Solution solution, List<JUnitIndividualResult> submissionResults) {
        List<Result> results = new ArrayList<>();

        for (JUnitIndividualResult unit : submissionResults) {
            var isPassing = unit.passes() == 1;
            var visibility = allVisible ? Visibility.VISIBLE : unit.visibility();
            var unitResult = new Result("Functionality: " + unit.name())
                    .setVisibility(visibility)
                    .setStatus(isPassing ? Status.PASSED : Status.FAILED);

            if (!isPassing || showPassing) {
                unitResult.appendOutput(isPassing ? "✅ Test scenario passes\n" : "❌ Test scenario fails\n");

                // Get Test class JavaDoc
                var testDescription = getTestJavaDoc(solution.getTestBuildPath(), className, unit.name());
                if (!testDescription.isEmpty()) {
                    unitResult.appendOutput("### Scenario\n");
                    unitResult.appendOutput(testDescription);
                }

                if (!isPassing) {
                    unitResult.appendOutput("### Details\n");
                    unitResult.appendOutput(unit.output());
                }
            }

            results.add(unitResult);
        }
        return results;
    }

    private Map<String, List<JUnitIndividualResult>> runTests(List<String> tests, String classPath) {
        var collection = new HashMap<String, List<JUnitIndividualResult>>();
        for (String className : tests) {
            // Ignore any that dont end in TEST
            if (!className.endsWith("Test")) {
                continue;
            }

            var results = JUnitRunner.runTests(className, classPath);
            if (results.isEmpty()) {
                continue;
            }
            results.sort(Comparator.comparing(JUnitIndividualResult::name));
            collection.put(className, results);
        }
        return collection;
    }

    protected String getTestJavaDoc(String folder, String className, String methodName) {
        try {
            var testDescription = new StringBuilder();
            var javaDoc = new SourceLoader(folder).getTestJavadoc(className);
            for (var method : javaDoc.getMethods()) {
                if (method.getName().equals(methodName.split("\\.")[1])) {
                    testDescription.append(method.getComment()).append("\n");
                }
            }
            return testDescription.toString();
        } catch (IOException ignored) {
            // Do Nothing
        }
        return "";
    }
}
