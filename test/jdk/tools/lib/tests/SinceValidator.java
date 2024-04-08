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
import javax.lang.model.type.TypeMirror;
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

public class SinceValidator {

    //these are methods that were preview in before the introduction of the @PreviewFeature
    private final Map<String, Set<String>> LEGACY_PREVIEW_METHODS = new HashMap<>();
    private final Map<String, IntroducedIn> classDictionary = new HashMap<>();
    private final JavaCompiler tool;
    private final List<String> wrongTagsList = new ArrayList<>();

    //should turn off compiler warning when running test on incubator module

    public static void main(String[] args) throws Exception {
        SinceValidator sinceCheckerTestHelper = new SinceValidator(args[0]);
        if (args.length == 0) {
            System.err.println("No module specified. Exiting...");
            System.exit(1);
        }
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
                    javacOptions, null,
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
        if (modules.contains(modules)) {
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
        persistElement(te, te, types, version);
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

    public void persistElement(TypeElement clazz, Element element, Types types, String version) {
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
        return LEGACY_PREVIEW_METHODS.containsKey(currentVersion)
                && LEGACY_PREVIEW_METHODS.get(currentVersion).contains(uniqueId);
    }

    private void testThisModule(String moduleName) throws Exception {
        List<Path> sources = new ArrayList<>();
//        Path home = Paths.get(System.getProperty("java.home"));
//        Path srcZip = home.resolve("lib").resolve("src.zip");
        Path srcZip = Path.of(pathToAPIKEY.pathToSRC); // local path to my src.zip file
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
                        processModuleCheck(elements.getModuleElement(moduleName), ct, sources, packagePath);
                        if (!wrongTagsList.isEmpty()) {
                            throw new Exception(wrongTagsList.toString());
                        }
                    }

                }
            }
        }
    }

    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, List<Path> sources, Path packagePath) {
        if (moduleElement == null) {
            throw new RuntimeException("Module element was null here");
        }
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                Version packageTopVersion = getPackageTopVersion(packagePath, ed);
                analyzePackageCheck(ed.getPackage(), ct, sources, packageTopVersion);
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


    private void analyzePackageCheck(PackageElement pe, JavacTask ct, List<Path> sources, Version packageTopVersion) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            try (JavadocHelper javadocHelper = JavadocHelper.create(ct, sources)) {
                analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), null, ct.getElements());
            } catch (Exception e) {
                wrongTagsList.add("Initiating javadocHelperFailed" + e.getMessage());
            }
        }
    }

    private void analyzeClassCheck(TypeElement te, String version, JavadocHelper javadocHelper,
                                   Types types, Version enclosingVersion, Elements elementUtils) {
        Set<Modifier> classModifiers = te.getModifiers();
        if (!(classModifiers.contains(Modifier.PUBLIC) || classModifiers.contains(Modifier.PROTECTED))) {
            return;
        }

        Version currentVersion = checkElement(te, te, types, javadocHelper, version, enclosingVersion, elementUtils);
        te.getEnclosedElements().stream().filter(element -> element.getModifiers().contains(Modifier.PUBLIC)
                        || element.getModifiers().contains(Modifier.PROTECTED))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> checkElement(te, element, types, javadocHelper, version, currentVersion, elementUtils));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassCheck(nestedClass, version, javadocHelper, types, currentVersion, elementUtils));
    }

    private Version checkElement(TypeElement clazz, Element element, Types types,
                                 JavadocHelper javadocHelper, String currentVersion, Version enclosingVersion, Elements elementUtils) {
        String uniqueId = getElementName(clazz, element, types);

        String comment = null;
        try {
            comment = javadocHelper.getResolvedDocComment(element);
        } catch (IOException e) {
            wrongTagsList.add("JavadocHelper failed for " + element + "\n");
        }
// Needs to be split it up once it's at a good working version

        Boolean foundOverridingMethod = false;
        Element overridenMethod = null;
        String overridenMethodID = null;
        Element methodSuperClass = null;
        if (element instanceof ExecutableElement) {
//            found this to not work as @Override is annotated with SOURCE
//            and it is discarded by the compiler
//            Boolean overrides = element instanceof ExecutableElement && ((ExecutableElement) element).getAnnotation(Override.class) != null;

//            if (uniqueId.equals("method:java.security.interfaces.RSAPublicKey:getParams:()")) {
            var superclasses = types.directSupertypes(clazz.asType());
            if (superclasses != null) {
                for (int i = superclasses.size() - 1; i >= 0; i--) {
                    var superclass = superclasses.get(i);
                    if (!superclass.toString().equals("java.lang.Object") && !foundOverridingMethod) {
                        List<? extends Element> superclassmethods = elementUtils.getAllMembers((TypeElement) types.asElement(superclass));
                        for (Element method : superclassmethods) {
                            if (method.getSimpleName().contentEquals(element.getSimpleName()) && method.asType().toString().equals(element.asType().toString())) {
                                overridenMethod = method;
                                foundOverridingMethod = true;
                                methodSuperClass = types.asElement(superclass);
                                overridenMethodID = getElementName((TypeElement) types.asElement(superclass), overridenMethod, types);
                            }
                        }
                    }
                }
            }
        }
//        }


        IntroducedIn mappedVersion = classDictionary.get(uniqueId);
        String realMappedVersion = null;
        try {
            realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                    mappedVersion.introducedPreview :
                    mappedVersion.introducedStable;
        } catch (Exception e) {
            wrongTagsList.add("For element " + element + "mappedVersion" + mappedVersion + " is null" + e + "\n");
        }
        Version sinceVersion = comment != null ? extractSinceVersionFromText(comment) : null;
        if (sinceVersion == null ||
                (enclosingVersion != null && enclosingVersion.compareTo(sinceVersion) > 0)) {
            sinceVersion = enclosingVersion;
        }
        if (!foundOverridingMethod) {
            checkEquals(sinceVersion, realMappedVersion, uniqueId);


//        } else {
//            String versionOverridenMethod = null;
//            String versionOverridenClass = null;
//            try {
//                versionOverridenMethod = String.valueOf(extractSinceVersionFromText(javadocHelper.getResolvedDocComment(overridenMethod)));
//                versionOverridenClass = String.valueOf(extractSinceVersionFromText(javadocHelper.getResolvedDocComment(methodSuperClass)));
//                if (versionOverridenMethod == null && versionOverridenClass != null) {
//                    versionOverridenMethod = versionOverridenClass;
//                }
//            } catch (IOException e) {
////                wrongTagsList.add("JavadocHelper failed for " + overridenMethod + "\n");
//            }
////            checkEqualsOverrides(enclosingVersion.toString(), sinceVersion.toString(),
////                    realMappedVersion, uniqueId, overridenMethodID, overridenMethod,
////                    versionOverridenMethod);
//
//
//            if (!sinceVersion.equals(enclosingVersion)) {
//
//            }
//
//            checkEquals(Version.parse(versionOverridenMethod), realMappedVersion,
//                    uniqueId);


        }
        return sinceVersion;
    }

    private void checkEqualsOverrides(String enclosingVersion, String sinceVersion, String realMappedVersion, String uniqueId, String overriddenMethodId, Element overriddenMethod, String overriddenMethodSinceVersion) {

        if (overriddenMethod != null && overriddenMethodId != null) {
            if (overriddenMethodSinceVersion != null && sinceVersion != null) {
                if (realMappedVersion.equals(overriddenMethodSinceVersion) && Integer.parseInt(enclosingVersion) <= Integer.parseInt(realMappedVersion)) { // mapping matches that of the supertype
                    // comparison with the supertype is good
                    if (enclosingVersion.equals(sinceVersion)) {
                        wrongTagsList.add("@since should be removed for this method" + uniqueId + "\n");
                    }
                } else if (Integer.parseInt(sinceVersion) > Integer.parseInt(realMappedVersion)) {

                }


                if (sinceVersion.compareTo(overriddenMethodSinceVersion) > 0) {
                    sinceVersion = overriddenMethodSinceVersion;
                }
            }
        } else {
            wrongTagsList.add("Checking @since failed for " + uniqueId);
        }
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
        } else if (sinceVersion.toString().equals("9")) {
            message = "For Element: " + elementSimpleName +
                    " Wrong @since version is 9 or older instead of " + mappedVersion + "\n";
        } else {
            message = "For Element: " + elementSimpleName +
                    " Wrong @since version " + sinceVersion + " instead of " + mappedVersion + "\n";
        }
        return message;
    }

    private String getElementName(TypeElement te, Element element, Types types) {
        String prefix = "";
        String suffix = "";
        ElementKind kind = element.getKind();
        if (kind.isField()) {
            prefix = "field";
            suffix = ":" + te.getQualifiedName() + ":" + element.getSimpleName();
        } else if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            prefix = "method";
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
            suffix = ":" + te.getQualifiedName();
        }
        return prefix + suffix;
    }

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }

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
}