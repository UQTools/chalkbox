package chalkbox.stages.faulty;

import chalkbox.api.common.java.JUnitResult;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.config.Config;
import chalkbox.config.ConfigException;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import org.github.gestalt.config.reflect.TypeCapture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RegisterStage
public class Faulty
        extends BaseStage
        implements SubmissionAndSolutionStage, StageProducer {

    private double weighting;
    private String faultySolutions;
    private List<String> assessableTestClasses;

    private List<String> solutionClassPath;


    public Faulty() {
        super("Faulty Solutions");
    }

    public Faulty(
            double weighting,
            String faultySolutions,
            List<String> assessableTestClasses,
            List<String> solutionClassPath
    ) {
        this();
        this.weighting = weighting;
        this.faultySolutions = faultySolutions;
        this.assessableTestClasses = assessableTestClasses;
        this.solutionClassPath = solutionClassPath;
    }

    @Override
    public Stage build(Config config) throws ConfigException {
        return new Faulty(
                config.getConfig("faulty.weighting", Double.class),
                config.getConfig("faulty.faultySolutions", String.class),
                config.getConfig("faulty.assessableTestClasses", new TypeCapture<List<String>>() {}),
                config.getConfig("solution.classPath", new TypeCapture<List<String>>() {}) // same as solution for now
        );
    }

    @Override
    public StageResult run(Submission submission, Solution solution) throws StageException {
        List<String> missing = submission.validateStructure(List.of("test"));
        if (!missing.isEmpty()) {
            throw new StageException("A test directory was not included in your submission.");
        }
        compileSolution(solution);
        /* Collect the list of broken solution folders */
        File solutionsFolder = new File(faultySolutions);
        /* Only include directories as faulty solutions (e.g. not .DS_Store) */
        File[] solutionFolders = solutionsFolder.listFiles(File::isDirectory);
        assert solutionFolders != null;
        List<Solution> solutions = Arrays.stream(solutionFolders).map(folder -> new Solution(folder.getName(), folder.getPath(), solutionClassPath)).toList();
        compileSolutions(solutions);

        compileTests(submission, solution);
        return runTests(submission, solution);
    }

    /** Mapping of faulty implementation names to their respective class path */
    private Map<String, String> classPaths = new TreeMap<>();

    /**
     * Total number of faulty implementations to run JUnit tests against,
     * excluding the correct solution if present.
     */
    private int numFaultySolutions;

    private void compileSolution(Solution solution) {
        try {
            var compilation = solution.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                        "Unable to compile solution: " + compilation.output()
                );
            }
        } catch (IOException e) {
            throw new StageException(e);
        }
    }
    private void compileSolutionAndTests(Solution solution) {
        compileSolution(solution);
        try {
            var compilation = solution.compileTest();
            if (!compilation.success()) {
                throw new StageException(
                        "Unable to compile tests: " + compilation.output()
                );
            }
        } catch (IOException e) {
            throw new StageException(e);
        }
    }

    /**
     * Compile all the faulty implementations to test submitted JUnit tests on
     */
    private void compileSolutions(List<Solution> solutions) {
        this.numFaultySolutions = solutions.size();
        // If "solution/" dir is in "solutions", subtract one from number of
        // faulty solutions
        for (Solution solution : solutions) {
            if (solution.getName().equals("solution")) {
                this.numFaultySolutions = solutions.size() - 1;
                break;
            }
        }

        for (Solution solution : solutions) {
            compileSolution(solution);
            classPaths.put(solution.getName(), solution.getSrcBuildPath()
                    + File.pathSeparator
                    + String.join(File.pathSeparator, solutionClassPath));
        }
    }

//    /**
//     * Compile the sample solution.
//     */
//    private void compileSolution() {
//        Bundle solutionSource = new Bundle(new File(options.correctSolution));
//
//        /* Compile the sample solution */
//        StringWriter writer = new StringWriter();
//        compileSolution(solutionSource, "sample solution",
//                solutionOutput.getUnmaskedPath(), writer);
//
//        solutionClassPath = options.classPath + System.getProperty("path.separator")
//                + solutionOutput.getUnmaskedPath();
//    }

    /**
     * Compiles the submitted JUnit tests with the correct implementation.
     *
     * Sets "extra_data.junit.compiles" to true/false based on whether at least
     * one of the submitted tests compiled.
     *
     * @param submission submission containing tests to compile
     * @return given submission with extra test results
     */
    private void compileTests(Submission submission, Solution solution) {
        try {
            var compilation = submission.compileSrc();
            if (!compilation.success()) {
                throw new StageException(
                        "Unable to compile solution: " + compilation.output()
                );
            }
            compilation = submission.compileTest(solution.getSrcBuildPath());
            if (!compilation.success()) {
                throw new StageException(
                        "Unable to compile tests: " + compilation.output()
                );
            }
        } catch (IOException e) {
            throw new StageException(e);
        }

//        StringJoiner output = new StringJoiner("\n");
//        StringWriter error = new StringWriter();
//
//        /* Compile each submitted test class individually */
//        boolean anyCompiles = false;
//        boolean allCompiles = true;
//
//        for (String className : assessableTestClasses) {
//            boolean success = false;
//            String fileName = className.replace(".", "/") + ".java";
//            String packageName = className.substring(0, className.lastIndexOf("."));
//            StringWriter compileOutput = new StringWriter();
//            SourceFile file;
//            try {
//                file = tests.getFile(fileName); // throws NPE if no test directory was found
//                StringSourceFile stringFile = StringSourceFile.copyOf(file);
//                stringFile.replaceAll("^package (.+);(.*)", "package " + packageName + ";");
//                String contents = stringFile.getContent();
//                Pattern packageHeader = Pattern.compile("^package");
//                Matcher matcher = packageHeader.matcher(contents);
//                if (!matcher.find()) {
//                    contents = "package " + packageName + ";" + System.lineSeparator() + contents;
//                    stringFile = stringFile.update(contents);
//                }
//                List<SourceFile> files = new ArrayList<>();
//                files.add(stringFile);
//                boolean fileSuccess = Compiler.compile(files,
//                        solutionClassPath,
//                        submission.getWorking().getUnmaskedPath(),
//                        compileOutput);
//                if (fileSuccess) {
//                    anyCompiles = true;
//                }
//                output.add("✅ JUnit test file `" + fileName + "` found.");
//                if (fileSuccess) {
//                    output.add("✅ JUnit test file `" + fileName + "` compiles.");
//                    success = true;
//                } else {
//                    output.add("❌ JUnit test file `" + fileName + "` does not compile.");
//                }
//                output.add(compileOutput.toString());
//            } catch (FileNotFoundException | NullPointerException e) {
//                e.printStackTrace();
//                error.write("❌ JUnit test file `" + fileName + "` not found.\n");
//            } catch (IOException e) {
//                e.printStackTrace();
//                error.write("IO Compile Error - Please contact course staff\n");
//            }
//            if (!success) {
//                allCompiles = false;
//            }
//        }
//
//        JSONArray testResults = (JSONArray) submission.getResults().get("tests");
//        JSONObject junitResult = new JSONObject();
//        junitResult.put("name", "JUnit compilation");
//        junitResult.put("output_format", "md");
//        junitResult.put("status", allCompiles ? "passed" : "failed");
//        String visibleOutput = output.toString();
//        if (!error.toString().isEmpty()) {
//            visibleOutput += error.toString();
//        }
//        junitResult.put("output", visibleOutput);
//        if (testResults.size() >= 3) {
//            testResults.add(3, junitResult);
//        }

        /*
        Run submitted JUnit tests against broken solutions even if one or
        more test classes don't compile, as long as at least one does.
         */
//        submission.getResults().set("extra_data.junit.compiles", anyCompiles);

//        return submission;
    }

    /**
     * Runs the submitted JUnit tests against each faulty implementation.
     *
     * @param submission submission containing tests to run
     * @return given submission with extra test results, one for each faulty
     * implementation
     */
    private StageResult runTests(Submission submission, Solution sampleSolution) {
        Map<String, Integer> passes = new HashMap<>();
//        Data junitInfo = new Data();
        for (String testClass : assessableTestClasses) {
            String classPath = String.join(File.pathSeparator, solutionClassPath)
                    + File.pathSeparator + sampleSolution.getSrcBuildPath()
                    + File.pathSeparator + submission.getTestBuildPath();
                    //+ File.pathSeparator
                    //+ submission.getWorking().getUnmaskedPath();
            JUnitResult results = JUnitRunner.runTestsCombined(testClass, classPath);
            passes.put(testClass, results.passes());
//            junitInfo.set("output", junitInfo.get("output") + "" +
//                    "\n==========\n" +
//                    testClass + " on Solution");
        }
        int totalSolutionPassed = passes.values().stream().mapToInt(Integer::intValue).sum();

        File solutionsFolder = new File(faultySolutions);
        int passingTests = 0;
        List<Result> tests = new ArrayList<>();
        for (String solution : classPaths.keySet()) {
            /* Class path for the particular solution */
            String classPath = classPaths.get(solution)
                    + File.pathSeparator + submission.getTestBuildPath();
                    //+ System.getProperty("path.separator")
                    //+ submission.getWorking().getUnmaskedPath();

            /* JSON test result for this broken solution */
            /* Results of the JUnit runner for each submitted test class */
            List<JUnitResult> classResults = new ArrayList<>();
            Map<String, Boolean> passed = new HashMap<>();
            /* Is the solution being tested the correct implementation? */
            boolean isCorrectSolution = solution.equals("solution");

            List<String> missingTestClasses = new ArrayList<>();

            for (String testClass : assessableTestClasses) {
                /* Run the JUnit tests */
                JUnitResult results = JUnitRunner.runTestsCombined(testClass, classPath);
                passed.put(testClass, results.passes() < passes.get(testClass));
                classResults.add(results);
                if (results.couldNotFindClass()) {
                    missingTestClasses.add(testClass);
                }
            }

            /* Mark awarded for correctly identifying a broken solution */
            final double solutionWeighting = 1d / this.numFaultySolutions * weighting;

            String name;
            Visibility visibility;
            /* Solutions whose name ends with _VISIBLE should be visible to students immediately */
            if (solution.endsWith("_VISIBLE")) {
                name = "JUnit (" + solution.replaceAll("_VISIBLE", "") + ")";
                visibility = Visibility.VISIBLE;
            } else {
                name = "JUnit (" + solution + ")";
                visibility = Visibility.AFTER_PUBLISHED;
            }

            Result solutionResult = new Result(name);
            solutionResult.setVisibility(visibility);
            //solutionResult.setMaxScore(solutionWeighting);
            /* The correct solution is not graded, but should still appear */
//            if (!isCorrectSolution) {
//                solutionResult.set("score", 0);
//                solutionResult.set("max_score", solutionWeighting);
//            }
            /*
             * For each test class result JSON:
             * - Concatenate the output of all the test classes
             * - Determine whether at least one test class was "correct"
             */
            StringJoiner joiner = new StringJoiner("\n");
            /* Find the total number of tests passed/failed for this solution */
            int totalPassed = 0;
            int totalFailed = 0;
            for (JUnitResult classResult : classResults) {
                totalPassed += classResult.passes();
                totalFailed += classResult.fails();
            }

            if (!isCorrectSolution) {
                if (totalPassed < totalSolutionPassed) {
                    joiner.add("\n✅ Outcome: Your unit tests correctly detected that this was a "
                            + "faulty implementation.");
                    passingTests += 1;
                    solutionResult.setStatus(Status.PASSED);
                } else {
                    joiner.add("\n❌ Outcome: Your unit tests did not correctly detect that this "
                            + "was a faulty implementation.");
                    solutionResult.setStatus(Status.FAILED);
                }
            }

            for (String missingTestClass : missingTestClasses) {
                joiner.add("\nNote: " + missingTestClass
                        + " class could not be found. Ensure it is within the test directory and in the appropriate package.");
            }

            joiner.add("\nTests that passed when run against a correct "
                    + "implementation: **" + totalSolutionPassed + "**");
            joiner.add("Tests that passed when run against this faulty "
                    + "implementation: **" + totalPassed + "**");

            String description = solutionsFolder.getAbsolutePath() + "/" + solution + "/README.txt";
            if (Files.exists(Path.of(description))) {
                String content = "";
                try {
                    content = Files.readString(Path.of(description));
                } catch (IOException e) {
                    System.out.println("Could not find solution description: " + description);
                }
                joiner.add("\n### Scenario");
                joiner.add(content);
            }

            joiner.add("\n### Details");
            if (totalFailed > 0) {
                joiner.add("Tests which did not pass for this implementation (remember not passing for a faulty implementation is a good thing):\n```text");
            }
            for (JUnitResult classResult : classResults) {
                String classOutput = classResult.output();
                /* Don't add output if there is no output ("") */
                if (!classOutput.isEmpty()) {
                    joiner.add(classOutput);
                }
//                if (classResult.is("extra_data.correct")
//                        && !isCorrectSolution) {
//                    solutionResult.set("score", solutionWeighting);
//                }
            }
            joiner.add("```");
            solutionResult.appendOutput(joiner.toString());
            solutionResult.setOutputFormat("md");

            tests.add(solutionResult);
        }

        double total = Math.ceil((passingTests / (float) numFaultySolutions) * weighting);

        Result summary = new Result("JUnit Tests")
                .setMaxScore(weighting)
                .setScore(total)
                .appendOutput("You correctly identified bugs in " + passingTests
                        + " out of " + numFaultySolutions
                        + " buggy solutions")
                .setVisibility(Visibility.AFTER_PUBLISHED);

        return new StageResult(summary, tests);
    }
}
