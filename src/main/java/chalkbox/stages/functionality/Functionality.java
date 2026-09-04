package chalkbox.stages.functionality;

import chalkbox.api.common.java.JUnitIndividualResult;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.config.Config;
import chalkbox.config.ConfigException;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import chalkbox.stages.conformance.SourceLoader;
import java.io.File;
import java.io.IOException;
import java.util.*;

@RegisterStage
public class Functionality
    extends BaseStage
    implements SubmissionAndSolutionStage, StageProducer {

    private double maxScore;
    private boolean showPassing;
    private boolean allVisible;

    public Functionality() {
        super("Functionality");
    }

    public Functionality(
        double maxScore,
        boolean showPassing,
        boolean allVisible
    ) {
        this();
        this.maxScore = maxScore;
        this.showPassing = showPassing;
        this.allVisible = allVisible;
    }

    @Override
    public Stage build(Config config) throws ConfigException {
        return new Functionality(
            config.getConfig("functionality.weighting", Double.class),
            config.getConfig("functionality.showPassing", true, Boolean.class),
            config.getConfig("functionality.allVisible", false, Boolean.class)
        );
    }

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
    public StageResult run(Submission submission, Solution solution)
        throws StageException {
        // Compile the solution, tests and the submission
        try {
            var compilation = solution.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile solution: " + compilation.output()
                );
            }
            compilation = solution.compileTest();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile tests: " + compilation.output()
                );
            }
            compilation = submission.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                    "Unable to compile submission: " + compilation.output()
                );
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
        var classPath =
            solution.getClassPath() +
            File.pathSeparator +
            solution.getSrcBuildPath() +
            File.pathSeparator +
            solution.getTestBuildPath();
        var baselineResults = this.runTests(tests, classPath);

        // Path contains dependencies and the compile submission
        classPath =
            solution.getClassPath() +
            File.pathSeparator +
            solution.getTestBuildPath() +
            File.pathSeparator +
            submission.getSrcBuildPath();
        var submissionResults = this.runTests(tests, classPath);

        var totalNumTests = 0;
        var innerResults = new ArrayList<Result>();
        var classResults = new ArrayList<ClassResult>();
        for (String className : tests) {
            if (!className.endsWith("Test")) {
                continue;
            }

            int classPassing = 0;

            // Use test summaries to collect information even if test fails to compile
            var classTests = baselineResults.get(className).size();
            var classWeighting = baselineResults
                .get(className)
                .getFirst()
                .classWeight();

            for (JUnitIndividualResult unit : submissionResults.get(
                className
            )) {
                var isPassing = unit.passes() == 1;
                var visibility = allVisible
                    ? Visibility.VISIBLE
                    : unit.visibility();
                var unitResult = new Result("Functionality: " + unit.name())
                    .setVisibility(visibility)
                    .setStatus(isPassing ? Status.PASSED : Status.FAILED);

                if (!isPassing || showPassing) {
                    unitResult.appendOutput(
                        isPassing
                            ? "✅ Test scenario passes\n"
                            : "❌ Test scenario fails\n"
                    );

                    // Get Test class JavaDoc
                    var testDescription = getTestJavaDoc(
                        solution.getTestBuildPath(),
                        className,
                        unit.name()
                    );
                    if (!testDescription.isEmpty()) {
                        unitResult.appendOutput("### Scenario\n");
                        unitResult.appendOutput(testDescription);
                    }

                    if (!isPassing) {
                        unitResult.appendOutput("### Details\n");
                        unitResult.appendOutput(unit.output());
                    }
                }

                var testMultiplier = (Integer) unit.weight();
                // e.g. a test worth 5 "units" will increase the total number of tests by 5
                totalNumTests += testMultiplier;
                innerResults.add(unitResult);
                classPassing += unit.passes() == 1 ? 1 : 0;
            }
            classResults.add(
                new ClassResult(
                    className,
                    classTests,
                    classPassing,
                    classWeighting,
                    submissionResults.get(className).size()
                )
            );
        }

        if (totalNumTests == 0) {
            // todo(mh): Do something better here
            return null;
        }

        double total = 0;
        double possible = 0;
        var table = new StringBuilder(
            "| TestClass | Weighting | Passing Tests | Total |"
        );
        table.append(
            "\n| ----------- | ----------- | ----------- | ----------- |\n"
        );
        for (var classResult : classResults) {
            if (classResult.count() <= 0) {
                continue;
            }
            double score =
                (classResult.passing() / (float) classResult.count()) *
                classResult.weight();
            table
                .append("| ")
                .append(classResult.name())
                .append(" | ")
                .append(classResult.weight())
                .append(" | ")
                .append(classResult.passing())
                .append("/")
                .append(classResult.count())
                .append(" | ")
                .append(String.format("%.3f", score))
                .append("|\n");
            total += score;
            possible += classResult.weight();
        }
        double scaled = Math.ceil((total / possible) * maxScore);

        var equation =
            "\n$$\n\\dfrac{" +
            String.format("%.3f", total) +
            "}{" +
            possible +
            "} \\times " +
            maxScore +
            " = " +
            scaled +
            "\n$$";
        var overview = new Result(getName());
        overview
            .setMaxScore(maxScore)
            .setScore(scaled)
            .appendOutput(table + equation)
            .setOutputFormat("md")
            .setVisibility(Visibility.AFTER_PUBLISHED);

        return new StageResult(overview, innerResults);
    }

    private Map<String, List<JUnitIndividualResult>> runTests(
        List<String> tests,
        String classPath
    ) {
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

    private String getTestJavaDoc(
        String folder,
        String className,
        String methodName
    ) {
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
