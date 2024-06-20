package chalkbox.engines;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

public class PythonEngineTest {

    private static final String BASE_FOLDER = "./test/resources/csse1001/";

    private static final String CONFIG_BASIC_PATH = BASE_FOLDER + "config-basic.yml";
    private static final String RESULTS_PATH = BASE_FOLDER + "results/results.json";
    private static final String EXPECTED_BASIC_PATH = BASE_FOLDER + "results/expected-basic.json";

    @Test
    public void testEngineBasic() throws IOException {
        Engine engine = null;
        try {
            engine = EngineLoader.load(CONFIG_BASIC_PATH);
        } catch (ConfigFormatException e) {
            fail("Configuration file should not be invalid");
        }
        engine.run();

        String expected = Files.readString(Paths.get(EXPECTED_BASIC_PATH),
                StandardCharsets.UTF_8);
        String actual = Files.readString(Paths.get(RESULTS_PATH),
                StandardCharsets.UTF_8);
        actual = actual.replace("\\r\\n","\\n");
        System.err.println("expected: " + expected);
        System.err.println("actual: " + actual);
        assertEquals("Output JSON files are different", expected, actual);
    }

}