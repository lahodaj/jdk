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
    // TODO add a bit more to include  java.compiler and jdk.compiler
    private final Set<String> LEGACY_PREVIEW_METHODS = Set.of(
            //13
            "method:java.lang.String:stripIndent:()",
            "method:java.lang.String:translateEscapes:()",
            "method:java.lang.String:formatted:(java.lang.Object[])",
            "method:com.sun.source.util.SimpleTreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "method:com.sun.source.util.TreeScanner:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "method:com.sun.source.tree.TreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
            "field:com.sun.source.tree.Tree.Kind:YIELD"
//            , "interface:com.sun.source.tree.YieldTree"

            //12

    );

//    JDK 9
//./jdk/src/java.base/share/classes/jdk/internal/reflect/Reflection.java:    @Deprecated(forRemoval=true)
//./jdk/src/jdk.unsupported/share/classes/sun/reflect/Reflection.java:    @Deprecated(forRemoval=true)

//    JDK 10 and 11 = nothing found
// JDK 12

//./src/jdk.compiler/share/classes/com/sun/source/tree/Tree.java:        @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/CaseTree.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/CaseTree.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/CaseTree.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/CaseTree.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/SwitchExpressionTree.java:@Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/TreeVisitor.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/tree/BreakTree.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/util/SimpleTreeVisitor.java:    @Deprecated(forRemoval=true, since="12")
//./src/jdk.compiler/share/classes/com/sun/source/util/TreeScanner.java:    @Deprecated(forRemoval=true, since="12")


    //JDK 14
//    ./src/java.compiler/share/classes/javax/lang/model/util/AbstractElementVisitor14.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/SimpleElementVisitor14.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/ElementKindVisitor14.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/ElementKindVisitor6.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/Elements.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/ElementFilter.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/ElementFilter.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/util/ElementScanner14.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/element/ElementVisitor.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/element/TypeElement.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/element/ElementKind.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/element/ElementKind.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.compiler/share/classes/javax/lang/model/element/ElementKind.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.PATTERN_MATCHING_IN_INSTANCEOF,
//            ./src/java.compiler/share/classes/javax/lang/model/element/RecordComponentElement.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/jdk.compiler/share/classes/com/sun/source/tree/Tree.java:        @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/sun/reflect/annotation/TypeAnnotation.java:        @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/reflect/RecordComponent.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/runtime/ObjectMethods.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/annotation/ElementType.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/Class.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/Class.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/java.base/share/classes/java/lang/String.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS,
//            ./src/java.base/share/classes/java/lang/String.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS,
//            ./src/java.base/share/classes/java/lang/String.java:    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS,
//            ./src/java.base/share/classes/java/lang/Record.java:@jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
//            ./src/jdk.jshell/share/classes/jdk/jshell/Snippet.java:        @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS)
//            ./test/langtools/tools/javac/preview/PreviewErrors.java:                                @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.${preview}
//            ./test/langtools/tools/javac/preview/PreviewErrors.java:                                @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.${preview}


//    nizarbenalla@nizarbenalla-mac jdk15-master % grep -r '@Deprecated(forRemoval=true, since="15")' ./src/*
//./src/java.rmi/share/classes/java/rmi/activation/ActivationGroup.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivateFailedException.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationException.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationDesc.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/UnknownObjectException.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationGroupDesc.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationInstantiator.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationSystem.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationGroup_Stub.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationID.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/UnknownGroupException.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/Activator.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationMonitor.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/Activatable.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/java/rmi/activation/ActivationGroupID.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/com/sun/rmi/rmid/ExecOptionPermission.java:@Deprecated(forRemoval=true, since="15")
//./src/java.rmi/share/classes/com/sun/rmi/rmid/ExecPermission.java:@Deprecated(forRemoval=true, since="15")
    private static final String JDK13 = "13";
    private static final String JDK14 = "14";
    private final  Map<String, IntroducedIn> classDictionary = new HashMap<>();
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

    private void persistElement(TypeElement clazz, Element element, Types types, String version) {
        String uniqueId = getElementName(clazz, element, types);
        classDictionary.computeIfAbsent(uniqueId,
                _ -> new IntroducedIn(null, null));

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

    private void testThisModule(String moduleName) throws Exception {
        List<Path> sources = new ArrayList<>();

//        Path home = Paths.get(System.getProperty("java.home"));
//        Path srcZip = home.resolve("lib").resolve("src.zip");
        Path srcZip = Path.of(pathToAPIKEY.pathToSRC);
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
                                Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
                        ct.analyze();
                        processModuleCheck(ct.getElements().getModuleElement(moduleName), ct, sources);
                        if (!wrongTagsList.isEmpty()) {
                            throw new Exception(wrongTagsList.toString());
                        }
                    }

                }
            }
        }
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
        String realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                mappedVersion.introducedPreview() :
                mappedVersion.introducedStable();
        checkEquals(sinceVersion, realMappedVersion, uniqueId);
        return sinceVersion;
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


    private static Version extractSinceVersion(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            String versionString = matcher.group(1);
            try {
                if (versionString.equals("1.0")) {
                    //XXX
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
        if (Version.parse("9").compareTo(sinceVersion) > 0) {
            sinceVersion = Version.parse("9");
        }
        if (!sinceVersion.equals(Version.parse(mappedVersion))) {
            wrongTagsList.add("For  Element: " + elementSimpleName
                    + " Wrong since version " + sinceVersion + " instead of " + mappedVersion + "\n");
        }
    }


    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, List<Path> sources) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackageCheck(ed.getPackage(), ct, sources);
            }
        }
    }

    private void analyzePackageCheck(PackageElement pe, JavacTask ct, List<Path> sources) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            try (JavadocHelper javadocHelper = JavadocHelper.create(ct, sources)) {
                analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), null); /*XXX: since tag from package-info (?!)*/
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
        }
        // should I be using getDeclared type? and split .isClass and isInterface here? for discussion
        else if (element.getKind().isDeclaredType()) {
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

    private record IntroducedIn(String introducedPreview, String introducedStable) {
    }
}