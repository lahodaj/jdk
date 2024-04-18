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

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Pair;
//import jtreg.SkippedException;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static javax.lang.model.element.ElementKind.MODULE;
import static javax.lang.model.element.ElementKind.PACKAGE;
import javax.tools.JavaFileManager.Location;

public class SinceValidator {
    private final Map<String, Set<String>> LEGACY_PREVIEW_METHODS = new HashMap<>();
    private final Map<String, IntroducedIn> classDictionary = new HashMap<>();
    private final JavaCompiler tool;
    private final List<String> wrongTagsList = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("No module specified. Exiting...");
            System.exit(1);
        }
        SinceValidator sinceCheckerTestHelper = new SinceValidator(args[0]);
        sinceCheckerTestHelper.testThisModule(args[0]);
    }

    private SinceValidator(String moduleName) throws IOException {
        tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= Runtime.version().feature(); i++) {
            //NOTE
            //Certain modules are only resolved in jdk 11 or newer, even tho they existed before
            //--add-module is necessary
            //JDK-8205169
            List<String> javacOptions = getJavacOptions(moduleName, i);
            JavacTask ct = (JavacTask) tool.getTask(null, null, null,
                    javacOptions,
                    null,
                    Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
            ct.analyze();

            String version = String.valueOf(i);
            Elements elements = ct.getElements();
            elements.getModuleElement("java.base"); // forces module graph to be instantiated
            elements.getAllModuleElements().forEach(me ->
                    processModuleRecord(me, version, ct));
        }
    }

    private static List<String> getJavacOptions(String moduleName, int i) {
        if (i > 10) {
            return List.of("--release", String.valueOf(i));
        }
        // these do not appear as part of the root modules until JDK 11
        Set<String> modules = Set.of(
                "jdk.jcmd", "jdk.jdeps", "jdk.jfr", "jdk.jlink",
                "java.smartcardio", "jdk.localedata", "jdk.management.jfr", "jdk.naming.dns",
                "jdk.naming.rmi", "jdk.charsets", "jdk.crypto.cryptoki", "jdk.crypto.ec",
                "jdk.editpad", "jdk.hotspot.agent", "jdk.zipfs"
        );
        if (modules.contains(moduleName)) {
            return List.of("--add-modules", moduleName, "--release", String.valueOf(i));
        }
        return List.of("--release", String.valueOf(i));

    }

    private void processModuleRecord(ModuleElement moduleElement, String releaseVersion, JavacTask ct) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            classDictionary.computeIfAbsent(moduleElement.getQualifiedName().toString(), _ -> {
                IntroducedIn introducedIn = new IntroducedIn();
                introducedIn.introducedStable = releaseVersion;
                return introducedIn;
            });
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
        Set<Modifier> classModifiers = te.getModifiers();
        if (!(classModifiers.contains(Modifier.PUBLIC) || classModifiers.contains(Modifier.PROTECTED))) {
            return;
        }
        persistElement(te.getEnclosingElement(), te, types, version);
        elements.getAllMembers(te).stream()
                .filter(element -> element.getModifiers().contains(Modifier.PUBLIC) || element.getModifiers().contains(Modifier.PROTECTED))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> persistElement(te, element, types, version));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassRecord(nestedClass, version, types, elements));
    }

    public void persistElement(Element explicitOwner, Element element, Types types, String version) {
        String uniqueId = getElementName(explicitOwner, element, types);
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

        return LEGACY_PREVIEW_METHODS.containsKey(currentVersion)
                && LEGACY_PREVIEW_METHODS.get(currentVersion).contains(uniqueId);

    }

    private void testThisModule(String moduleName) throws Exception {
        List<Path> sources = new ArrayList<>();
        Path home = Paths.get(System.getProperty("java.home"));
        Path srcZip = home.resolve("lib").resolve("src.zip");
        if (Files.notExists(srcZip)) {
            //possibly running over an exploded JDK build, attempt to find a
            //co-located full JDK image with src.zip:
            Path testJdk = Paths.get(System.getProperty("test.jdk"));
            srcZip = testJdk.getParent().resolve("images").resolve("jdk").resolve("lib").resolve("src.zip");
        }
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
                                List.of("--add-modules", moduleName, "-d", "."),
                                null,
                                Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
                        ct.analyze();
                        Elements elements = ct.getElements();
                        elements.getModuleElement("java.base");
                        try (EffectiveSourceSinceHelper javadocHelper = EffectiveSourceSinceHelper.create(ct, List.of(root), this)) {
                            processModuleCheck(elements.getModuleElement(moduleName), ct, packagePath, javadocHelper);
                        } catch (Exception e) {
                            e.printStackTrace();
                            wrongTagsList.add("Initiating javadocHelperFailed" + e.getMessage());
                        }
                        if (!wrongTagsList.isEmpty()) {
                            throw new Exception(wrongTagsList.toString());
                        }
                    }

                }
            }
        }
    }

    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, Path packagePath, EffectiveSourceSinceHelper javadocHelper) {
        if (moduleElement == null) {
            throw new RuntimeException("Module element was null here");
        }
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackageCheck(ed.getPackage(), ct, javadocHelper);
            } else {
                checkModuleVersion(moduleElement, packagePath);
            }
        }
    }

    private void checkModuleVersion(ModuleElement moduleElement, Path packagePath) {
        Path moduleInfoFile = packagePath.resolve("module-info.java");
        if (Files.exists(moduleInfoFile)) {
            try {
                byte[] ModuleInfoAsBytes = Files.readAllBytes(moduleInfoFile);
                String ModuleInfoContent = new String(ModuleInfoAsBytes, StandardCharsets.UTF_8);
                String moduleVersion = extractSinceVersionFromText(ModuleInfoContent).toString();
                String moduleName = moduleElement.getQualifiedName().toString();
                String introducedVersion = classDictionary.get(moduleName).introducedStable;
                if (!moduleVersion.equals(introducedVersion)) {
                    wrongTagsList.add("For  Module: " + moduleName
                            + " Wrong since version " + moduleVersion + " instead of " + introducedVersion + "\n");
                }
            } catch (IOException e) {
                wrongTagsList.add("module-info.java not found or couldn't be opened AND this module has no unqualified exports\n");
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

        Version packageTopVersion = null;
        if (Files.exists(pkgInfo)) {
            try {
                byte[] packageAsBytes = Files.readAllBytes(pkgInfo);
                String packageContent = new String(packageAsBytes, StandardCharsets.UTF_8);
                packageTopVersion = extractSinceVersionFromText(packageContent);
            } catch (IOException e) {
                wrongTagsList.add("package-info.java not found or couldn't be opened\n");
            }
        }
        return packageTopVersion;
    }


    private void analyzePackageCheck(PackageElement pe, JavacTask ct, EffectiveSourceSinceHelper javadocHelper) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), ct.getElements());
        }
    }

    private void analyzeClassCheck(TypeElement te, String version, EffectiveSourceSinceHelper javadocHelper,
                                   Types types, Elements elementUtils) {
        Set<Modifier> classModifiers = te.getModifiers();
        if (!(classModifiers.contains(Modifier.PUBLIC) || classModifiers.contains(Modifier.PROTECTED))) {
            return;
        }
        checkElement(te.getEnclosingElement(), te, types, javadocHelper, version, elementUtils);
        te.getEnclosedElements().stream().filter(element -> element.getModifiers().contains(Modifier.PUBLIC)
                        || element.getModifiers().contains(Modifier.PROTECTED))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> checkElement(te, element, types, javadocHelper, version, elementUtils));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassCheck(nestedClass, version, javadocHelper, types, elementUtils));
    }


    private void checkElement(Element explicitOwner, Element element, Types types,
                                 EffectiveSourceSinceHelper javadocHelper, String currentVersion, Elements elementUtils) {
        String uniqueId = getElementName(explicitOwner, element, types);

        if (element.getKind() == ElementKind.METHOD &&
            element.getEnclosingElement().getKind() == ElementKind.ENUM &&
            (uniqueId.endsWith(":values:()") || uniqueId.endsWith(":valueOf:(java.lang.String)"))) {
            //mandated enum type methods
            return ;
        }

        Version sinceVersion = javadocHelper.effectiveSinceVersion(explicitOwner, element, types, elementUtils);
        IntroducedIn mappedVersion = classDictionary.get(uniqueId);
        String realMappedVersion = null;
        try {
            realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                    mappedVersion.introducedPreview :
                    mappedVersion.introducedStable;
        } catch (Exception e) {
            wrongTagsList.add("For element " + element + "mappedVersion" + mappedVersion + " is null" + e + "\n");
        }
        checkEquals(sinceVersion, realMappedVersion, uniqueId);
    }

    private Version extractSinceVersionFromText(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            String versionString = matcher.group(1);
            try {
                if (versionString.equals("1.0")) {
                    versionString = "1"; //ended up being necessary
                } else if (versionString.startsWith("1.")) {
                    versionString = versionString.substring(2);
                }
                return Version.parse(versionString);
            } catch (NumberFormatException ex) {
                wrongTagsList.add("@since value that cannot be parsed: " + versionString + "\n");
                return null;
            }
        } else {
            return null;
        }
    }


    private void checkEquals(Version sinceVersion, String mappedVersion, String elementSimpleName) {
        if (sinceVersion == null || mappedVersion == null) {
            System.err.println("For " + elementSimpleName + " mapped version is="
                    + mappedVersion + " while the @since in the source code is= " + sinceVersion);
            return;
        }
        if (Version.parse("9").compareTo(sinceVersion) > 0) {
            sinceVersion = Version.parse("9");
        }
        if (!sinceVersion.equals(Version.parse(mappedVersion))) {
            String message = getWrongSinceMessage(sinceVersion, mappedVersion, elementSimpleName);
            wrongTagsList.add(message);
        }

    }

    private static String getWrongSinceMessage(Version sinceVersion, String mappedVersion, String elementSimpleName) {
        String message;
        if (mappedVersion.toString().equals("9")) {
            message = "For Element: " + elementSimpleName +
                    " Wrong @since version " + sinceVersion + " But the element exists before JDK 10\n";
        } else{
            message = "For Element: " + elementSimpleName +
                    " Wrong @since version is " + sinceVersion + " instead of " + mappedVersion + "\n";
        }
        return message;
    }

    private static String getElementName(Element owner, Element element, Types types) {
        String prefix = "";
        String suffix = "";
        ElementKind kind = element.getKind();
        if (kind.isField()) {
            TypeElement te = (TypeElement) owner;

            prefix = "field";
            suffix = ":" + te.getQualifiedName() + ":" + element.getSimpleName();
        } else if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            prefix = "method";
            TypeElement te = (TypeElement) owner;
            ExecutableElement executableElement = (ExecutableElement) element;
            String methodName = executableElement.getSimpleName().toString();
            String descriptor = executableElement.getParameters().stream()
                    .map(p -> types.erasure(p.asType()).toString())
                    .collect(Collectors.joining(",", "(", ")"));
            suffix = ":" + te.getQualifiedName() + ":" + methodName + ":" + descriptor;
        } else if (kind.isDeclaredType()) {
            if (kind.isClass()) {
                prefix = "class";
            } else if (kind.isInterface()) {
                prefix = "interface";
            }
            suffix = ":" + ((TypeElement) element).getQualifiedName();
        } else if (kind == ElementKind.PACKAGE) {
            prefix = "package";
            suffix = ":" + ((PackageElement) element).getQualifiedName();
        } else if (kind == ElementKind.MODULE) {
            prefix = "module";
            suffix = ":" + ((ModuleElement) element).getQualifiedName();
        }
        return prefix + suffix;
    }

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }
//these were preview in before the introduction of the @PreviewFeature
    {
        LEGACY_PREVIEW_METHODS.put("12", Set.of(
                "method:com.sun.source.tree.BreakTree:getValue:()",
                "method:com.sun.source.tree.CaseTree:getExpressions:()",
                "method:com.sun.source.tree.CaseTree:getBody:()",
                "method:com.sun.source.tree.CaseTree:getCaseKind:()",
                "class:com.sun.source.tree.CaseTree.CaseKind",
                "field:com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface:com.sun.source.tree.SwitchExpressionTree",
                "method:com.sun.source.tree.TreeVisitor:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method:com.sun.source.util.TreeScanner:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method:com.sun.source.util.SimpleTreeVisitor:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("13", Set.of(
                "method:com.sun.source.tree.CaseTree:getExpressions:()",
                "method:com.sun.source.tree.CaseTree:getBody:()",
                "method:com.sun.source.tree.CaseTree:getCaseKind:()",
                "class:com.sun.source.tree.CaseTree.CaseKind",
                "field:com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface:com.sun.source.tree.SwitchExpressionTree",
                "method:com.sun.source.tree.TreeVisitor:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method:com.sun.source.util.TreeScanner:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method:com.sun.source.util.SimpleTreeVisitor:visitSwitchExpression:(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method:java.lang.String:stripIndent:()",
                "method:java.lang.String:translateEscapes:()",
                "method:java.lang.String:formatted:(java.lang.Object[])",
                "class:javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "field:com.sun.source.tree.Tree.Kind:YIELD",
                "interface:com.sun.source.tree.YieldTree",
                "method:com.sun.source.tree.TreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method:com.sun.source.util.SimpleTreeVisitor:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method:com.sun.source.util.TreeScanner:visitYield:(com.sun.source.tree.YieldTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("14", Set.of(
                "class:javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "method:java.lang.String:stripIndent:()",
                "method:java.lang.String:translateEscapes:()",
                "method:java.lang.String:formatted:(java.lang.Object[])",
                "field:jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class:javax.lang.model.element.RecordComponentElement",
                "method:javax.lang.model.element.ElementVisitor:visitRecordComponent:(javax.lang.model.element.RecordComponentElement,P)",
                "class:javax.lang.model.util.ElementScanner14",
                "class:javax.lang.model.util.AbstractElementVisitor14",
                "class:javax.lang.model.util.SimpleElementVisitor14",
                "method:javax.lang.model.util.ElementKindVisitor6:visitTypeAsRecord:(javax.lang.model.element.TypeElement,java.lang.Object)",
                "class:javax.lang.model.util.ElementKindVisitor14",
                "method:javax.lang.model.util.Elements:recordComponentFor:(javax.lang.model.element.ExecutableElement)",
                "method:javax.lang.model.util.ElementFilter:recordComponentsIn:(java.lang.Iterable)",
                "method:javax.lang.model.util.ElementFilter:recordComponentsIn:(java.util.Set)",
                "method:javax.lang.model.element.TypeElement:getRecordComponents:()",
                "field:javax.lang.model.element.ElementKind:RECORD",
                "field:javax.lang.model.element.ElementKind:RECORD_COMPONENT",
                "field:javax.lang.model.element.ElementKind:BINDING_VARIABLE",
                "field:com.sun.source.tree.Tree.Kind:RECORD",
                "field:sun.reflect.annotation.TypeAnnotation.TypeAnnotationTarget:RECORD_COMPONENT",
                "class:java.lang.reflect.RecordComponent",
                "class:java.lang.runtime.ObjectMethods",
                "field:java.lang.annotation.ElementType:RECORD_COMPONENT",
                "method:java.lang.Class:isRecord:()",
                "method:java.lang.Class:getRecordComponents:()",
                "class:java.lang.Record"
        ));

        LEGACY_PREVIEW_METHODS.put("15", Set.of(
                "field:jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class:javax.lang.model.element.RecordComponentElement",
                "method:javax.lang.model.element.ElementVisitor:visitRecordComponent:(javax.lang.model.element.RecordComponentElement,P)",
                "class:javax.lang.model.util.ElementScanner14",
                "class:javax.lang.model.util.AbstractElementVisitor14",
                "class:javax.lang.model.util.SimpleElementVisitor14",
                "method:javax.lang.model.util.ElementKindVisitor6:visitTypeAsRecord:(javax.lang.model.element.TypeElement,java.lang.Object)",
                "class:javax.lang.model.util.ElementKindVisitor14",
                "method:javax.lang.model.util.Elements:recordComponentFor:(javax.lang.model.element.ExecutableElement)",
                "method:javax.lang.model.util.ElementFilter:recordComponentsIn:(java.lang.Iterable)",
                "method:javax.lang.model.util.ElementFilter:recordComponentsIn:(java.util.Set)",
                "method:javax.lang.model.element.TypeElement:getRecordComponents:()",
                "field:javax.lang.model.element.ElementKind:RECORD",
                "field:javax.lang.model.element.ElementKind:RECORD_COMPONENT",
                "field:javax.lang.model.element.ElementKind:BINDING_VARIABLE",
                "field:com.sun.source.tree.Tree.Kind:RECORD",
                "field:sun.reflect.annotation.TypeAnnotation.TypeAnnotationTarget:RECORD_COMPONENT",
                "class:java.lang.reflect.RecordComponent",
                "class:java.lang.runtime.ObjectMethods",
                "field:java.lang.annotation.ElementType:RECORD_COMPONENT",
                "class:java.lang.Record",
                "method:java.lang.Class:isRecord:()",
                "method:java.lang.Class:getRecordComponents:()",
                "field:javax.lang.model.element.Modifier:SEALED",
                "field:javax.lang.model.element.Modifier:NON_SEALED",
                "method:javax.lang.model.element.TypeElement:getPermittedSubclasses:()",
                "method:com.sun.source.tree.ClassTree:getPermitsClause:()",
                "method:java.lang.Class:isSealed:()",
                "method:java.lang.Class:permittedSubclasses:()"
        ));

        LEGACY_PREVIEW_METHODS.put("16", Set.of(
                "field:jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "field:javax.lang.model.element.Modifier:SEALED",
                "field:javax.lang.model.element.Modifier:NON_SEALED",
                "method:javax.lang.model.element.TypeElement:getPermittedSubclasses:()",
                "method:com.sun.source.tree.ClassTree:getPermitsClause:()",
                "method:java.lang.Class:isSealed:()",
                "method:java.lang.Class:getPermittedSubclasses:()"
        ));
    }

    private final class EffectiveSourceSinceHelper implements AutoCloseable {
        private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        private final JavaFileManager baseFileManager;
        private final StandardJavaFileManager fm;
        private final Set<String> seenLookupElements = new HashSet<>();
        private final Map<String, Version> signature2Source = new HashMap<>();

        public static EffectiveSourceSinceHelper create(JavacTask mainTask, Collection<? extends Path> sourceLocations, SinceValidator validator) {
            StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
            try {
                fm.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, sourceLocations);
                return validator.new EffectiveSourceSinceHelper(mainTask, fm);
            } catch (IOException ex) {
                try {
                    fm.close();
                } catch (IOException closeEx) {
                }
                throw new UncheckedIOException(ex);
            }
        }

        private EffectiveSourceSinceHelper(JavacTask mainTask, StandardJavaFileManager fm) {
            this.baseFileManager = ((JavacTaskImpl) mainTask).getContext().get(JavaFileManager.class);
            this.fm = fm;
        }

        public Version effectiveSinceVersion(Element owner, Element element, Types typeUtils, Elements elementUtils) {
            String handle = getElementName(owner, element, typeUtils);
            Version since = signature2Source.get(handle);

            if (since == null) {
                try {
                    Element lookupElement = switch (element.getKind()) {
                        case MODULE -> element;
                        case PACKAGE -> element;
                        default -> elementUtils.getOutermostTypeElement(element);
                    };

                    if (lookupElement == null)
                        return null;

                    String lookupHandle = getElementName(owner, element, typeUtils);

                    if (!seenLookupElements.add(lookupHandle)) {
                        //we've already processed this top-level, don't try to compute
                        //the values again:
                        return null;
                    }

                    Pair<JavacTask, CompilationUnitTree> source = findSource(lookupElement, elementUtils);

                    if (source == null)
                        return null;

                    fillElementCache(source.fst, source.snd, source.fst.getTypes(), source.fst.getElements());

                    since = signature2Source.get(handle);
                } catch (IOException ex) {
                    wrongTagsList.add("JavadocHelper failed for " + element + "\n");
                }
            }

            return since;
        }
        //where:
            private void fillElementCache(JavacTask task, CompilationUnitTree cut, Types typeUtils, Elements elementUtils) {
                Trees trees = Trees.instance(task);

                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree node, Void p) {
                        handleDeclaration();
                        return null;
                    }

                    @Override
                    public Void visitClass(ClassTree node, Void p) {
                        handleDeclaration();
                        return super.visitClass(node, p);
                    }

                    @Override
                    public Void visitVariable(VariableTree node, Void p) {
                        handleDeclaration();
                        return null;
                    }

                    @Override
                    public Void visitModule(ModuleTree node, Void p) {
                        handleDeclaration();
                        return null;
                    }

                    @Override
                    public Void visitBlock(BlockTree node, Void p) {
                        return null;
                    }

                    @Override
                    public Void visitPackage(PackageTree node, Void p) {
                        if (cut.getSourceFile().isNameCompatible("package-info", JavaFileObject.Kind.SOURCE)) {
                            handleDeclaration();
                        }
                        return super.visitPackage(node, p);
                    }

                    private void handleDeclaration() {
                        Element currentElement = trees.getElement(getCurrentPath());

                        if (currentElement != null) {
                            signature2Source.put(getElementName(currentElement.getEnclosingElement(), currentElement, typeUtils), computeSinceVersion(currentElement, typeUtils, elementUtils));
                        }
                    }
                }.scan(cut, null);
            }

            private Version computeSinceVersion(Element element, Types types,
                                                Elements elementUtils) {
                String docComment = elementUtils.getDocComment(element);
                Version version = null;
                if (docComment != null) {
                    version = extractSinceVersionFromText(docComment);
                }

                if (version != null) {
                    return version; //explicit @since has an absolute priority
                }

                Version overriddenVersion = null;
                if (element instanceof ExecutableElement && docComment == null) {
                    TypeElement clazz = (TypeElement) element.getEnclosingElement();
                    var superclasses = types.directSupertypes(clazz.asType());
                    if (superclasses != null) {
                        for (int i = superclasses.size() - 1; i >= 0; i--) {
                            var superclass = superclasses.get(i);
                            TypeElement superType = (TypeElement) types.asElement(superclass);
                            List<? extends Element> superclassmethods = elementUtils.getAllMembers(superType);
                            for (ExecutableElement method : ElementFilter.methodsIn(superclassmethods)) {
                                if (elementUtils.overrides((ExecutableElement) element, method, clazz)) {
                                    Version currentMethodVersion = effectiveSinceVersion(method.getEnclosingElement(), method, types, elementUtils);
                                    //currentMethodVersion may be null for private/non-public elements
                                    if (overriddenVersion == null || (currentMethodVersion != null && currentMethodVersion.compareTo(overriddenVersion) < 0)) {
                                        overriddenVersion = currentMethodVersion;
                                    }
                                }
                            }
                        }
                    }
                }

                if (element.getKind() != ElementKind.MODULE) {
                    version = effectiveSinceVersion(element.getEnclosingElement().getEnclosingElement(), element.getEnclosingElement(), types, elementUtils);
                }
                if (version == null) {
                    //may be null for private elements
                    //TODO: can we be more careful here?
                }
                if (overriddenVersion == null || (version != null && version.compareTo(overriddenVersion) > 0)) {
                    return version;
                } else {
                    return overriddenVersion;
                }
            }

            private Pair<JavacTask, CompilationUnitTree> findSource(Element forElement, Elements elementUtils) throws IOException {
                String moduleName = elementUtils.getModuleOf(forElement).getQualifiedName().toString();
                String binaryName = switch (forElement.getKind()) {
                    case MODULE -> "module-info";
                    case PACKAGE -> ((QualifiedNameable) forElement).getQualifiedName() + ".package-info";
                    default -> elementUtils.getBinaryName((TypeElement) forElement).toString();
                };
                Location packageLocationForModule = fm.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, moduleName);
                JavaFileObject jfo = fm.getJavaFileForInput(packageLocationForModule,
                                                            binaryName,
                                                            JavaFileObject.Kind.SOURCE);

                if (jfo == null)
                    return null;

                List<JavaFileObject> jfos = Arrays.asList(jfo);
                JavaFileManager patchFM = moduleName != null
                        ? new PatchModuleFileManager(baseFileManager, jfo, moduleName)
                        : baseFileManager;
                JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, patchFM, d -> {}, null, null, jfos);
                Iterable<? extends CompilationUnitTree> cuts = task.parse();

                task.enter();

                return Pair.of(task, cuts.iterator().next());
            }

            @Override
            public void close() throws IOException {
                fm.close();
            }

            private static final class PatchModuleFileManager
                    extends ForwardingJavaFileManager<JavaFileManager> {

                private final JavaFileObject file;
                private final String moduleName;

                public PatchModuleFileManager(JavaFileManager fileManager,
                                              JavaFileObject file,
                                              String moduleName) {
                    super(fileManager);
                    this.file = file;
                    this.moduleName = moduleName;
                }

                @Override
                public Location getLocationForModule(Location location,
                                                     JavaFileObject fo) throws IOException {
                    return fo == file
                            ? PATCH_LOCATION
                            : super.getLocationForModule(location, fo);
                }

                @Override
                public String inferModuleName(Location location) throws IOException {
                    return location == PATCH_LOCATION
                            ? moduleName
                            : super.inferModuleName(location);
                }

                @Override
                public boolean hasLocation(Location location) {
                    return location == StandardLocation.PATCH_MODULE_PATH ||
                           super.hasLocation(location);
                }

                private static final Location PATCH_LOCATION = new Location() {
                    @Override
                    public String getName() {
                        return "PATCH_LOCATION";
                    }

                    @Override
                    public boolean isOutputLocation() {
                        return false;
                    }

                    @Override
                    public boolean isModuleOrientedLocation() {
                        return false;
                    }

                };
            }
        }
}

