package chalkbox.source;

import chalkbox.api.common.java.Compiler;
import chalkbox.api.files.FileSourceFile;
import chalkbox.stages.StageException;
import chalkbox.stages.conformance.SourceLoader;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Source {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final String basePath;
    private String classPath = "";
    private String name;
    private CompilationResult srcCompilation;
    private CompilationResult testCompilation;

    public Source(String name, String root, List<String> classPath) {
        this.name = name;
        var path = Paths.get(root);
        this.basePath = path.toAbsolutePath().toString();
        if (classPath != null) {
            for (var item : classPath) {
                if (!this.classPath.isEmpty()) {
                    this.classPath += ":";
                }
                this.classPath += Paths.get(item).toAbsolutePath();
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public List<String> validateStructure(List<String> paths) {
        List<String> missing = new ArrayList<>();
        for (String path : paths) {
            File file = new File(basePath + "/" + path);
            if (!file.exists()) {
                missing.add(path);
            }
        }
        return missing;

        //throw new StageException("Expecting to find the following paths in "
        //        + getName() + " that were absent: \n" + String.join("\n", missing));
    }

    public String getClassPath() {
        return this.classPath;
    }

    public CompilationResult compileSrc() throws IOException {
        if (srcCompilation != null) {
            return srcCompilation;
        }
        return compile(getSrcBuildPath(), getSrcJavaFiles(), "");
    }

    public CompilationResult compileTest(String sourcePath) throws IOException {
        if (testCompilation != null) {
            return testCompilation;
        }
        return compile(
            getTestBuildPath(),
            getTestJavaFiles(),
            sourcePath
        );
    }

    public CompilationResult compileTest() throws IOException {
        return compileTest(getSrcBuildPath());
    }

    private CompilationResult compile(
        String destination,
        List<FileSourceFile> sourceFiles,
        String additionalClasspath
    ) throws IOException {
        if (sourceFiles == null) {
            throw new StageException("Couldn't load source files");
        }

        var build = new File(destination);

        // Check if the folder exists
        if (!build.exists()) {
            if (build.mkdirs()) {
                logger
                    .atInfo()
                    .log("build filepath created %s", build.getAbsolutePath());
            } else {
                logger
                    .atSevere()
                    .log(
                        "unable to make build path %s",
                        build.getAbsolutePath()
                    );
                throw new StageException("Unable to make build path");
            }
        }

        var classPath = this.classPath;
        if (!additionalClasspath.isEmpty()) {
            classPath += File.pathSeparator + additionalClasspath;
        }

        var output = new StringWriter();
        var success = Compiler.compile(
            sourceFiles,
            classPath,
            build.getAbsolutePath(),
            output
        );

        return new CompilationResult(success, output.toString());
    }

    public String getBasePath() {
        return basePath;
    }

    public String getSrcBuildPath() {
        return basePath + "/build/classes/";
    }

    public String getSrcFolder() {
        return basePath + "/src";
    }

    public String getTestBuildPath() {
        return basePath + "/build/test/";
    }

    public String getTestFolder() {
        return basePath + "/test";
    }

    public List<FileSourceFile> getSrcJavaFiles() throws IOException {
        return getFilesWithExtension(getSrcFolder(), ".java");
    }

    public List<String> getSrcClasses() throws IOException {
        return getClasses(getSrcFolder());
    }

    public List<FileSourceFile> getTestJavaFiles() throws IOException {
        return getFilesWithExtension(getTestFolder(), ".java");
    }

    public List<String> getTestClasses() throws IOException {
        return getClasses(getTestFolder());
    }

    private List<String> getClasses(String root) throws IOException {
        var start = Paths.get(root);

        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> getClassName(start.relativize(path).toString()))
                .collect(Collectors.toList());
        }
    }

    /**
     * Get the class name of a file path.
     *
     * <p>Removes the java extension and replaces paths with dots.
     *
     * <p>If it's within src/ or test/ those folders are removed.
     *
     * <p>Examples:
     * <pre>
     * src/package1/ClassOne.java -&gt; package1.ClassOne
     * package1/ClassOne.java -&gt; package1.ClassOne
     * test/package1/ClassOne.java -&gt; package1.ClassOne
     * src/package1/package2/ClassOne.java -&gt; package1.package2.ClassOne
     * src/ClassOne.java -&gt; ClassOne
     * </pre>
     * @param filePath File path of the class
     * @return The name of the class
     */
    public static String getClassName(String filePath) {
        if (filePath.startsWith("/src/")) {
            filePath = filePath.replace("/src/", "");
        }
        if (filePath.startsWith("/test/")) {
            filePath = filePath.replace("/test/", "");
        }
        return filePath
            .replace(".java", "")
            .replace("/", ".")
            .replace(File.separator, ".");
    }

    /**
     * Get the path of a class from it's class name.
     *
     * <p>Examples:
     * <pre>
     * package1.ClassOne -&gt; package1/ClassOne.java
     * ClassOne -&gt; ClassOne.java
     * </pre>
     *
     * @param className Name of the class
     * @return File path for a class
     */
    public static String getPathName(String className) {
        return className.replace(".", File.separator) + ".java";
    }

    private List<FileSourceFile> getFilesWithExtension(
        String root,
        String extension
    ) throws IOException {
        var start = Paths.get(root);

        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(extension))
                .map(path -> new FileSourceFile(path.toString(), path.toFile()))
                .collect(Collectors.toList());
        }
    }

    public SourceLoader getSrcLoader() throws IOException {
        return getSourceLoader(getSrcBuildPath());
    }

    private SourceLoader getSourceLoader(String directory) throws IOException {
        URL[] urls = Arrays
            .stream(classPath.split(":"))
            .filter(e -> !e.isEmpty())
            .map(e -> {
                try {
                    return new URI("file://" + e).toURL();
                } catch (MalformedURLException | URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
            })
            .toArray(URL[]::new);
        var loader = new URLClassLoader(urls);
        return new SourceLoader(directory, loader);
    }
}
