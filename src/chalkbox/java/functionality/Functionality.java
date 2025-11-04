package chalkbox.java.functionality;

import chalkbox.api.collections.Bundle;
import chalkbox.api.collections.Collection;
import chalkbox.api.collections.Data;
import chalkbox.api.common.java.Compiler;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.engines.ConfigFormatException;
import chalkbox.engines.Configuration;
import chalkbox.java.compilation.JavaCompilation;
import chalkbox.java.conformance.SourceLoader;
import com.github.therapi.runtimejavadoc.ClassJavadoc;
import com.github.therapi.runtimejavadoc.MethodJavadoc;
import org.json.simple.JSONArray;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Process to execute JUnit tests on each submission.
 *
 * Requires {@link JavaCompilation} process to be executed first.
 * Checks if "extra_data.compilation.compiles" is true.
 */
public class Functionality {

    public static class FunctionalityOptions implements Configuration {

        /** Whether or not to run this stage */
        private boolean enabled = false;

        /** Sample solution to compile tests with */
        private String correctSolution;

        /** Class path for tests to be compiled with */
        private String classPath;

        /** Number of marks allocated to the functionality stage */
        private int weighting;

        /** Total amount of unit tests */
        private int overrideTotalTests;

        /** Path of JUnit test files */
        private String testDirectory;

        /**
         * Checks this configuration and throws an exception if it is invalid.
         *
         * @throws ConfigFormatException if the configuration is invalid
         */
        @Override
        public void validateConfig() throws ConfigFormatException {
            if (!enabled) {
                return;
            }

            /* Must have a test directory */
            if (testDirectory == null || testDirectory.isEmpty()) {
                throw new ConfigFormatException(
                        "Missing testDirectory in functionality stage");
            }

            /* Must have a weighting between 0 and 100 */
            if (weighting < 0 || weighting > 100) {
                throw new ConfigFormatException(
                        "Functionality weighting must be between 0 and 100");
            }
        }

        //<editor-fold desc="JavaBeans getters/setters">

        public int getWeighting() {
            return weighting;
        }

        public void setWeighting(int weighting) {
            this.weighting = weighting;
        }

        public int getOverrideTotalTests() {
            return overrideTotalTests;
        }

        public void setOverrideTotalTests(int overrideTotalTests) {
            this.overrideTotalTests = overrideTotalTests;
        }

        public String getTestDirectory() {
            return testDirectory;
        }

        public void setTestDirectory(String testDirectory) {
            this.testDirectory = testDirectory;
        }

        public String getCorrectSolution() {
            return correctSolution;
        }

        public void setCorrectSolution(String correctSolution) {
            this.correctSolution = correctSolution;
        }

        public String getClassPath() {
            return classPath;
        }

        public void setClassPath(String classPath) {
            this.classPath = classPath;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        //</editor-fold>
    }

    private record TestClassInfo(
            String className,
            int totalTests,
            int passingTests,
            double weight,
            List<Data> tests
    ) {}

    private record SolutionSummary(
            int totalTests,
            double classWeight
    ) {}

    /** Configuration options */
    private FunctionalityOptions options;

    /** Bundle containing the JUnit test files to run against the submission */
    private Bundle tests;

    /** Whether there were issues compiling the sample solution or tests */
    private boolean hasErrors;

    /** Directory containing compiled test files */
    private String testCompiledOutputDirectory;

    /** Mapping of testing classes to summaries. */
    private Map<String, SolutionSummary> testSummaries = new HashMap<>();

    /**
     * Sets up the functionality stage ready to process a submission.
     *
     * @param options configuration options to use when running functionality
     *                tests
     */
    public Functionality(FunctionalityOptions options) {
        this.options = options;

        this.compileTests();
    }

    /**
     * Compile the sample solution and then compile the tests with the sample
     * solution.
     */
    public void compileTests() {
        tests = new Bundle(new File(options.testDirectory));
        Bundle solution = new Bundle(new File(options.correctSolution));

        /* Load output directories for the solution and the tests */
        Bundle solutionOutput;
        Bundle testOutput;
        try {
            solutionOutput = new Bundle();
            testOutput = new Bundle();
            /* Add the tests to the class path for execution */
            options.setClassPath(options.classPath
                    + System.getProperty("path.separator")
                    + testOutput.getUnmaskedPath());
        } catch (IOException e) {
            hasErrors = true;
            e.printStackTrace();
            return;
        }

        StringWriter output = new StringWriter();
        boolean didCompile = true;

        /* Compile the sample solution */
        didCompile = Compiler.compile(Compiler.getSourceFiles(solution), options.classPath,
                solutionOutput.getUnmaskedPath(), output);

        String classPath = options.classPath
                + System.getProperty("path.separator")
                + solutionOutput.getUnmaskedPath();

        /* Compile the tests with the sample solution */
        didCompile = Compiler.compile(Compiler.getSourceFiles(tests), classPath,
                testOutput.getUnmaskedPath(), output) || didCompile;
        testCompiledOutputDirectory = testOutput.getUnmaskedPath();

        if (!didCompile) {
            System.out.println(output);
        }

        /* Summarise tests using the solution. */
        for (String className : tests.getClasses("")) {
            List<Data> results = JUnitRunner.runTests(className, classPath);
            if (!results.isEmpty()) {
                // Only summarise if there are tests in file, else skip.
                testSummaries.put(className, new SolutionSummary(results.size(), (Double) results.getLast().get("classWeighting")));
            }
        }
    }

    /**
     * Run the tests on a submission.
     *
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
    public Collection run(Collection submission) {
        if (hasErrors) {
            return submission;
        }
        if (!submission.getResults().is("extra_data.compilation.compiles")) {
            return submission;
        }

        /* No longer supported:
           Copy the included resources to the submission directory
        if (options.included != null && !options.included.isEmpty()) {
            try {
                submission.getWorking().copyFolder(new File(options.included));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        */

        /* Class path contains dependencies and the compiled submission */
        String classPath = submission.getWorking().getUnmaskedPath("bin")
                + System.getProperty("path.separator")
                + options.classPath;
        JSONArray testResults = (JSONArray) submission.getResults().get("tests");
        int totalNumTests = 0;
        JSONArray functionalityResults = new JSONArray();
        Map<String, TestClassInfo> testInfo = new HashMap<>();
        String[] testNames;
        try {
            testNames = submission.getSource().getFile("tasks").getContent().split("\n");
        } catch (IOException e) {
            System.out.println("Unable to find tasks file");
            return submission;
        }
        for (String className : testNames) {
            className = "demos." + className + "Test";
            List<Data> results = JUnitRunner.runTests(className, classPath);
            // There are no tests in file - skip over it.
            if (results.isEmpty()) {
                continue;
            }
            /* Sort alphabetically by test class then test name */
            results.sort(Comparator.comparing(o -> ((String) o.get("name"))));
            int classPassing = 0;
            /* Use test summaries to collect information even if test fails to compile. */
            int classTests = testSummaries.get(className).totalTests;
            double classWeighting = testSummaries.get(className).classWeight;
            List<Data> testCases = new ArrayList<>();

            for (Data result : results) {
                boolean isPassing = (Integer) result.get("extra_data.passes") == 1;
                // Get Test class JavaDoc
                try {
                    String testDescription = "";
                    ClassJavadoc javaDoc = new SourceLoader(testCompiledOutputDirectory).getTestJavadoc(className);
                    for (MethodJavadoc method : javaDoc.getMethods()) {
                        if (method.getName().equals(result.get("name").toString().split("\\.")[1])) {
                            testDescription += "" + method.getComment() + "\n";
                        }
                    }
                    if (!isPassing && !testDescription.equals("")) {
                        result.set("output", "❌ Test scenario fails\n### Scenario\n" + testDescription + "### Details\n" + result.get("output"));
                    }
                } catch (IOException ignored) {
                    // Do Nothing
                }

                int testMultiplier = (Integer) result.get("weighting");
                /* e.g. a test worth 5 "units" will increase the total number of tests by 5 */
                totalNumTests += testMultiplier;
                functionalityResults.add(result);
                classPassing += (Integer) result.get("extra_data.passes") == 1 ? 1 : 0;
                testCases.add(result);
            }
            testInfo.put(className, new TestClassInfo(className, classTests, classPassing, classWeighting, testCases));
        }
        if (totalNumTests == 0) {
            return submission;
        }

        /* Mark awarded for passing a single test method (un-scaled by test multipliers) */
        final double individualTestWeighting = 1d / totalNumTests * options.weighting;
        int passingTests = 0;

        for (Object o : functionalityResults) {
            Data functionalityResult = (Data) o;
            boolean didPass = (Integer) functionalityResult.get("extra_data.passes") == 1;
            int testMultiplier = (Integer) functionalityResult.get("weighting");
            functionalityResult.set("status", didPass ? "passed" : "failed");
            passingTests += didPass ? 1 : 0;
//            functionalityResult.set("max_score", individualTestWeighting * testMultiplier);
            testResults.add(functionalityResult);
        }

        String results = "| TestClass | Weighting | Passing Tests | Total |";
        results += "\n| ----------- | ----------- | ----------- | ----------- |\n";
        double total = 0;
        double possible = 0;

        for (TestClassInfo info : testInfo.values()) {
            // Skip classes that have no tests.
            if (info.totalTests <= 0) {
                continue;
            }
            double score = (info.passingTests / (float) info.totalTests) * info.weight;
            total += score;
            possible += info.weight;
            results += "| " + info.className + " | " + info.weight + " | " + info.passingTests + "/" + info.totalTests + " | " + score + "|\n";
        }
        double scaled = Math.ceil((total / possible) * options.weighting);

        //results += "\n$$\n\\dfrac{" + total + "}{" + possible + "} \\times " + options.weighting + " = " + scaled + "\n$$";
        results += "\n$$sum = "+total+"$$";

        Data data = new Data();
        data.set("name", "Functionality Tests");
        data.set("score", total);
        data.set("max_score", options.weighting);
        data.set("output", results);
        data.set("output_format", "md");
//        data.set("output", "You passed " + passingTests + " out of " + options.overrideTotalTests + " tests");
//        for (TestClassInfo info : testInfo.values()) {
//            data.set("output", data.get("output") + "\n" + info.toString());
//        }
        data.set("visibility", "after_published");
        testResults.add(0, data);

        return submission;
    }
}
