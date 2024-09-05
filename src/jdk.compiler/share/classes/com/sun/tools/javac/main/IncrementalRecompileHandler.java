/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sun.tools.javac.main;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import static com.sun.source.tree.Tree.Kind.ANNOTATION_TYPE;
import static com.sun.source.tree.Tree.Kind.BLOCK;
import static com.sun.source.tree.Tree.Kind.CLASS;
import static com.sun.source.tree.Tree.Kind.ENUM;
import static com.sun.source.tree.Tree.Kind.INTERFACE;
import static com.sun.source.tree.Tree.Kind.METHOD;
import static com.sun.source.tree.Tree.Kind.RECORD;
import static com.sun.source.tree.Tree.Kind.VARIABLE;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Directive;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.main.JavaCompiler.InitialFileParser;
import static com.sun.tools.javac.main.Option.MODULE;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public class IncrementalRecompileHandler extends InitialFileParser {

    private final JavaCompiler compiler;
    private final JavaFileManager fileManager;
    private final Options options;
    private final Modules modules;
    private final Names names;
    private boolean fullRecompile = false;
    private Iterable<JavaFileObject> pendingFileObjects;

    protected IncrementalRecompileHandler(Context context) {
        super(context);
        compiler = JavaCompiler.instance(context);
        fileManager = context.get(JavaFileManager.class);
        options = Options.instance(context);
        modules = Modules.instance(context);
        names = Names.instance(context);
    }

    @Override
    public List<JCCompilationUnit> parse(Iterable<JavaFileObject> fileObjects) {
        ListBuffer<JCTree.JCCompilationUnit> result = new ListBuffer<>();
        String moduleName = options.get(MODULE);
        JavaFileManager.Location sourceLoc;
        try {
            sourceLoc = moduleName != null ? fileManager.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, moduleName) : null;
        } catch (IOException ex) {
            ex.printStackTrace();
            sourceLoc = null;
        }
        Map<JavaFileObject, JCTree.JCCompilationUnit> files2CUT = new IdentityHashMap<>();
        if (sourceLoc != null) {
            JavaFileManager.Location classLoc;
            try {
                classLoc = fileManager.getLocationForModule(StandardLocation.CLASS_OUTPUT, moduleName);
            } catch (IOException ex) {
                throw new Abort(ex);
            }
            for (JavaFileObject jfo : fileObjects) {
                try {
                    String className = fileManager.inferBinaryName(sourceLoc, jfo);
                    JavaFileObject classfile = className != null ? fileManager.getJavaFileForInput(classLoc, className, JavaFileObject.Kind.CLASS) : null;
                    boolean modified = classfile == null || classfile.getLastModified() <= jfo.getLastModified();
                    if (modified || "module-info".equals(className)) {
                        JCTree.JCCompilationUnit parsed = compiler.parse(jfo);
                        files2CUT.put(jfo, parsed);
                        result.add(parsed);

                        byte[] currentInternalDigest = parsed.internalDigest = treeDigest(parsed);
                        Digests digests = classfile != null ? readClassFileDigest(classfile) : null;
                        byte[] storedInternalDigest = digests != null ? digests.internalDigest : null;
                        if (!Arrays.equals(currentInternalDigest, storedInternalDigest)) {
                            fullRecompile = true;
                        } else if (!modified) {
                            parsed.doNotGenerate = true;
                        }
                        if (parsed.getModuleDecl() != null && digests != null) {
                            parsed.getModuleDecl().apiDigest = digests.apiDigest;
                            parsed.getModuleDecl().dependenciesDigests = digests.dependenciesDigests;
                        }
                    }
                } catch (IOException ex) {
                    fullRecompile = true;
                }
            }
//            if (!fullRecompile) {
//                noApiChange.set(true);
//            }
        } else {
            fullRecompile = true;
        }
        if (fullRecompile) {
            for (JavaFileObject jfo : fileObjects) {
                if (!files2CUT.containsKey(jfo)) {
                    JCTree.JCCompilationUnit parsed = files2CUT.get(jfo);
                    if (parsed == null) {
                        parsed = compiler.parse(jfo);
                        parsed.internalDigest = treeDigest(parsed);
                    }
                    result.add(parsed);
                }
            }
            files2CUT.values().forEach(cut -> cut.doNotGenerate = false);
        } else {
            ListBuffer<JavaFileObject> pendingFileObjects = new ListBuffer<>();
            for (JavaFileObject jfo : fileObjects) {
                if (!files2CUT.containsKey(jfo)) {
                    pendingFileObjects.add(jfo);
                }
            }
            this.pendingFileObjects = pendingFileObjects;
        }
//        if (debug) {
//            long allJavaInputs = StreamSupport.stream(fileObjects.spliterator(), false).count();
//            String module = StreamSupport.stream(fileObjects.spliterator(), false)
//                         .map(fo -> fo.toUri().toString())
//                         .filter(path -> path.contains("/share/classes/"))
//                         .map(path -> path.substring(0, path.indexOf("/share/classes/")))
//                         .map(path -> path.substring(path.lastIndexOf("/") + 1))
//                         .findAny()
//                         .orElseGet(() -> "unknown");
//            String nonJavaModifiedFiles = modified.stream()
//                                                  .map(Path::toString)
//                                                  .filter(f -> !StringUtils.toLowerCase(f)
//                                                                           .endsWith(".java"))
//                                                  .collect(Collectors.joining(", "));
//            System.err.println("compiling module: " + module +
//                               ", all Java inputs: " + allJavaInputs +
//                               ", modified files (Java or non-Java): " + modified.size() +
//                               ", full recompile: " + fullRecompile +
//                               ", non-Java modified files: " + nonJavaModifiedFiles);
//        }
        return result.toList();
    }

    private Digests readClassFileDigest(JavaFileObject classfile) {
        try {
            byte[] internalDigest = null;
            byte[] apiDigest = null;
            Map<String, byte[]> dependenciesDigests = null;
            ClassModel model = java.lang.classfile.ClassFile.of().parse(classfile.openInputStream().readAllBytes());
            for (java.lang.classfile.Attribute<?> attr : model.attributes()) {
                if (attr instanceof UnknownAttribute unknown && attr.attributeName().contentEquals("javac.InternalDigest")) {
                    internalDigest = unknown.contents();
                } else if (attr instanceof UnknownAttribute unknown && attr.attributeName().contentEquals("javac.APIDigest")) {
                    apiDigest = unknown.contents();
                } else if (attr instanceof UnknownAttribute unknown && attr.attributeName().contentEquals("javac.DependenciesDigests")) {
                    dependenciesDigests = new HashMap<>();
                    byte[] data = unknown.contents();
                    int count = (data[0] << 8) + data[1];
                    int pointer = 2;

                    for (int i = 0; i < count; i++) {
                        int nameIdx = (data[pointer] << 8) + data[pointer + 1];

                        pointer += 2;
                        dependenciesDigests.put(model.constantPool().entryByIndex(nameIdx, Utf8Entry.class).stringValue(),
                                                Arrays.copyOfRange(data, pointer, pointer + 32));
                        pointer += 32;
                    }
                }
            }
            if (internalDigest != null) {
                return new Digests(internalDigest, apiDigest, dependenciesDigests);
            }
        } catch (IOException ex) {
            return null;
        }
        return null;
    }

    record Digests(byte[] internalDigest, byte[] apiDigest, Map<String, byte[]> dependenciesDigests) {}

    private byte[] treeDigest(JCTree.JCCompilationUnit cu) {
        try {
            TreeVisitor v = new TreeVisitor(MessageDigest.getInstance("MD5"));
            v.scan(cu, null);
            return v.apiHash.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<JCCompilationUnit> checkDependencies(List<JCCompilationUnit> baseUnits) {
        if (fullRecompile) {
            return baseUnits;
        }

        List<JCCompilationUnit> result = baseUnits;
        AtomicBoolean fullRecompile = new AtomicBoolean();

        for (JCCompilationUnit cut : baseUnits) {
            if (cut.getModuleDecl() != null) {
                Map<String, byte[]> dependenciesDigests = cut.getModuleDecl().dependenciesDigests;
                ModuleSymbol thisModule = cut.getModuleDecl().sym;
                thisModule.requires.stream().forEach(rd -> {
                    byte[] recordedDigest = dependenciesDigests != null ? dependenciesDigests.get(rd.module.name.toString()) : null;
                    byte[] actualDigest = rd.module.apiDigest;
                    if (!Arrays.equals(recordedDigest, actualDigest) || recordedDigest == null) {
                        if ((rd.module.flags() & Flags.SYSTEM_MODULE) == 0 || recordedDigest != null || actualDigest != null) {
                            fullRecompile.set(true);
                        }
                    }
                });
            }
        }

        if (fullRecompile.get()) {
            for (JavaFileObject toParse : pendingFileObjects) {
                JCCompilationUnit parsed = compiler.parse(toParse);
                parsed.internalDigest = treeDigest(parsed);
                result = result.prepend(parsed);
                modules.enter(List.of(parsed), null);
            }
            for (JCCompilationUnit u : baseUnits) {
                u.doNotGenerate = false; //XXX: back
            }
        }

        return result;
    }

    private static final class TreeVisitor extends com.sun.source.util.TreeScanner<Void, Void> {

        private final Set<javax.lang.model.element.Name> seenIdentifiers = new HashSet<>();
        private final MessageDigest apiHash;
        private final Charset utf8;

        public TreeVisitor(MessageDigest apiHash) {
            this.apiHash = apiHash;
            utf8 = Charset.forName("UTF-8");
        }

        private void update(CharSequence data) {
            apiHash.update(data.toString().getBytes(utf8));
        }

        @Override
        public Void scan(Tree tree, Void p) {
            update("(");
            if (tree != null) {
                update(tree.getKind().name());
            };
            super.scan(tree, p);
            update(")");
            return null;
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
            seenIdentifiers.clear();
            scan(node.getPackage(), p);
            scan(node.getTypeDecls(), p);
            scan(((JCTree.JCCompilationUnit) node).getModuleDecl(), p);
            java.util.List<ImportTree> importantImports = new ArrayList<>();
            for (ImportTree imp : node.getImports()) {
                Tree t = imp.getQualifiedIdentifier();
                if (t.getKind() == Tree.Kind.MEMBER_SELECT) {
                    javax.lang.model.element.Name member = ((MemberSelectTree) t).getIdentifier();
                    if (member.contentEquals("*") || seenIdentifiers.contains(member)) {
                        importantImports.add(imp);
                    }
                } else {
                    //should not happen, possibly erroneous source?
                    importantImports.add(imp);
                }
            }
            importantImports.sort((imp1, imp2) -> {
                if (imp1.isStatic() ^ imp2.isStatic()) {
                    return imp1.isStatic() ? -1 : 1;
                } else {
                    return imp1.getQualifiedIdentifier().toString().compareTo(imp2.getQualifiedIdentifier().toString());
                }
            });
            scan(importantImports, p);
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void p) {
            update(node.getName());
            seenIdentifiers.add(node.getName());
            return super.visitIdentifier(node, p);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void p) {
            update(node.getIdentifier());
            return super.visitMemberSelect(node, p);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void p) {
            update(node.getName());
            return super.visitMemberReference(node, p);
        }

        @Override
        public Void scan(Iterable<? extends Tree> nodes, Void p) {
            update("(");
            super.scan(nodes, p);
            update(")");
            return null;
        }

        @Override
        public Void visitClass(ClassTree node, Void p) {
            update(node.getSimpleName());
            scan(node.getModifiers(), p);
            scan(node.getTypeParameters(), p);
            scan(node.getExtendsClause(), p);
            scan(node.getImplementsClause(), p);
            scan(node.getMembers()
                     .stream()
                     .filter(this::importantMember)
                     .collect(Collectors.toList()),
                 p);
            return null;
        }

        private boolean importantMember(Tree m) {
            return switch (m.getKind()) {
                case ANNOTATION_TYPE, CLASS, ENUM, INTERFACE, RECORD ->
                    !isPrivate(((ClassTree) m).getModifiers());
                case METHOD ->
                    !isPrivate(((MethodTree) m).getModifiers());
                case VARIABLE ->
                    !isPrivate(((VariableTree) m).getModifiers()) ||
                    isRecordComponent((VariableTree) m);
                case BLOCK -> false;
                default -> false;
            };
        }

        private boolean isPrivate(ModifiersTree mt) {
            return mt.getFlags().contains(Modifier.PRIVATE);
        }

        private boolean isRecordComponent(VariableTree vt) {
            return (((JCTree.JCVariableDecl) vt).mods.flags & Flags.RECORD) != 0;
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            update(node.getName());
            return super.visitVariable(node, p);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            update(node.getName());
            scan(node.getModifiers(), p);
            scan(node.getReturnType(), p);
            scan(node.getTypeParameters(), p);
            scan(node.getParameters(), p);
            scan(node.getReceiverParameter(), p);
            scan(node.getThrows(), p);
            scan(node.getDefaultValue(), p);
            return null;
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void p) {
            update(String.valueOf(node.getValue()));
            return super.visitLiteral(node, p);
        }

        @Override
        public Void visitModifiers(ModifiersTree node, Void p) {
            update(node.getFlags().toString());
            return super.visitModifiers(node, p);
        }

        @Override
        public Void visitPrimitiveType(PrimitiveTypeTree node, Void p) {
            update(node.getPrimitiveTypeKind().name());
            return super.visitPrimitiveType(node, p);
        }

    }



    public List<JCCompilationUnit> recordApiHashes(List<JCCompilationUnit> units) {
        for (JCCompilationUnit unit : units) {
            if (unit.getModuleDecl() == null) {
                continue;
            }
            MessageDigest apiHash;
            try {
                apiHash = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
            ModuleSymbol module = unit.modle;
            new APIVisitor(apiHash).visit(module);
            module.exports.stream().filter(ed -> ed.modules == null).flatMap(ed -> ed.getPackage().getEnclosedElements().stream()).forEach(s -> new APIVisitor(apiHash).visit(s));
            module.apiDigest = apiHash.digest();
        }
        return units;
    }
    
    private static final class APIVisitor implements ElementVisitor<Void, Void>,
                                                     TypeVisitor<Void, Void>,
                                                     AnnotationValueVisitor<Void, Void>,
                                                     ModuleElement.DirectiveVisitor<Void, Void> {

        private final MessageDigest apiHash;
        private final Charset utf8;

        public APIVisitor(MessageDigest apiHash) {
            this.apiHash = apiHash;
            utf8 = Charset.forName("UTF-8");
        }

        public Void visit(Iterable<? extends Element> list, Void p) {
            list.forEach(e -> visit(e, p));
            return null;
        }

        private void update(CharSequence data) {
            apiHash.update(data.toString().getBytes(utf8));
        }

        private void visit(Iterable<? extends TypeMirror> types) {
            for (TypeMirror type : types) {
                visit(type);
            }
        }

        private void updateAnnotation(AnnotationMirror am) {
            update("@");
            visit(am.getAnnotationType());
            am.getElementValues()
              .keySet()
              .stream()
              .sorted((ee1, ee2) -> ee1.getSimpleName().toString().compareTo(ee2.getSimpleName().toString()))
              .forEach(ee -> {
                  visit(ee);
                  visit(am.getElementValues().get(ee));
              });
        }

        private void updateAnnotations(Iterable<? extends AnnotationMirror> annotations) {
            for (AnnotationMirror am : annotations) {
                if (am.getAnnotationType().asElement().getAnnotation(Documented.class) == null)
                    continue;
                updateAnnotation(am);
            }
        }

        @Override
        public Void visit(Element e, Void p) {
            if (e.getKind() != ElementKind.MODULE &&
                !e.getModifiers().contains(Modifier.PUBLIC) &&
                !e.getModifiers().contains(Modifier.PROTECTED)) {
                return null;
            }
            update(e.getKind().name());
            update(e.getModifiers().stream()
                                   .map(mod -> mod.name())
                                   .collect(Collectors.joining(",", "[", "]")));
            update(e.getSimpleName());
            updateAnnotations(e.getAnnotationMirrors());
            return e.accept(this, p);
        }

        @Override
        public Void visit(Element e) {
            return visit(e, null);
        }

        @Override
        public Void visitModule(ModuleElement e, Void p) {
            update(String.valueOf(e.isOpen()));
            update(e.getQualifiedName());
            e.getDirectives()
             .stream()
             .forEach(d -> d.accept(this, null));
            return null;
        }

        @Override
        public Void visitPackage(PackageElement e, Void p) {
            throw new UnsupportedOperationException("Should not get here.");
        }

        @Override
        public Void visitType(TypeElement e, Void p) {
            visit(e.getTypeParameters(), p);
            visit(e.getSuperclass());
            visit(e.getInterfaces());
            visit(e.getEnclosedElements(), p);
            return null;
        }

        @Override
        public Void visitRecordComponent(@SuppressWarnings("preview")RecordComponentElement e, Void p) {
            update(e.getSimpleName());
            visit(e.asType());
            return null;
        }

        @Override
        public Void visitVariable(VariableElement e, Void p) {
            visit(e.asType());
            update(String.valueOf(e.getConstantValue()));
            return null;
        }

        @Override
        public Void visitExecutable(ExecutableElement e, Void p) {
            update("<");
            visit(e.getTypeParameters(), p);
            update(">");
            visit(e.getReturnType());
            update("(");
            visit(e.getParameters(), p);
            update(")");
            visit(e.getThrownTypes());
            update(String.valueOf(e.getDefaultValue()));
            update(String.valueOf(e.isVarArgs()));
            return null;
        }

        @Override
        public Void visitTypeParameter(TypeParameterElement e, Void p) {
            visit(e.getBounds());
            return null;
        }

        @Override
        public Void visitUnknown(Element e, Void p) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Void visit(TypeMirror t, Void p) {
            if (t == null) {
                update("null");
                return null;
            }
            update(t.getKind().name());
            updateAnnotations(t.getAnnotationMirrors());
            t.accept(this, p);
            return null;
        }

        @Override
        public Void visitPrimitive(PrimitiveType t, Void p) {
            return null; //done
        }

        @Override
        public Void visitNull(NullType t, Void p) {
            return null; //done
        }

        @Override
        public Void visitArray(ArrayType t, Void p) {
            visit(t.getComponentType());
            update("[]");
            return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, Void p) {
            update(((QualifiedNameable) t.asElement()).getQualifiedName());
            update("<");
            visit(t.getTypeArguments());
            update(">");
            return null;
        }

        @Override
        public Void visitError(ErrorType t, Void p) {
            return visitDeclared(t, p);
        }

        private final Set<Element> seenVariables = new HashSet<>();

        @Override
        public Void visitTypeVariable(TypeVariable t, Void p) {
            Element decl = t.asElement();
            if (!seenVariables.add(decl)) {
                return null;
            }
            visit(decl, null);
            visit(t.getLowerBound(), null);
            visit(t.getUpperBound(), null);
            seenVariables.remove(decl);
            return null;
        }

        @Override
        public Void visitWildcard(WildcardType t, Void p) {
            visit(t.getSuperBound());
            visit(t.getExtendsBound());
            return null;
        }

        @Override
        public Void visitExecutable(ExecutableType t, Void p) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Void visitNoType(NoType t, Void p) {
            return null;//done
        }

        @Override
        public Void visitUnknown(TypeMirror t, Void p) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Void visitUnion(UnionType t, Void p) {
            update("(");
            visit(t.getAlternatives());
            update(")");
            return null;
        }

        @Override
        public Void visitIntersection(IntersectionType t, Void p) {
            update("(");
            visit(t.getBounds());
            update(")");
            return null;
        }

        @Override
        public Void visit(AnnotationValue av, Void p) {
            return av.accept(this, p);
        }

        @Override
        public Void visitBoolean(boolean b, Void p) {
            update(String.valueOf(b));
            return null;
        }

        @Override
        public Void visitByte(byte b, Void p) {
            update(String.valueOf(b));
            return null;
        }

        @Override
        public Void visitChar(char c, Void p) {
            update(String.valueOf(c));
            return null;
        }

        @Override
        public Void visitDouble(double d, Void p) {
            update(String.valueOf(d));
            return null;
        }

        @Override
        public Void visitFloat(float f, Void p) {
            update(String.valueOf(f));
            return null;
        }

        @Override
        public Void visitInt(int i, Void p) {
            update(String.valueOf(i));
            return null;
        }

        @Override
        public Void visitLong(long i, Void p) {
            update(String.valueOf(i));
            return null;
        }

        @Override
        public Void visitShort(short s, Void p) {
            update(String.valueOf(s));
            return null;
        }

        @Override
        public Void visitString(String s, Void p) {
            update(s);
            return null;
        }

        @Override
        public Void visitType(TypeMirror t, Void p) {
            return visit(t);
        }

        @Override
        public Void visitEnumConstant(VariableElement c, Void p) {
            return visit(c);
        }

        @Override
        public Void visitAnnotation(AnnotationMirror a, Void p) {
            updateAnnotation(a);
            return null;
        }

        @Override
        public Void visitArray(java.util.List<? extends AnnotationValue> vals, Void p) {
            update("(");
            for (AnnotationValue av : vals) {
                visit(av);
            }
            update(")");
            return null;
        }

        @Override
        public Void visitUnknown(AnnotationValue av, Void p) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Void visitRequires(ModuleElement.RequiresDirective d, Void p) {
            update("RequiresDirective");
            update(String.valueOf(d.isStatic()));
            update(String.valueOf(d.isTransitive()));
            update(d.getDependency().getQualifiedName());
            return null;
        }

        @Override
        public Void visitExports(ModuleElement.ExportsDirective d, Void p) {
            update("ExportsDirective");
            update(d.getPackage().getQualifiedName());
            if (d.getTargetModules() != null) {
                for (ModuleElement me : d.getTargetModules()) {
                    update(me.getQualifiedName());
                }
            } else {
                update("<none>");
            }
            return null;
        }

        @Override
        public Void visitOpens(ModuleElement.OpensDirective d, Void p) {
            update("OpensDirective");
            update(d.getPackage().getQualifiedName());
            if (d.getTargetModules() != null) {
                for (ModuleElement me : d.getTargetModules()) {
                    update(me.getQualifiedName());
                }
            } else {
                update("<none>");
            }
            return null;
        }

        @Override
        public Void visitUses(ModuleElement.UsesDirective d, Void p) {
            update("UsesDirective");
            update(d.getService().getQualifiedName());
            return null;
        }

        @Override
        public Void visitProvides(ModuleElement.ProvidesDirective d, Void p) {
            update("ProvidesDirective");
            update(d.getService().getQualifiedName());
            update("(");
            for (TypeElement impl : d.getImplementations()) {
                update(impl.getQualifiedName());
            }
            update(")");
            return null;
        }

    }
}
