package chalkbox.api.common.java;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.junit.runner.JUnitCore;

/**
 * Utility class to execute a JUnit test.
 */
public class JUnitRunner {

    // Runs all tests in the given class and returns a single output object
    public static JUnitResult runTestsCombined(
        String className,
        String classPath
    ) {
        return run(className, classPath).getResultsForClass();
    }

    // Runs all tests in the given class and returns an output object for each @Test
    public static List<JUnitIndividualResult> runTests(
        String className,
        String classPath
    ) {
        return run(className, classPath).getIndividualResults();
    }

    private static JUnitListener run(String className, String classPath) {
        System.out.println("Testing " + className);
        JUnitListener listener = new JUnitListener();
        JUnitCore runner = new JUnitCore();
        runner.addListener(listener);

        String[] classPathEntries = classPath.split(
            System.getProperty("path.separator")
        );
        URL[] classPathUrls = new URL[classPathEntries.length];
        for (int i = 0; i < classPathEntries.length; ++i) {
            try {
                classPathUrls[i] =
                new File(classPathEntries[i]).toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        URLClassLoader classLoader = new URLClassLoader(classPathUrls);
        try {
            runner.run(classLoader.loadClass(className));
        } catch (ClassNotFoundException e) {
            listener.classMissing();
            e.printStackTrace();
        }
        return listener;
    }
}
