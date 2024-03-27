/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import jdk.internal.shellsupport.doc.JavadocHelper;
//import jtreg.SkippedException;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SinceCheckerHelper {

    //these are methods that were preview in JDK 13 and JDK 14, before the introduction
    //of the @PreviewFeature
    //TODO IIRC we will need a bit more of this when we include java.compiler and jdk.compiler. I think I would create
    // a separated section for detection of "isPreview", which would include this set (which may need to be changed to a map,
    // saying in which versions the given element was a preview).
    private final Set<String> LEGACY_PREVIEW_METHODS = Set.of(
            //13
            "method:java.lang.String:stripIndent:()",
            "method:java.lang.String:translateEscapes:()",
            "method:java.lang.String:formatted:(java.lang.Object[])",
            "method:com.sun.source.util.SimpleTreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "method:com.sun.source.util.TreeScanner:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "method:com.sun.source.tree.TreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "field:com.sun.source.tree.Tree.Kind:YIELD",
            "interface:com.sun.source.tree.YieldTree"
            //14
    );

    private static final String JDK13 = "13";
    private static final String JDK14 = "14";
    private final Map<String, IntroducedIn> classDictionary = new HashMap<>();
    private final JavaCompiler tool;

    private final List<String> wrongTagsList = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        SinceCheckerHelper sinceCheckerTestHelper = new SinceCheckerHelper();

        if (args.length == 0) {
            System.out.println("No module specified. Exiting...");
            System.exit(1);
        }
        sinceCheckerTestHelper.testThisModule(args[0]);
    }

    private SinceCheckerHelper() throws Exception {
        tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= Runtime.version().feature(); i++) {
            JavacTask ct = (JavacTask) tool.getTask(null, null, null,
                    List.of("--release", String.valueOf(i)), null,
                    Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
            ct.analyze();
            String version = String.valueOf(i);
            ct.getElements().getAllModuleElements().forEach(me ->
                    processModuleRecord(me, version, ct));
        }
    }

    private void processModuleRecord(ModuleElement moduleElement, String releaseVersion, JavacTask ct) {
        // handle exception here
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackageRecord(ed.getPackage(), releaseVersion, ct);
            }
        }
    }

    private void analyzePackageRecord(PackageElement pe, String s, JavacTask ct) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            analyzeClassRecord(te, s, ct.getTypes(), ct.getElements());
        }
    }

    private void analyzeClassRecord(TypeElement te, String version, Types types, Elements elements) {
        if (!te.getModifiers().contains(Modifier.PUBLIC)) {
            return;
        }
        persistElement(te, te, types, version);
        elements.getAllMembers(te).stream()
                .filter(element -> element.getModifiers().contains(Modifier.PUBLIC))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> persistElement(te, element, types, version));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassRecord(nestedClass, version, types, elements));
    }

    public  void persistElement(TypeElement clazz, Element element, Types types, String version) {
        String uniqueId = getElementName(clazz, element, types);
        IntroducedIn introduced = classDictionary.computeIfAbsent(uniqueId, i -> new IntroducedIn());
        if (isPreview(element, uniqueId, version)) {
            if (introduced.introducedPreview == null) {
                introduced.introducedPreview = version;
            }
        } else {
            if (introduced.introducedStable == null) {
                introduced.introducedStable = version;
            }
        }
    }

    private boolean isPreview(Element el, String uniqueId, String currentVersion) {
        while (el != null) {
            Symbol s = (Symbol) el;
            if ((s.flags() & Flags.PREVIEW_API) != 0) {
                return true;
            }
            el = el.getEnclosingElement();
        }
        return LEGACY_PREVIEW_METHODS.contains(uniqueId)
                &&
                (JDK13.equals(currentVersion) || JDK14.equals(currentVersion));
    }

    private void testThisModule(String moduleName) throws Exception {
        List<Path> sources = new ArrayList<>();

//        Path home = Paths.get(System.getProperty("java.home"));
//        Path srcZip = home.resolve("lib").resolve("src.zip");
        Path srcZip = Path.of(pathToAPIKEY.pathToSRC);
        File f = new File(srcZip.toUri());
        if (!f.exists() && !f.isDirectory()) {
//          throw new SkippedException("Skipping Test because src.zip wasn't found");
            throw new Exception("Skipping Test because src.zip wasn't found");
        }
        if (Files.isReadable(srcZip)) {
            URI uri = URI.create("jar:" + srcZip.toUri());
            try (FileSystem zipFO = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path root = zipFO.getRootDirectories().iterator().next();
                Path packagePath = root.resolve(moduleName);
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                    for (Path p : ds) {
                        if (Files.isDirectory(p)) {
                            sources.add(p);
                        }
                    }
                    try (StandardJavaFileManager fm =
                                 tool.getStandardFileManager(null, null, null)) {
                        JavacTask ct = (JavacTask) tool.getTask(null,
                                fm,
                                null,
                                List.of("--limit-modules", moduleName, "-d", "."),
                                null,
                                Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
                        ct.analyze();
                        processModuleCheck(ct.getElements().getModuleElement(moduleName), ct, sources, packagePath);
                        if (!wrongTagsList.isEmpty()) {
                            throw new Exception(wrongTagsList.toString());
                        }
                    }

                }
            }
        }
    }

    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, List<Path> sources, Path packagePath) {
        //TODO handle expection here
        if (moduleElement == null) {
            throw new RuntimeException("Module element was null here");
        }
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                Version packageTopVersion = getPackageTopVersion(packagePath, ed);
                analyzePackageCheck(ed.getPackage(), ct, sources, packageTopVersion);
            }
        }
    }

    private Version getPackageTopVersion(Path packagePath, ModuleElement.ExportsDirective ed) {
        Path pkgInfo = packagePath.resolve(ed.getPackage()
                        .getQualifiedName()
                        .toString()
                        .replaceAll("\\.", "/")
                )
                .resolve("package-info.java");
        Files.exists(pkgInfo);
        byte[] packageAsBytes;
        try {
            packageAsBytes = Files.readAllBytes(pkgInfo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String packageContent = new String(packageAsBytes, StandardCharsets.UTF_8);
        Version packageTopVersion = getSinceFromPackage(packageContent);
        ed.getPackage().getModifiers();
        return packageTopVersion;
    }

    private Version getSinceFromPackage(String packageContent) {
        return extractSinceVersion(packageContent);
    }

    private void analyzePackageCheck(PackageElement pe, JavacTask ct, List<Path> sources, Version packageTopVersion) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            try (JavadocHelper javadocHelper = JavadocHelper.create(ct, sources)) {
                // TODO package version should equal @since when a class doesn't have @since
                analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), packageTopVersion);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void analyzeClassCheck(TypeElement te, String version, JavadocHelper javadocHelper,
                                   Types types, Version enclosingVersion) {
        if (!te.getModifiers().contains(Modifier.PUBLIC)) {
            return;
        }
        Version currentVersion = checkElement(te, te, types, javadocHelper, version, enclosingVersion);
        te.getEnclosedElements().stream().filter(element -> element.getModifiers().contains(Modifier.PUBLIC))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> checkElement(te, element, types, javadocHelper, version, currentVersion));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassCheck(nestedClass, version, javadocHelper, types, currentVersion));
    }


    private Version checkElement(TypeElement clazz, Element element, Types types,
                                 JavadocHelper javadocHelper, String currentVersion, Version enclosingVersion) {
        String uniqueId = getElementName(clazz, element, types);
        String comment = null;
        try {
            comment = javadocHelper.getResolvedDocComment(element);
        } catch (IOException e) {
            throw new RuntimeException("JavadocHelper failed for " + element);
        }
        Version sinceVersion = comment != null ? extractSinceVersion(comment) : null;
        if (sinceVersion == null ||
                (enclosingVersion != null && enclosingVersion.compareTo(sinceVersion) > 0)) {
            sinceVersion = enclosingVersion;
        }
        IntroducedIn mappedVersion = classDictionary.get(uniqueId);
        //TODO handle expection here
        String realMappedVersion = null;
        try {
            realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                    mappedVersion.introducedPreview :
                    mappedVersion.introducedStable;
        } catch (Exception e) {
            System.err.println(mappedVersion + " is null");
        }
        checkEquals(sinceVersion, realMappedVersion, uniqueId);
        return sinceVersion;
    }


    private static Version extractSinceVersion(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            String versionString = matcher.group(1);
            //TODO Won't the next condition (.startsWith("1.")) handle this?
            try {
                if (versionString.equals("1.0")) {
                    //deals with @since version 0
                    versionString = "1";
                } else if (versionString.startsWith("1.")) {
                    versionString = versionString.substring(2);
                }
                return Version.parse(versionString);
            } catch (NumberFormatException ex) {
                System.err.println("@since value that cannot be parsed: " + versionString);
                return null;
            }
        } else {
            return null;
        }
    }


    private void checkEquals(Version sinceVersion, String mappedVersion, String elementSimpleName) {
        if (sinceVersion == null || mappedVersion == null) {
            throw new IllegalArgumentException("For " + elementSimpleName + " mapped is=" + mappedVersion + " since is= " + sinceVersion);
        }
        //TODO Handle base line better
        if (Version.parse("9").compareTo(sinceVersion) > 0) {
            sinceVersion = Version.parse("9");
        }
        // TODO For consideration - if the since version if not a number, should we try to verify it is one of known (existing) patterns,
        //  and fail if it is not? So that we would find out if some new non-number version would be introduced?
        if (!sinceVersion.equals(Version.parse(mappedVersion))) {
            wrongTagsList.add("For  Element: " + elementSimpleName
                    + " Wrong since version " + sinceVersion + " instead of " + mappedVersion + "\n");
        }
    }

    private String getElementName(TypeElement te, Element element, Types types) {
        String prefix = "";
        String suffix = "";

        if (element.getKind().isField()) {
            prefix = "field";
            suffix = ":" + te.getQualifiedName() + ":" + element.getSimpleName();
        } else if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR) {
            prefix = "method";
            ExecutableElement executableElement = (ExecutableElement) element;
            String methodName = executableElement.getSimpleName().toString();
            String descriptor = executableElement.getParameters().stream()
                    .map(p -> types.erasure(p.asType()).toString())
                    .collect(Collectors.joining(",", "(", ")"));
            suffix = ":" + te.getQualifiedName() + ":" + methodName + ":" + descriptor;
        } else if (element.getKind().isDeclaredType()) {
            if (element.getKind().isClass()) {
                prefix = "class";
                suffix = ":" + te.getQualifiedName();
            } else if (element.getKind().isInterface()) {
                prefix = "interface";
                suffix = ":" + te.getQualifiedName();
            }
        }
        return prefix + suffix;
    }

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }
}