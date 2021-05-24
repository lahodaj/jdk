/**
 * @test
 */

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

public class StandardJavaFileManagerTest {
    public static void main(String... args) throws Exception {
        new StandardJavaFileManagerTest().run();
    }

    private void run() throws Exception {
        verifyGetJavaFileObjects();
        verifyGetJavaFileObjectsFromPaths();
        verifySetLocationFromPaths();
    }

    void verifyGetJavaFileObjects() {
        var stJFM = new NoopStandardJavaFileManager();
        verifyNPEThrown(() -> stJFM.getJavaFileObjects((Path)null));
        verifyNPEThrown(() -> stJFM.getJavaFileObjects((Path)null, (Path)null));
        verifyNPEThrown(() -> stJFM.getJavaFileObjects(Path.of("a"), (Path)null));
    }

    static void verifyNPEThrown(Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected a NullPointerException, but got none.");
        } catch (NullPointerException ex) {
            //OK, expected
        }
    }

    void verifyGetJavaFileObjectsFromPaths() throws Exception {
        var stJFM = new NoopStandardJavaFileManager();
        Path jlObject = Paths.get(new URI("jrt:///modules/java.base/java/lang/Object.class"));
        List<Path> paths = Collections.singletonList(jlObject);
        verifyIAEThrown(() -> stJFM.getJavaFileObjectsFromPaths(paths));
        verifyIAEThrown(() -> stJFM.getJavaFileObjectsFromPaths((Iterable<Path>) paths));
        verifyIAEThrown(() -> {
            try {
                stJFM.setLocationFromPaths(StandardLocation.CLASS_PATH, paths);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        });
    }

    static void verifyIAEThrown(Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected a IllegalArgumentException, but got none.");
        } catch (IllegalArgumentException ex) {
            //OK, expected
        }
    }

    void verifySetLocationFromPaths() throws Exception {
        boolean[] setLocationCalled = new boolean[1];
        var stJFM = new NoopStandardJavaFileManager() {
            @Override
            public void setLocation(JavaFileManager.Location location, Iterable<? extends File> files) throws IOException {
                setLocationCalled[0] = true;
            }
        };
        Path base = Paths.get(".");
        stJFM.setLocationFromPaths(StandardLocation.CLASS_PATH, Collections.singletonList(base));
        if (!setLocationCalled[0]) {
            throw new AssertionError();
        }
    }

    private static class NoopStandardJavaFileManager implements StandardJavaFileManager {

        @Override public int isSupportedOption(String option) { return 0; }

        @Override public ClassLoader getClassLoader(Location location) { return null; }

        @Override public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException { return null; }

        @Override public String inferBinaryName(Location location, JavaFileObject file) { return null; }

        @Override public boolean isSameFile(FileObject a, FileObject b) { return false; }

        @Override public boolean handleOption(String current, Iterator<String> remaining) { return false; }

        @Override public boolean hasLocation(Location location) { return false; }

        @Override public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException { return null; }

        @Override public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException { return null; }

        @Override public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException { return null; }

        @Override public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException { return null; }

        @Override public void flush() throws IOException { }

        @Override public void close() throws IOException { }

        @Override public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) { return null; }

        @Override public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) { return null; }

        @Override public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) { return null; }

        @Override public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) { return null; }

        @Override public void setLocation(Location location, Iterable<? extends File> files) throws IOException { }

        @Override public Iterable<? extends File> getLocation(Location location) { return null; }
    }

}
