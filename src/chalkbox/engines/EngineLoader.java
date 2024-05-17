package chalkbox.engines;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class responsible for initialising Engine instances.
 */
public class EngineLoader {

    /** No instance state. */
    private EngineLoader() {}

    /**
     * Loads an Engine by parsing the given YAML configuration file.
     *
     * Calls {@link Engine#validateConfig()} on the engine after it has been
     * loaded to ensure that the configuration file is valid.
     *
     * @param configPath path of YAML configuration file to load engine from
     * @return loaded engine
     * @throws ConfigFormatException if the engine's configuration is invalid
     */
    public static Engine load(String configPath) throws ConfigFormatException {
        String cwd = System.getProperty("user.dir");
        System.out.println("Working Directory = " + cwd);
        List<Object> documents = readConfig(configPath, new Yaml());
        Map<String, Object> header = (Map<String, Object>) documents.get(0);

        Class<?> engineClass;
        String engineClassName = String.valueOf(header.get("engine"));
        try {
            engineClass = Class.forName(engineClassName);
        } catch (ClassNotFoundException e) {
            throw new ConfigFormatException("Engine \"" + engineClassName
                    + "\" not found.", e);
        }

        /*
         * We have to read the config file again, this time using the
         * constructor of the engine class.
         */
        Representer representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);
        Yaml yaml = new Yaml(new Constructor(engineClass), representer);
        documents = readConfig(configPath, yaml);

        Engine engine = (Engine) documents.get(1);
        engine.validateConfig();

        return engine;
    }

    /**
     * Reads a YAML configuration file consisting of two documents.
     *
     * The first document should be a header specifying the class name of the
     * engine to use when interpreting the second document.
     *
     * @param configPath file path of config file to read
     * @param yaml YAML instance to use when reading file
     * @return a list of Objects where each Object is a YAML document
     * @throws ConfigFormatException if the file could not be found, or if there is
     *                               an incorrect number of documents in the file.
     * @ensures \result.size() == 2
     */
    private static List<Object> readConfig(String configPath, Yaml yaml)
            throws ConfigFormatException {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(configPath));
        } catch (FileNotFoundException e) {
            throw new ConfigFormatException("YAML config not found.", e);
        }
        List<Object> documents = new ArrayList<>();
        for (Object document : yaml.loadAll(reader)) {
            documents.add(document);
        }
        if (documents.size() != 2) {
            throw new ConfigFormatException("Incorrect number of documents in YAML config: "
                                            + "expected 2, found " + documents.size() + ".");
        }
        return documents;
    }
}
