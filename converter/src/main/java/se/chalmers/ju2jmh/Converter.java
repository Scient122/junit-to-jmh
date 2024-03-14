package se.chalmers.ju2jmh;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import picocli.CommandLine;
import se.chalmers.ju2jmh.api.ExceptionTest;
import se.chalmers.ju2jmh.api.JU2JmhBenchmark;
import se.chalmers.ju2jmh.api.Rules;
import se.chalmers.ju2jmh.api.ThrowingConsumer;
import se.chalmers.ju2jmh.model.UnitTestClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "ju2jmh", mixinStandardHelpOptions = true)
public class Converter implements Callable<Integer> {
    @CommandLine.Parameters(description =
            "Root path(s) of the input source files. `${sys:path.separator}` may be used as a"
                    + " separator to specify multiple directories.", index = "0")
    private String sourcePath;

    @CommandLine.Parameters(description =
            "Root path(s) of the input class files. `${sys:path.separator}` may be used as a"
                    + " separator to specify multiple directories.", index = "1")
    private String classPath;

    @CommandLine.Parameters(description = "Root path for the output source files.", index = "2")
    private Path outputPath;

    @CommandLine.Parameters(
            description = "Fully qualified names of the classes to convert to benchmarks.",
            index = "3..*")
    private List<String> classNames;

    @CommandLine.Option(
            names = {"--ju4-runner-benchmark"},
            description = "Generate benchmarks delegating their execution to the JUnit 4 JUnitCore "
                    + "runner.")
    private boolean ju4RunnerBenchmark;

    @CommandLine.Option(
            names = {"--tailored-benchmark"},
            description = "Generate benchmarks customised to and optimised based on the specific "
                    + "JUnit features used by the individual tests.")
    private boolean tailoredBenchmark;

    @CommandLine.Option(
            names = {"-i", "--ignore-failures"},
            description = "Generate the remaining benchmark classes even if conversion of some "
                    + "input classes fails.")
    private boolean ignoreFailures;

    @CommandLine.Option(
            names = {"--class-names-file"},
            description = "File to load class names from.")
    private Path classNamesFile;

    @CommandLine.Option(
            names = {"--fork"},
            description = {"The number of forks which will be created."}
    )
    private int fork=5;

    @CommandLine.Option(
            names = {"--warmup-iterations"},
            description = {"The number of warm up iterations."}
    )
    private int warmup_iterations=5;

    @CommandLine.Option(
            names = {"--warmup-time"},
            description = {"The time for each warmup iteration"}
    )
    private int warmup_time = 10;

    @CommandLine.Option(
            names={"--warmup-time-unit"},
            description={"Time unit used for warm up iterations' length"}
    )
    private String warmup_time_unit = "seconds";

    @CommandLine.Option(
            names = {"--measurement-iterations"},
            description = {"The number of measurement iterations."}
    )
    private int measurement_iterations=5;

    @CommandLine.Option(
            names={"--measurement-time"},
            description = {"The time for each measurement iteration"}
    )
    private int measurement_time=10;

    @CommandLine.Option(
            names = {"--measurement-time-unit"},
            description = {"Time unit used for measurement iterations' length"}
    )
    private String measurement_time_unit = "seconds";

    @CommandLine.Option(
            names = {"--benchmark-mode"},
            description = {"The benchmark mode desired."}
    )
    private String benchmark_mode="throughput";

    @CommandLine.Option(
            names = {"--output-time-unit"},
            description = {"The output time unit desired."}
    )
    private String output_time_unit="seconds";

    private String[] modes = {"throughput","average-time","sample-time","single-shot-time","all"};
    private String[] time_units = {"seconds","nanoseconds","milliseconds","microseconds","minutes","hours","days"};

    private static CompilationUnit loadApiSource(Class<?> apiClass) throws IOException {
        return StaticJavaParser.parseResource(
                apiClass.getCanonicalName().replace('.', '/') + ".java");
    }

    private static void writeSourceCodeToFile(CompilationUnit benchmark, File outputFile)
            throws IOException {
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();
        try (OutputStreamWriter out = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            out.append(benchmark.toString());
        }
    }

    private void generateNestedBenchmarks() throws ClassNotFoundException, IOException {
        NestedBenchmarkSuiteBuilder benchmarkSuiteBuilder =
                new NestedBenchmarkSuiteBuilder(toPaths(sourcePath), toPaths(classPath),fork,warmup_iterations,measurement_iterations,benchmark_mode,output_time_unit,warmup_time,measurement_time,warmup_time_unit,measurement_time_unit);
        for (String className : classNames) {
            benchmarkSuiteBuilder.addTestClass(className);
        }
        Map<String, CompilationUnit> suite = benchmarkSuiteBuilder.buildSuite();
        for (String className : suite.keySet()) {
            File outputFile =
                    outputPath.resolve(className.replace('.', File.separatorChar) + ".java")
                            .toFile();
            writeSourceCodeToFile(suite.get(className), outputFile);
        }
        writeSourceCodeToFile(
                loadApiSource(JU2JmhBenchmark.class),
                outputPath.resolve(
                        JU2JmhBenchmark.class.getCanonicalName()
                                .replace('.', File.separatorChar) + ".java")
                        .toFile());
    }

    private void generateJU4Benchmarks()
            throws ClassNotFoundException, IOException, InvalidInputClassException {
        InputClassRepository repository =
                new InputClassRepository(toPaths(sourcePath), toPaths(classPath));
        JU4BenchmarkFactory benchmarkFactory = new JU4BenchmarkFactory(repository);
        List<CompilationUnit> benchmarks = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                benchmarks.add(benchmarkFactory.createBenchmarkFromTest(className));
            } catch (BenchmarkGenerationException e) {
                if (!ignoreFailures) {
                    throw e;
                }
            }
        }
        for (CompilationUnit benchmark : benchmarks) {
            TypeDeclaration<?> benchmarkClass = benchmark.getTypes().get(0);
            String benchmarkClassName = benchmarkClass.getFullyQualifiedName().orElseThrow();
            File outputFile = outputPath.resolve(
                    benchmarkClassName.replace('.', File.separatorChar) + ".java").toFile();
            writeSourceCodeToFile(benchmark, outputFile);
        }
    }

    private static void loadMissingCompilationUnits(
            String packageName, File file, InputClassRepository repository,
            Map<String, CompilationUnit> compilationUnits) throws ClassNotFoundException {
        String name = file.getName();
        if (file.isFile()) {
            if (name.endsWith(".java")) {
                String className =
                        packageName + name.substring(0, name.length() - ".java".length());
                if (!compilationUnits.containsKey(className)) {
                    compilationUnits.put(
                            className,
                            repository.findClass(className)
                                    .getSource()
                                    .findCompilationUnit()
                                    .orElseThrow());
                }
            }
        } else if (file.isDirectory()) {
            packageName = packageName + name + ".";
            File[] containedFiles = file.listFiles();
            if (containedFiles == null) {
                return;
            }
            for (File containedFile : containedFiles) {
                loadMissingCompilationUnits(
                        packageName, containedFile, repository, compilationUnits);
            }
        }
    }

    private void generateTailoredBenchmarks() throws ClassNotFoundException, IOException {
        InputClassRepository repository =
                new InputClassRepository(toPaths(sourcePath), toPaths(classPath));
        UnitTestClassRepository testClassRepository = new UnitTestClassRepository(repository);
        Map<String, CompilationUnit> compilationUnits = new HashMap<>();
        for (String className : classNames) {
            UnitTestClass testClass = testClassRepository.findClass(className);
            Predicate<String> nameValidator =
                    TailoredBenchmarkFactory.nameValidatorForCompilationUnit(
                            repository.findClass(className)
                                    .getSource()
                                    .findCompilationUnit()
                                    .orElseThrow());
            ClassOrInterfaceDeclaration benchmarkClass =
                    TailoredBenchmarkFactory.generateBenchmarkClass(testClass, nameValidator);
            TypeDeclaration<?> testClassSource = repository.findClass(className).getSource();
            testClassSource.addMember(benchmarkClass);
            compilationUnits.put(className, testClassSource.findCompilationUnit().orElseThrow());
        }
        for (Path path : toPaths(sourcePath)) {
            File[] files = path.toFile().listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                loadMissingCompilationUnits("", file, repository, compilationUnits);
            }
        }
        compilationUnits.put(
                ExceptionTest.class.getCanonicalName(), loadApiSource(ExceptionTest.class));
        compilationUnits.put(Rules.class.getCanonicalName(), loadApiSource(Rules.class));
        compilationUnits.put(
                ThrowingConsumer.class.getCanonicalName(), loadApiSource(ThrowingConsumer.class));
        for (String className : compilationUnits.keySet()) {
            CompilationUnit benchmark = compilationUnits.get(className);
            File outputFile = outputPath.resolve(
                    className.replace('.', File.separatorChar) + ".java").toFile();
            writeSourceCodeToFile(benchmark, outputFile);
        }
    }

    private static List<Path> toPaths(String pathString) {
        return Arrays.stream(pathString.split(File.pathSeparator))
                .map(Path::of)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Integer call() throws ClassNotFoundException, IOException, InvalidInputClassException {
        if (!outputPath.toFile().exists()) {
            throw new FileNotFoundException("Output directory " + outputPath + " does not exist");
        }
        if (classNamesFile != null) {
            try (Stream<String> lines = Files.lines(classNamesFile)) {
                if (classNames == null) {
                    classNames = lines.collect(Collectors.toUnmodifiableList());
                } else {
                    classNames = Stream.concat(classNames.stream(), lines).collect(Collectors.toUnmodifiableList());
                }
            }
        }
        if(fork <= 0){
            throw new RuntimeException("fork option must be >= than 1");
        }


        if(warmup_iterations <0){
            throw new RuntimeException("warmup iterations option must be >= than 0");
        }

        if(warmup_time <= 0){
            throw new RuntimeException("warm up time option must be > than 0");
        }

        if(measurement_iterations <= 0){
            throw new RuntimeException("measurement iterations option must be >= than 1");
        }

        if(measurement_time <= 0){
            throw new RuntimeException("measurement time option must be > than 0");
        }

        boolean mode_found = false;

        for(String mode : modes) {

            if (benchmark_mode.equals(mode)) {
                mode_found = true;
                break;
            }
        }

        if(!mode_found)
            throw new RuntimeException("Benchmark mode is not valid. These are the legit values: throughput average-time sample-time single-shot-time all");

        boolean unit_found = false;

        for(String unit : time_units){
            if(output_time_unit.equals(unit)){
                unit_found = true;
                break;
            }
        }

        if(!unit_found)
            throw new RuntimeException("Output time unit is not valid. These are the legit values: nanoseconds microseconds milliseconds seconds minutes hours days");

        boolean warmup_time_unit_found=false;
        for(String unit : time_units){
            if(warmup_time_unit.equals(unit)){
                warmup_time_unit_found=true;
                break;
            }
        }

        if(!warmup_time_unit_found)
            throw new RuntimeException("Warmup time unit is not valid. These are the legit values: nanoseconds microseconds milliseconds seconds minutes hours days");

        boolean measurement_time_unit_found=false;
        for(String unit : time_units){
            if(measurement_time_unit.equals(unit)){
                measurement_time_unit_found=true;
                break;
            }
        }

        if(!measurement_time_unit_found)
            throw new RuntimeException("Measurement time unit is not valid. These are the legit values: nanoseconds microseconds milliseconds seconds minutes hours days");



        if (!ju4RunnerBenchmark) {
            if (!tailoredBenchmark) {
                generateNestedBenchmarks();
            } else {
                generateTailoredBenchmarks();
            }
        } else {
            generateJU4Benchmarks();
        }
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Converter()).execute(args);
        System.exit(exitCode);
    }
}
