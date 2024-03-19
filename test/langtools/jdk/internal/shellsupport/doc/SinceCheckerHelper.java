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
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SinceCheckerHelper {

    //these are methods that were preview in JDK 13 and JDK 14, before the introduction
    //of the @PreviewFeature
    static final Set<String> LEGACY_PREVIEW_METHODS = Set.of(
            "method:java.lang.String:stripIndent:()",
            "method:java.lang.String:translateEscapes:()",
            "method:java.lang.String:formatted:(java.lang.Object[])");

    static final int JdkStart = 9, currJDK = Runtime.version().feature();
    static final String JDK13 = "13";
    static final String JDK14 = "14";
    public Map<String, IntroducedIn> classDictionary = new HashMap<>();
    public JavaCompiler tool;
    List<String> errors = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        SinceCheckerHelper sinceCheckerTestHelper = new SinceCheckerHelper();

        if (args.length == 0) {
            System.out.println("No module specified. Exiting...");
            System.exit(1);
        }
        sinceCheckerTestHelper.testThisModule(args[0]);
    }

    public SinceCheckerHelper() throws IOException {
        tool = ToolProvider.getSystemJavaCompiler();
        for (int i = JdkStart; i <= currJDK; i++) {
            JavacTask ct = (JavacTask) tool.getTask(null, null, null,
                    List.of("--release", String.valueOf(i)), null,
                    Collections.singletonList(new JavaSource()));
            ct.analyze();
            String version = String.valueOf(i);
            ct.getElements().getAllModuleElements().forEach(me ->
                    processModuleRecord(me, version, ct));

        }
    }

    public void processModuleRecord(ModuleElement moduleElement, String releaseVersion, JavacTask ct) {
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
                .filter(element -> element.getKind().isClass())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassRecord(nestedClass, version, types, elements));
    }

    public void persistElement(TypeElement clazz, Element element, Types types, String version) {
        String uniqueId = getElementName(clazz, element, types);
        classDictionary.computeIfAbsent(uniqueId,
                i -> new IntroducedIn(null, null));

        IntroducedIn introduced = classDictionary.get(uniqueId);

        if (isPreview(element, uniqueId, version)) {
            if (introduced.introducedPreview() == null) {
                classDictionary.put(uniqueId,
                        new IntroducedIn(version, introduced.introducedStable()));
            }
        } else {
            if (introduced.introducedStable() == null) {
                classDictionary.put(uniqueId,
                        new IntroducedIn(introduced.introducedPreview(), version));
            }
        }
    }

    public void testThisModule(String moduleName) throws Exception {
        List<Path> sources = new ArrayList<>();

        Path home = Paths.get(System.getProperty("java.home"));
        Path srcZip = home.resolve("lib").resolve("src.zip");

        File f = new File(srcZip.toUri());
        if (!f.exists() && !f.isDirectory()) {
//            throw new SkippedException("Skipping Test because src.zip wasn't found");
            throw new Exception("Skipping Test because src.zip wasn't found");
        }
        if (Files.isReadable(srcZip)) {
            URI uri = URI.create("jar:" + srcZip.toUri());
            try (FileSystem zipFO = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path root = zipFO.getRootDirectories().iterator().next();
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
                                Collections.singletonList(new JavaSource()));
                        ct.analyze();
                        ct.getElements().getAllModuleElements().stream()
                                .forEach(me -> processModuleCheck(me, null, ct, sources));
                        if (!errors.isEmpty()) {
                            throw new Exception(errors.toString());
                        }
                    }

                }
            }
        }
    }

    public Version checkElement(JavadocHelper javadocHelper, String uniqueId,
                                String currentVersion, Version enclosingVersion, Element element) {
        String comment = null;
        try {
            comment = javadocHelper.getResolvedDocComment(element);
            Version sinceVersion = comment != null ? extractSinceVersion(comment) : null;
            if (sinceVersion == null ||
                    (enclosingVersion != null && enclosingVersion.compareTo(sinceVersion) > 0)) {
                sinceVersion = enclosingVersion;
            }
            IntroducedIn mappedVersion = classDictionary.get(uniqueId);
            try {
                String realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                        mappedVersion.introducedPreview() :
                        mappedVersion.introducedStable();
                checkEquals(sinceVersion, realMappedVersion, uniqueId);
            } catch (Exception e) {
                System.err.println("Error for " + uniqueId + " " + e.getMessage());
            }
            return sinceVersion;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        boolean legacyPreview = LEGACY_PREVIEW_METHODS.contains(uniqueId)
                &&
                (JDK13.equals(currentVersion) || JDK14.equals(currentVersion));
        return legacyPreview;
    }

    public Version checkElement(TypeElement clazz, Element element, Types types,
                                JavadocHelper javadocHelper, String currentVersion, Version enclosingVersion) {
        String uniqueId = getElementName(clazz, element, types);
        return checkElement(javadocHelper, uniqueId, currentVersion, enclosingVersion, element);
    }

    private Version extractSinceVersion(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            String versionString = matcher.group(1);

            if (versionString.equals("1.0")) {
                //XXX
                versionString = "1";
            } else if (versionString.startsWith("1.")) {
                versionString = versionString.substring(2);
            }

            try {
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
        try {
            if (sinceVersion == null) {
                return;
            }
            if (mappedVersion == null) {
                System.out.println("check for why mapped version is null for" + elementSimpleName);
                return;
            }
            if (Version.parse("9").compareTo(sinceVersion) > 0) {
                sinceVersion = Version.parse("9"); //TODO: handle baseline version better
            }
            if (!sinceVersion.equals(Version.parse(mappedVersion))) {
                errors.add("For  Element: " + elementSimpleName
                        + " Wrong since version " + sinceVersion + " instead of " + mappedVersion + "\n");
            }
        } catch (NumberFormatException e) {
            System.err.println("Element: " + elementSimpleName + "\t Invalid number: " + sinceVersion);
        }
    }


    private void processModuleCheck(ModuleElement moduleElement, String releaseVersion, JavacTask ct, List<Path> sources) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackageCheck(ed.getPackage(), releaseVersion, ct, sources);
            }
        }
    }

    private void analyzePackageCheck(PackageElement pe, String s, JavacTask ct, List<Path> sources) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            try (JavadocHelper javadocHelper = JavadocHelper.create(ct, sources)) {
                analyzeClassCheck(te, s, javadocHelper, ct.getTypes(), null); /*XXX: since tag from package-info (?!)*/
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
                .filter(element -> element.getKind().isClass())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassCheck(nestedClass, version, javadocHelper, types, currentVersion));
    }

    public String getElementName(TypeElement te, Element element, Types types) {
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
            prefix = "class";
            suffix = ":" + te.getQualifiedName();
        }
        return prefix + suffix;
    }

    private static class JavaSource extends SimpleJavaFileObject {
        private static final String TEXT = "";

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return TEXT;
        }
    }

    public record IntroducedIn(String introducedPreview, String introducedStable) {
    }
}