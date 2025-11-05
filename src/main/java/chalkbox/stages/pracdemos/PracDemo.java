package chalkbox.stages.pracdemos;

import chalkbox.api.common.java.JUnitIndividualResult;
import chalkbox.api.common.java.JUnitRunner;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;
import chalkbox.stages.conformance.SourceLoader;
import chalkbox.stages.functionality.BaseFunctionalityStage;
import chalkbox.stages.functionality.ClassResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The practical demonstration stage differs from the Functionality stage
 * in that test classes are read from a tasks file included in the submission
 * as each student will have a different subset of test classes.
 */
public class PracDemo extends BaseFunctionalityStage {

    public final static String name = "PracDemo";

    public PracDemo(double maxScore, boolean showPassing, boolean allVisible) {
        super(maxScore, showPassing, allVisible);
    }

    @Override
    public String getName() {
        return name;
    }

   @Override
    public StageResult formatResults(Submission submission, Solution solution,
                                     Map<String, List<JUnitIndividualResult>> solutionResults,
                                     Map<String, List<JUnitIndividualResult>> submissionResults) throws IOException {
        var totalNumTests = 0;
        var innerResults = new ArrayList<Result>();
        var classResults = new ArrayList<ClassResult>();
        List<String> testNames;
        try {
            Path taskFile = Path.of(submission.getBasePath() + "/tasks");
            testNames = Files.readAllLines(taskFile);
        } catch (IOException e) {
            throw new StageException("Unable to find tasks file");
        }
        for (String className : testNames) {
            className = "demos." + className + "Test";

            classResults.add(testClassDetails(className, solutionResults.get(className), submissionResults.get(className)));
            for (JUnitIndividualResult result : submissionResults.get(className)) {
                totalNumTests += result.weight();
            }
            innerResults.addAll(formatTestClass(className, solution, submissionResults.get(className)));
        }

        if (totalNumTests == 0) {
            // todo(mh): Do something better here
            throw new StageException("No tests were found");
        }

        var table = formatResultTable(classResults);

        double total = 0;
        for (ClassResult classResult : classResults) {
            total += classResult.passing();
        }

        var equation = "\n$$sum = "+total+"$$";
        var overview = new Result(name);
        overview.setScore(total)
                .setMaxScore(maxScore)
                .appendOutput(table + equation)
                .setOutputFormat("md")
                .setVisibility(Visibility.AFTER_PUBLISHED);

        return new StageResult(overview, innerResults);
    }
}
