package chalkbox.stages.functionality;

import chalkbox.api.common.java.JUnitIndividualResult;
import chalkbox.source.Solution;
import chalkbox.source.Submission;
import chalkbox.stages.*;

import java.io.IOException;
import java.util.*;

public class Functionality extends BaseFunctionalityStage {

    public final static String name = "Functionality";

    public Functionality(double maxScore, boolean showPassing, boolean allVisible) {
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
        for (String className : solution.getTestClasses()) {
            if (!className.endsWith("Test")) {
                continue;
            }

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
        double possible = 0;
        for (ClassResult classResult : classResults) {
            total += classResult.passing();
            possible += classResult.weight();
        }

        double scaled = Math.ceil((total / possible) * maxScore);

        var equation = "\n$$\n\\dfrac{" + String.format("%.3f", total) + "}{" + possible + "} \\times " + maxScore + " = " + scaled + "\n$$";
        var overview = new Result(name);
        overview.setScore(scaled)
                .setMaxScore(maxScore)
                .appendOutput(table + equation)
                .setOutputFormat("md")
                .setVisibility(Visibility.AFTER_PUBLISHED);

        return new StageResult(overview, innerResults);
    }

}