package json.java21.jsonpath;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/// Emits a JsonPath executor class from a parsed [JsonPathAst] tree.
///
/// The generated class implements the package-private [JsonPathExecutor]
/// interface as Java 21 bytecode (`ClassFile.JAVA_21_VERSION`, version 65),
/// with one private static continuation method per path segment, mirroring the
/// interpreted engine's evaluation order exactly. Filter predicates and
/// comparisons delegate to the [JsonPathHelpers] methods, the single source of
/// truth shared with the interpreted engine.
///
/// This class requires JDK 24+ (ClassFile API, JEP 484) and lives in the
/// profile-gated codegen source root; see the module README ("Two Engines").
final class JsonPathBytecodeEmitter {

    private static final Logger LOG = Logger.getLogger(JsonPathBytecodeEmitter.class.getName());

    /// The one runtime package generated executors may be emitted into: generated
    /// classes implement the package-private [JsonPathExecutor] interface, so they
    /// must be defined in a package that can access it.
    static final String SUPPORTED_PACKAGE = "json.java21.jsonpath";

    // -- class descriptors --
    private static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    private static final ClassDesc CD_Collection = ClassDesc.of("java.util.Collection");
    private static final ClassDesc CD_Iterator = ClassDesc.of("java.util.Iterator");
    private static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
    private static final ClassDesc CD_Arrays = ClassDesc.of("java.util.Arrays");
    private static final ClassDesc CD_Math = ClassDesc.of("java.lang.Math");
    private static final ClassDesc CD_JsonValue = ClassDesc.of("jdk.incubator.java.util.json.JsonValue");
    private static final ClassDesc CD_JsonObject = ClassDesc.of("jdk.incubator.java.util.json.JsonObject");
    private static final ClassDesc CD_JsonArray = ClassDesc.of("jdk.incubator.java.util.json.JsonArray");
    private static final ClassDesc CD_ComparisonOp = ClassDesc.of("json.java21.jsonpath.JsonPathAst$ComparisonOp");
    private static final ClassDesc CD_JsonPathExecutor = ClassDesc.of("json.java21.jsonpath.JsonPathExecutor");
    private static final ClassDesc CD_Helpers = ClassDesc.of("json.java21.jsonpath.JsonPathHelpers");

    // -- method type descriptors --
    private static final MethodTypeDesc MTD_EXECUTE = MethodTypeDesc.of(CD_List, CD_JsonValue, CD_JsonValue);
    private static final MethodTypeDesc MTD_CONT = MethodTypeDesc.of(ConstantDescs.CD_void, CD_JsonValue, CD_JsonValue, CD_List);
    private static final MethodTypeDesc MTD_PRED = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_JsonValue);
    private static final MethodTypeDesc MTD_MAP = MethodTypeDesc.of(CD_Map);
    private static final MethodTypeDesc MTD_LIST = MethodTypeDesc.of(CD_List);
    private static final MethodTypeDesc MTD_COLLECTION = MethodTypeDesc.of(CD_Collection);
    private static final MethodTypeDesc MTD_ITERATOR = MethodTypeDesc.of(CD_Iterator);
    private static final MethodTypeDesc MTD_GET_OBJECT = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_OBJECT_INT = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_INT = MethodTypeDesc.of(ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_ADD = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_ITERATOR_HAS_NEXT = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_ITERATOR_NEXT = MethodTypeDesc.of(ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_II_I = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_INTEGER_VALUE_OF = MethodTypeDesc.of(ConstantDescs.CD_Integer, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_LONG_VALUE_OF = MethodTypeDesc.of(ConstantDescs.CD_Long, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_DOUBLE_VALUE_OF = MethodTypeDesc.of(ConstantDescs.CD_Double, ConstantDescs.CD_double);
    private static final MethodTypeDesc MTD_FLOAT_VALUE_OF = MethodTypeDesc.of(ConstantDescs.CD_Float, ConstantDescs.CD_float);
    private static final MethodTypeDesc MTD_RESOLVE_PATH = MethodTypeDesc.of(CD_JsonValue, CD_JsonValue, CD_List);
    private static final MethodTypeDesc MTD_TO_COMPARABLE = MethodTypeDesc.of(ConstantDescs.CD_Object, CD_JsonValue);
    private static final MethodTypeDesc MTD_COMPARE = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object, CD_ComparisonOp, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_VOID_STRING = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_ARRAYS_AS_LIST = MethodTypeDesc.of(CD_List, ConstantDescs.CD_Object.arrayType());

    private final ClassBuilder clb;
    private final ClassDesc self;
    private final List<JsonPathAst.Segment> segments;
    private final Map<JsonPathAst.FilterExpression, Integer> predicateMethods = new HashMap<>();
    private final Map<List<String>, Integer> pathConstants = new HashMap<>();

    private JsonPathBytecodeEmitter(ClassBuilder clb, ClassDesc self, List<JsonPathAst.Segment> segments) {
        this.clb = clb;
        this.self = self;
        this.segments = segments;
    }

    /// Emits the classfile bytes for a compiled JsonPath executor.
    ///
    /// @param packageName target package (must be the supported runtime package so the
    ///                    generated class can implement the package-private executor)
    /// @param className simple class name within the target package
    /// @param ast the parsed JsonPath AST to compile
    /// @return the generated classfile bytes
    static byte[] emit(String packageName, String className, JsonPathAst.Root ast) {
        if (!SUPPORTED_PACKAGE.equals(packageName)) {
            throw new IllegalArgumentException(
                    "generated executors can only be emitted into package '" + SUPPORTED_PACKAGE
                            + "' so they implement the package-private JsonPathExecutor interface, got: '"
                            + packageName + "'");
        }
        final var internalName = packageName.replace('.', '/') + "/" + className;
        final var classDesc = ClassDesc.ofInternalName(internalName);
        LOG.fine(() -> "Emitting JsonPath executor " + internalName + " with " + ast.segments().size() + " segments");

        return ClassFile.of().build(classDesc, clb -> {
            clb.withVersion(ClassFile.JAVA_21_VERSION, 0);
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            clb.withSuperclass(ConstantDescs.CD_Object);
            clb.withInterfaceSymbols(CD_JsonPathExecutor);
            clb.with(SourceFileAttribute.of("JsonPathCodegen"));
            new JsonPathBytecodeEmitter(clb, classDesc, ast.segments()).emitClass();
        });
    }

    private void emitClass() {
        emitConstructor();
        emitExecute();
        for (final var segment : segments) {
            if (segment instanceof JsonPathAst.Filter(JsonPathAst.FilterExpression expression)) {
                predicateMethodFor(expression);
            }
        }
        emitContinuation(0);
        emitPathConstantFields();
    }

    private void emitConstructor() {
        clb.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, cob -> {
            cob.aload(0);
            cob.invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
            cob.return_();
        });
    }

    /// Instance method slots: 0 = this, 1 = current, 2 = root; the fresh
    /// results list is allocated into the next local slot.
    private void emitExecute() {
        clb.withMethodBody("execute", MTD_EXECUTE, ClassFile.ACC_PUBLIC, cob -> {
            cob.new_(CD_ArrayList);
            cob.dup();
            cob.invokespecial(CD_ArrayList, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
            final int resultsSlot = cob.allocateLocal(TypeKind.REFERENCE);
            cob.astore(resultsSlot);
            cob.aload(1);
            cob.aload(2);
            cob.aload(resultsSlot);
            cob.invokestatic(self, "cont0", MTD_CONT);
            cob.aload(resultsSlot);
            cob.areturn();
        });
    }

    /// Emits `contK`, the static continuation method for segment K. Conventions
    /// for continuation methods: slot 0 = current value, slot 1 = root, slot 2 = results.
    private void emitContinuation(int k) {
        clb.withMethodBody("cont" + k, MTD_CONT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            if (k >= segments.size()) {
                emitTerminal(cob);
                return;
            }
            emitSegment(k, cob);
        });
        if (k < segments.size()) {
            emitContinuation(k + 1);
        }
    }

    /// Terminal continuation: the whole path matched, so record the current node.
    private static void emitTerminal(CodeBuilder cob) {
        cob.aload(2);
        cob.aload(0);
        cob.invokeinterface(CD_List, "add", MTD_ADD);
        cob.pop();
        cob.return_();
    }

    /// Invokes the continuation for the next segment with the given candidate value.
    /// Needs the emitter's `self` descriptor, so it stays an instance method.
    private void emitContinueWith(CodeBuilder cob, int k, int valueSlot) {
        cob.aload(valueSlot);
        cob.aload(1);
        cob.aload(2);
        cob.invokestatic(self, "cont" + (k + 1), MTD_CONT);
    }

    // -- segment emission --

    private void emitSegment(int k, CodeBuilder cob) {
        final var done = cob.newLabel();
        switch (segments.get(k)) {
            case JsonPathAst.PropertyAccess(String name) ->
                    emitPropertyMatch(cob, k, name, done);
            case JsonPathAst.ArrayIndex(int index) ->
                    emitIndexMatch(cob, k, index, done);
            case JsonPathAst.ArraySlice(Integer start, Integer end, Integer step) ->
                    emitSlice(cob, k, start, end, step, done);
            case JsonPathAst.Wildcard ignored ->
                    emitChildren(cob, k, done);
            case JsonPathAst.RecursiveDescent(JsonPathAst.Segment target) -> {
                // descK applies the target at the current level and then walks
                // every descendant, mirroring evaluateRecursiveDescent.
                emitRecursiveDescent(k, target);
                cob.aload(0);
                cob.aload(1);
                cob.aload(2);
                cob.invokestatic(self, "desc" + k, MTD_CONT);
            }
            case JsonPathAst.Filter(JsonPathAst.FilterExpression expression) -> {
                final int predMethod = predicateMethods.get(expression);
                cob.aload(0);
                cob.instanceOf(CD_JsonArray);
                cob.ifeq(done);
                final int listSlot = emitAsList(cob);
                emitFilterLoop(cob, k, listSlot, predMethod);
            }
            case JsonPathAst.Union(List<JsonPathAst.Segment> selectors) -> {
                for (final var selector : selectors) {
                    // each selector jumps to its own end label on failure so the
                    // remaining selectors still evaluate
                    final var selectorDone = cob.newLabel();
                    switch (selector) {
                        case JsonPathAst.PropertyAccess(String name) ->
                                emitPropertyMatch(cob, k, name, selectorDone);
                        case JsonPathAst.ArrayIndex(int index) ->
                                emitIndexMatch(cob, k, index, selectorDone);
                        default -> {
                            LOG.finer(() -> "Unsupported selector in union: " + selector);
                            cob.labelBinding(selectorDone);
                            continue;
                        }
                    }
                    cob.labelBinding(selectorDone);
                }
            }
            case JsonPathAst.ScriptExpression(String script) ->
                    emitScriptExpression(cob, k, script, done);
        }
        cob.labelBinding(done);
        cob.return_();
    }

    /// Matches a named property: current must be a JsonObject holding the key.
    private void emitPropertyMatch(CodeBuilder cob, int k, String name, Label done) {
        cob.aload(0);
        cob.instanceOf(CD_JsonObject);
        cob.ifeq(done);
        cob.aload(0);
        cob.checkcast(CD_JsonObject);
        cob.invokeinterface(CD_JsonObject, "asMap", MTD_MAP);
        cob.ldc(name);
        cob.invokeinterface(CD_Map, "get", MTD_GET_OBJECT);
        cob.checkcast(CD_JsonValue);
        final int valueSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(valueSlot);
        cob.aload(valueSlot);
        cob.instanceOf(CD_JsonValue); // false for null: mirrors the interpreter's null check
        cob.ifeq(done);
        emitContinueWith(cob, k, valueSlot);
    }

    /// Matches an array element by absolute or negative-from-end index.
    private void emitIndexMatch(CodeBuilder cob, int k, int index, Label done) {
        cob.aload(0);
        cob.instanceOf(CD_JsonArray);
        cob.ifeq(done);
        final int listSlot = emitAsList(cob);
        final int sizeSlot = emitListSize(cob, listSlot);
        final int idxSlot = cob.allocateLocal(TypeKind.INT);
        // idx = index < 0 ? size + index : index
        final var negativeLabel = cob.newLabel();
        final var storeLabel = cob.newLabel();
        cob.ldc(index);
        cob.iflt(negativeLabel);
        cob.ldc(index);
        cob.goto_(storeLabel);
        cob.labelBinding(negativeLabel);
        cob.iload(sizeSlot);
        cob.ldc(index);
        cob.iadd();
        cob.labelBinding(storeLabel);
        cob.istore(idxSlot);
        cob.iload(idxSlot);
        cob.iflt(done);
        cob.iload(idxSlot);
        cob.iload(sizeSlot);
        cob.if_icmpge(done);
        cob.aload(listSlot);
        cob.iload(idxSlot);
        cob.invokeinterface(CD_List, "get", MTD_OBJECT_INT);
        cob.checkcast(CD_JsonValue);
        final int elementSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(elementSlot);
        emitContinueWith(cob, k, elementSlot);
    }

    /// Matches all direct children (object values then array elements).
    private void emitChildren(CodeBuilder cob, int k, Label done) {
        emitWalkChildren(cob, "cont" + (k + 1), done);
    }

    /// Walks all direct children (object values then array elements) and invokes
    /// the named static continuation method with (child, root, results) for each.
    /// Used both by the wildcard segment (continuing with the next segment) and
    /// by recursive descent (re-entering the descent itself).
    private void emitWalkChildren(CodeBuilder cob, String callTarget, Label done) {
        final var arrayLabel = cob.newLabel();
        final var objectDone = cob.newLabel();

        // Object values
        cob.aload(0);
        cob.instanceOf(CD_JsonObject);
        cob.ifeq(arrayLabel);
        final var loopStart = cob.newLabel();
        final var loopEnd = cob.newLabel();
        cob.aload(0);
        cob.checkcast(CD_JsonObject);
        cob.invokeinterface(CD_JsonObject, "asMap", MTD_MAP);
        cob.invokeinterface(CD_Map, "values", MTD_COLLECTION);
        cob.invokeinterface(CD_Collection, "iterator", MTD_ITERATOR);
        final int iteratorSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(iteratorSlot);
        cob.labelBinding(loopStart);
        cob.aload(iteratorSlot);
        cob.invokeinterface(CD_Iterator, "hasNext", MTD_ITERATOR_HAS_NEXT);
        cob.ifeq(loopEnd);
        cob.aload(iteratorSlot);
        cob.invokeinterface(CD_Iterator, "next", MTD_ITERATOR_NEXT);
        cob.checkcast(CD_JsonValue);
        final int valueSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(valueSlot);
        cob.aload(valueSlot);
        cob.aload(1);
        cob.aload(2);
        cob.invokestatic(self, callTarget, MTD_CONT);
        cob.goto_(loopStart);
        cob.labelBinding(loopEnd);
        cob.goto_(objectDone);

        // Array elements
        cob.labelBinding(arrayLabel);
        cob.aload(0);
        cob.instanceOf(CD_JsonArray);
        cob.ifeq(done);
        final int listSlot = emitAsList(cob);
        final int sizeSlot = emitListSize(cob, listSlot);
        final int iSlot = cob.allocateLocal(TypeKind.INT);
        final var elementLoopStart = cob.newLabel();
        final var elementLoopEnd = cob.newLabel();
        cob.iconst_0();
        cob.istore(iSlot);
        cob.labelBinding(elementLoopStart);
        cob.iload(iSlot);
        cob.iload(sizeSlot);
        cob.if_icmpge(elementLoopEnd);
        cob.aload(listSlot);
        cob.iload(iSlot);
        cob.invokeinterface(CD_List, "get", MTD_OBJECT_INT);
        cob.checkcast(CD_JsonValue);
        final int elementSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(elementSlot);
        cob.aload(elementSlot);
        cob.aload(1);
        cob.aload(2);
        cob.invokestatic(self, callTarget, MTD_CONT);
        cob.iinc(iSlot, 1);
        cob.goto_(elementLoopStart);
        cob.labelBinding(elementLoopEnd);
        cob.labelBinding(objectDone);
    }

    /// Matches a slice by start:end:step, mirroring the interpreter's
    /// normalization and clamping rules exactly.
    private void emitSlice(CodeBuilder cob, int k, Integer start, Integer end, Integer step, Label done) {
        final int stepValue = step != null ? step : 1;
        cob.aload(0);
        cob.instanceOf(CD_JsonArray);
        cob.ifeq(done);
        final int listSlot = emitAsList(cob);
        final int sizeSlot = emitListSize(cob, listSlot);
        if (stepValue == 0) {
            return; // invalid step: no matches, like the interpreter
        }
        final int startSlot = cob.allocateLocal(TypeKind.INT);
        final int endSlot = cob.allocateLocal(TypeKind.INT);
        final int iSlot = cob.allocateLocal(TypeKind.INT);
        final var positiveLabel = cob.newLabel();
        final var negativeLabel = cob.newLabel();
        final var afterLabel = cob.newLabel();
        cob.ldc(stepValue);
        cob.ifgt(positiveLabel);
        cob.goto_(negativeLabel);

        cob.labelBinding(positiveLabel);
        emitForwardSlice(cob, k, listSlot, sizeSlot, start, end, stepValue, startSlot, endSlot, iSlot);
        cob.goto_(afterLabel);

        cob.labelBinding(negativeLabel);
        emitBackwardSlice(cob, k, listSlot, sizeSlot, start, end, stepValue, startSlot, endSlot, iSlot);

        cob.labelBinding(afterLabel);
    }

    private void emitForwardSlice(CodeBuilder cob, int k, int listSlot, int sizeSlot,
                                  Integer start, Integer end, int stepValue,
                                  int startSlot, int endSlot, int iSlot) {
        // start = (start != null) ? normalize(start, size) : 0
        emitOptionalBound(cob, start, sizeSlot, FALLBACK_ZERO, startSlot);
        emitOptionalBound(cob, end, sizeSlot, FALLBACK_SIZE, endSlot);
        clampLowHigh(cob, startSlot, 0, null, sizeSlot);
        clampLowHigh(cob, endSlot, 0, null, sizeSlot);
        final var loopStart = cob.newLabel();
        final var loopEnd = cob.newLabel();
        cob.iload(startSlot);
        cob.istore(iSlot);
        cob.labelBinding(loopStart);
        cob.iload(iSlot);
        cob.iload(endSlot);
        cob.if_icmpge(loopEnd);
        emitSliceElement(cob, k, listSlot, iSlot);
        cob.iinc(iSlot, stepValue);
        cob.goto_(loopStart);
        cob.labelBinding(loopEnd);
    }

    private void emitBackwardSlice(CodeBuilder cob, int k, int listSlot, int sizeSlot,
                                   Integer start, Integer end, int stepValue,
                                   int startSlot, int endSlot, int iSlot) {
        // start = (start != null) ? normalize(start, size) : size - 1
        emitOptionalBound(cob, start, sizeSlot, FALLBACK_SIZE_MINUS_ONE, startSlot);
        emitOptionalBound(cob, end, sizeSlot, FALLBACK_MINUS_ONE, endSlot);
        // start = Math.max(0, Math.min(start, size - 1))
        cob.iload(startSlot);
        cob.iload(sizeSlot);
        cob.iconst_1();
        cob.isub();
        cob.invokestatic(CD_Math, "min", MTD_II_I);
        cob.ldc(0);
        cob.invokestatic(CD_Math, "max", MTD_II_I);
        cob.istore(startSlot);
        final var loopStart = cob.newLabel();
        final var loopEnd = cob.newLabel();
        cob.iload(startSlot);
        cob.istore(iSlot);
        cob.labelBinding(loopStart);
        cob.iload(iSlot);
        cob.iload(endSlot);
        cob.if_icmple(loopEnd);
        emitSliceElement(cob, k, listSlot, iSlot);
        cob.iinc(iSlot, stepValue);
        cob.goto_(loopStart);
        cob.labelBinding(loopEnd);
    }

    /// Fallback kinds for slice bounds that the interpreter computes at runtime.
    private static final int FALLBACK_ZERO = 0;
    private static final int FALLBACK_SIZE = 1;
    private static final int FALLBACK_SIZE_MINUS_ONE = 2;
    private static final int FALLBACK_MINUS_ONE = 3;

    /// target = (bound != null) ? normalizeIndex(bound, size) : fallback(size)
    private void emitOptionalBound(CodeBuilder cob, Integer bound, int sizeSlot, int fallbackKind, int targetSlot) {
        if (bound != null) {
            cob.ldc(bound);
            cob.iload(sizeSlot);
            cob.invokestatic(CD_Helpers, "normalizeIndex", MTD_II_I);
        } else {
            switch (fallbackKind) {
                case FALLBACK_ZERO -> cob.ldc(0);
                case FALLBACK_SIZE -> cob.iload(sizeSlot);
                case FALLBACK_SIZE_MINUS_ONE -> {
                    cob.iload(sizeSlot);
                    cob.iconst_1();
                    cob.isub();
                }
                case FALLBACK_MINUS_ONE -> cob.ldc(-1);
                default -> throw new IllegalArgumentException("unknown fallback kind: " + fallbackKind);
            }
        }
        cob.istore(targetSlot);
    }

    /// slot = Math.max(low, Math.min(slot, high)) where high is either another
    /// int local (highSlot >= 0) or a constant (high).
    private static void clampLowHigh(CodeBuilder cob, int slot, int low, Integer high, int highSlot) {
        cob.iload(slot);
        if (highSlot >= 0) {
            cob.iload(highSlot);
        } else {
            cob.ldc(high);
        }
        cob.invokestatic(CD_Math, "min", MTD_II_I);
        cob.ldc(low);
        cob.invokestatic(CD_Math, "max", MTD_II_I);
        cob.istore(slot);
    }

    private void emitSliceElement(CodeBuilder cob, int k, int listSlot, int iSlot) {
        cob.aload(listSlot);
        cob.iload(iSlot);
        cob.invokeinterface(CD_List, "get", MTD_OBJECT_INT);
        cob.checkcast(CD_JsonValue);
        final int elementSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(elementSlot);
        emitContinueWith(cob, k, elementSlot);
    }

    /// Filter loop: for each array element, run the generated predicate and
    /// continue with matching elements.
    private void emitFilterLoop(CodeBuilder cob, int k, int listSlot, int predMethod) {
        final int sizeSlot = emitListSize(cob, listSlot);
        final int iSlot = cob.allocateLocal(TypeKind.INT);
        final var loopStart = cob.newLabel();
        final var loopEnd = cob.newLabel();
        final var continueLabel = cob.newLabel();
        cob.iconst_0();
        cob.istore(iSlot);
        cob.labelBinding(loopStart);
        cob.iload(iSlot);
        cob.iload(sizeSlot);
        cob.if_icmpge(loopEnd);
        cob.aload(listSlot);
        cob.iload(iSlot);
        cob.invokeinterface(CD_List, "get", MTD_OBJECT_INT);
        cob.checkcast(CD_JsonValue);
        final int elementSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(elementSlot);
        cob.aload(elementSlot);
        cob.invokestatic(self, "pred" + predMethod, MTD_PRED);
        cob.ifeq(continueLabel);
        emitContinueWith(cob, k, elementSlot);
        cob.labelBinding(continueLabel);
        cob.iinc(iSlot, 1);
        cob.goto_(loopStart);
        cob.labelBinding(loopEnd);
    }

    /// Limited script support: emits the last element for "(@.length-1)";
    /// any other script logs once through the shared helper, mirroring the interpreter.
    private void emitScriptExpression(CodeBuilder cob, int k, String script, Label done) {
        cob.aload(0);
        cob.instanceOf(CD_JsonArray);
        cob.ifeq(done);
        final int listSlot = emitAsList(cob);
        final int sizeSlot = emitListSize(cob, listSlot);
        if ("@.length-1".equals(script)) {
            final int lastIndexSlot = cob.allocateLocal(TypeKind.INT);
            cob.iload(sizeSlot);
            cob.iconst_1();
            cob.isub();
            cob.istore(lastIndexSlot);
            cob.iload(lastIndexSlot);
            cob.iflt(done);
            cob.aload(listSlot);
            cob.iload(lastIndexSlot);
            cob.invokeinterface(CD_List, "get", MTD_OBJECT_INT);
            cob.checkcast(CD_JsonValue);
            final int elementSlot = cob.allocateLocal(TypeKind.REFERENCE);
            cob.astore(elementSlot);
            emitContinueWith(cob, k, elementSlot);
        } else {
            cob.ldc(script);
            cob.invokestatic(CD_Helpers, "unsupportedScript", MTD_VOID_STRING);
        }
    }

    /// Recursive descent: applies the target at the current level and then
    /// walks every descendant, exactly like the interpreter.
    private void emitRecursiveDescent(int k, JsonPathAst.Segment target) {
        emitApplyTarget(k, target);
        clb.withMethodBody("desc" + k, MTD_CONT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            cob.aload(0);
            cob.aload(1);
            cob.aload(2);
            cob.invokestatic(self, "applyTarget" + k, MTD_CONT);
            final var done = cob.newLabel();
            emitWalkChildren(cob, "desc" + k, done);
            cob.labelBinding(done);
            cob.return_();
        });
    }

    /// Mirrors the interpreter's recursive-descent target handling: property
    /// access, wildcard, and array index targets match; anything else logs once.
    private void emitApplyTarget(int k, JsonPathAst.Segment target) {
        clb.withMethodBody("applyTarget" + k, MTD_CONT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            final var done = cob.newLabel();
            switch (target) {
                case JsonPathAst.PropertyAccess(String name) ->
                        emitPropertyMatch(cob, k, name, done);
                case JsonPathAst.Wildcard ignored ->
                        emitChildren(cob, k, done);
                case JsonPathAst.ArrayIndex(int index) ->
                        emitIndexMatch(cob, k, index, done);
                default ->
                        LOG.finer(() -> "Unsupported target in recursive descent: " + target);
            }
            cob.labelBinding(done);
            cob.return_();
        });
    }

    // -- filter predicate emission --

    private int predicateMethodFor(JsonPathAst.FilterExpression expression) {
        final var existing = predicateMethods.get(expression);
        if (existing != null) {
            return existing;
        }
        final int method = predicateMethods.size();
        predicateMethods.put(expression, method);
        emitPredicate(expression, method);
        return method;
    }

    private void emitPredicate(JsonPathAst.FilterExpression expression, int method) {
        clb.withMethodBody("pred" + method, MTD_PRED, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cob -> {
            switch (expression) {
                case JsonPathAst.ExistsFilter(JsonPathAst.PropertyPath path) -> {
                    cob.aload(0); // the candidate node
                    cob.getstatic(self, "P" + pathConstant(path.properties()), CD_List);
                    cob.invokestatic(CD_Helpers, "resolvePropertyPath", MTD_RESOLVE_PATH);
                    cob.instanceOf(CD_JsonValue); // false for null: value must exist
                    cob.ireturn();
                }
                case JsonPathAst.ComparisonFilter(var left, var op, var right) -> {
                    emitFilterOperand(cob, left);
                    cob.getstatic(CD_ComparisonOp, op.name(), CD_ComparisonOp);
                    emitFilterOperand(cob, right);
                    cob.invokestatic(CD_Helpers, "compareValues", MTD_COMPARE);
                    cob.ireturn();
                }
                case JsonPathAst.LogicalFilter(var left, var op, var right) -> {
                    final int leftMethod = predicateMethodFor(left);
                    switch (op) {
                        case AND -> {
                            final int rightMethod = predicateMethodFor(right);
                            final var falseLabel = cob.newLabel();
                            cob.aload(0);
                            cob.invokestatic(self, "pred" + leftMethod, MTD_PRED);
                            cob.ifeq(falseLabel);
                            cob.aload(0);
                            cob.invokestatic(self, "pred" + rightMethod, MTD_PRED);
                            cob.ireturn();
                            cob.labelBinding(falseLabel);
                            cob.iconst_0();
                            cob.ireturn();
                        }
                        case OR -> {
                            final int rightMethod = predicateMethodFor(right);
                            final var trueLabel = cob.newLabel();
                            final var falseLabel = cob.newLabel();
                            cob.aload(0);
                            cob.invokestatic(self, "pred" + leftMethod, MTD_PRED);
                            cob.ifne(trueLabel);
                            cob.aload(0);
                            cob.invokestatic(self, "pred" + rightMethod, MTD_PRED);
                            cob.ifeq(falseLabel);
                            cob.labelBinding(trueLabel);
                            cob.iconst_1();
                            cob.ireturn();
                            cob.labelBinding(falseLabel);
                            cob.iconst_0();
                            cob.ireturn();
                        }
                        case NOT -> {
                            final var falseLabel = cob.newLabel();
                            cob.aload(0);
                            cob.invokestatic(self, "pred" + leftMethod, MTD_PRED);
                            cob.ifne(falseLabel);
                            cob.iconst_1();
                            cob.ireturn();
                            cob.labelBinding(falseLabel);
                            cob.iconst_0();
                            cob.ireturn();
                        }
                    }
                }
                case JsonPathAst.CurrentNode ignored -> {
                    cob.iconst_1();
                    cob.ireturn();
                }
                case JsonPathAst.PropertyPath path -> {
                    cob.aload(0);
                    cob.getstatic(self, "P" + pathConstant(path.properties()), CD_List);
                    cob.invokestatic(CD_Helpers, "resolvePropertyPath", MTD_RESOLVE_PATH);
                    cob.instanceOf(CD_JsonValue);
                    cob.ireturn();
                }
                case JsonPathAst.LiteralValue ignored -> {
                    cob.iconst_1(); // mirrors the interpreter: bare literals match
                    cob.ireturn();
                }
            }
        });
    }

    /// Pushes the comparable operand for a filter expression onto the stack,
    /// mirroring the interpreter's resolveFilterExpression.
    private void emitFilterOperand(CodeBuilder cob, JsonPathAst.FilterExpression operand) {
        switch (operand) {
            case JsonPathAst.PropertyPath path -> {
                cob.aload(0);
                cob.getstatic(self, "P" + pathConstant(path.properties()), CD_List);
                cob.invokestatic(CD_Helpers, "resolvePropertyPath", MTD_RESOLVE_PATH);
                cob.invokestatic(CD_Helpers, "toJsonComparable", MTD_TO_COMPARABLE);
            }
            case JsonPathAst.LiteralValue(Object value) -> emitLiteral(cob, value);
            case JsonPathAst.CurrentNode ignored -> {
                cob.aload(0);
                cob.invokestatic(CD_Helpers, "toJsonComparable", MTD_TO_COMPARABLE);
            }
            default -> cob.aconst_null();
        }
    }

    /// Pushes a boxed filter literal (String, Boolean, Long, Double, or JSON null).
    private static void emitLiteral(CodeBuilder cob, Object value) {
        switch (value) {
            case null -> cob.aconst_null();
            case String s -> cob.ldc(s);
            case Boolean b -> cob.getstatic(ConstantDescs.CD_Boolean, b ? "TRUE" : "FALSE", ConstantDescs.CD_Boolean);
            case Integer i -> {
                cob.ldc(i);
                cob.invokestatic(ConstantDescs.CD_Integer, "valueOf", MTD_INTEGER_VALUE_OF);
            }
            case Long l -> {
                cob.ldc(l);
                cob.invokestatic(ConstantDescs.CD_Long, "valueOf", MTD_LONG_VALUE_OF);
            }
            case Double d -> {
                cob.ldc(d);
                cob.invokestatic(ConstantDescs.CD_Double, "valueOf", MTD_DOUBLE_VALUE_OF);
            }
            case Float f -> {
                cob.ldc(f);
                cob.invokestatic(ConstantDescs.CD_Float, "valueOf", MTD_FLOAT_VALUE_OF);
            }
            default -> throw new IllegalArgumentException(
                    "unsupported filter literal type: " + value.getClass());
        }
    }

    // -- shared bytecode helpers for the emitted code --

    /// current.asList() of the JsonArray in local slot 0, stored in a fresh local.
    private static int emitAsList(CodeBuilder cob) {
        cob.aload(0);
        cob.checkcast(CD_JsonArray);
        cob.invokeinterface(CD_JsonArray, "asList", MTD_LIST);
        final int listSlot = cob.allocateLocal(TypeKind.REFERENCE);
        cob.astore(listSlot);
        return listSlot;
    }

    private static int emitListSize(CodeBuilder cob, int listSlot) {
        cob.aload(listSlot);
        cob.invokeinterface(CD_List, "size", MTD_INT);
        final int sizeSlot = cob.allocateLocal(TypeKind.INT);
        cob.istore(sizeSlot);
        return sizeSlot;
    }

    /// Returns (and registers) the constant-list field index for a property path.
    private int pathConstant(List<String> properties) {
        final var canonical = List.copyOf(properties);
        final var existing = pathConstants.get(canonical);
        if (existing != null) {
            return existing;
        }
        final int index = pathConstants.size();
        pathConstants.put(canonical, index);
        return index;
    }

    /// Emits the private static final List fields holding shared property paths,
    /// initialized in <clinit> once every user of them has been emitted.
    private void emitPathConstantFields() {
        for (final var entry : pathConstants.entrySet()) {
            clb.withField("P" + entry.getValue(), CD_List,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }
        clb.withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_STATIC, cob -> {
            for (final var entry : pathConstants.entrySet()) {
                final var properties = entry.getKey();
                cob.ldc(properties.size());
                cob.anewarray(ConstantDescs.CD_String);
                for (int i = 0; i < properties.size(); i++) {
                    cob.dup();
                    cob.ldc(i);
                    cob.ldc(properties.get(i));
                    cob.aastore();
                }
                cob.invokestatic(CD_Arrays, "asList", MTD_ARRAYS_AS_LIST);
                cob.putstatic(self, "P" + entry.getValue(), CD_List);
            }
            cob.return_();
        });
    }
}
