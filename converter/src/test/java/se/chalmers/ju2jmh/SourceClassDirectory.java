package se.chalmers.ju2jmh;

import java.io.*;
import java.nio.file.Path;

public final class SourceClassDirectory {
    private final Path sourcesDirectory;
    private final Path bytecodeDirectory;

    public SourceClassDirectory(Path baseDirectory) {
        sourcesDirectory = baseDirectory.resolve("src");
        bytecodeDirectory = baseDirectory.resolve("classes");
    }

    public static SourceClassDirectory directoryWithClasses(Path baseDirectory, Class<?>... classes)
            throws IOException, ClassNotFoundException {
        SourceClassDirectory directory = new SourceClassDirectory(baseDirectory);
        for (Class<?> clazz : classes) {
            directory.add(clazz);
        }
        return directory;
    }

    private static void copyResourceToFile(ClassLoader classLoader, String resource, Path basePath)
            throws IOException, ClassNotFoundException {
        File outputFile = basePath.resolve(resource.replace('/', File.separatorChar)).toFile();
        if (outputFile.exists()) {
            return;
        }
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ClassNotFoundException("Could not find resource " + resource);
            }
            try (OutputStream out = new FileOutputStream(outputFile)) {
                in.transferTo(out);
            }
        }
    }

    private static String baseResourceName(Class<?> clazz) {
        return clazz.getName().replace('.', '/');
    }

    private static String sourceResourceName(Class<?> clazz) {
        String base = baseResourceName(clazz);
        int nestedClassMarkerIndex = base.indexOf('$');
        if (nestedClassMarkerIndex < 0) {
            return base + ".java";
        } else {
            return base.substring(0, nestedClassMarkerIndex) + ".java";
        }
    }

    private static String bytecodeResourceName(Class<?> clazz) {
        return baseResourceName(clazz) + ".class";
    }

    public void add(Class<?> clazz) throws IOException, ClassNotFoundException {
        addSource(clazz);
        addBytecode(clazz);
    }

    public void addSource(Class<?> clazz) throws IOException, ClassNotFoundException {
        copyResourceToFile(clazz.getClassLoader(), sourceResourceName(clazz), sourcesDirectory);
    }

    public void addBytecode(Class<?> clazz) throws IOException, ClassNotFoundException {
        copyResourceToFile(clazz.getClassLoader(), bytecodeResourceName(clazz), bytecodeDirectory);
    }

    public Path sourcesDirectory() {
        return sourcesDirectory;
    }

    public Path bytecodeDirectory() {
        return bytecodeDirectory;
    }
}
