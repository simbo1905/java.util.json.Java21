package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Elements schema (array validation).
final class EmitElements {

  private EmitElements() {}

  static void emit(CodeBuilder cob, JtdSchema.ElementsSchema e,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonArray);
    cob.ifeq(fail);

    emitLoop(cob, e, instSlot, errSlot, instPath + "/", schemaPath + "/elements");

    cob.goto_(ok);

    cob.labelBinding(fail);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/elements");
    cob.labelBinding(ok);
  }

  /// Dynamic-path variant: parent instancePath comes from a local variable.
  static void emitDynamic(CodeBuilder cob, JtdSchema.ElementsSchema e,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonArray);
    cob.ifeq(fail);

    // Build prefix: parentPath + "/"
    cob.aload(pathSlot);
    cob.ldc("/");
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int prefixSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(prefixSlot);

    emitLoopDynamic(cob, e, instSlot, errSlot, prefixSlot, schemaPath + "/elements");

    cob.goto_(ok);

    cob.labelBinding(fail);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/elements");
    cob.labelBinding(ok);
  }

  /// Shared loop logic: iterates array elements and validates each.
  /// `prefix` is a compile-time string like "instPath/".
  private static void emitLoop(CodeBuilder cob, JtdSchema.ElementsSchema e,
                               int instSlot, int errSlot,
                               String prefix, String childSchemaPath) {
    cob.aload(instSlot);
    cob.checkcast(CD_JsonArray);
    cob.invokeinterface(CD_JsonArray, "elements", MTD_List);
    int listSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(listSlot);

    cob.aload(listSlot);
    cob.invokeinterface(CD_List, "size", MTD_int);
    int sizeSlot = cob.allocateLocal(TypeKind.INT);
    cob.istore(sizeSlot);

    int iSlot = cob.allocateLocal(TypeKind.INT);
    cob.iconst_0();
    cob.istore(iSlot);

    cob.ldc(prefix);
    int prefixSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(prefixSlot);

    var loopStart = cob.newLabel();
    var loopEnd = cob.newLabel();

    cob.labelBinding(loopStart);
    cob.iload(iSlot);
    cob.iload(sizeSlot);
    cob.if_icmpge(loopEnd);

    cob.aload(listSlot);
    cob.iload(iSlot);
    cob.invokeinterface(CD_List, "get", MTD_Object_int);
    cob.checkcast(CD_JsonValue);
    int elemSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(elemSlot);

    EmitNode.emitDeferredIdx(cob, e.elements(), elemSlot, errSlot, prefixSlot, iSlot, childSchemaPath);

    cob.iinc(iSlot, 1);
    cob.goto_(loopStart);

    cob.labelBinding(loopEnd);
  }

  /// Loop with dynamic prefix: prefix comes from a local variable.
  private static void emitLoopDynamic(CodeBuilder cob, JtdSchema.ElementsSchema e,
                                      int instSlot, int errSlot,
                                      int prefixSlot, String childSchemaPath) {
    cob.aload(instSlot);
    cob.checkcast(CD_JsonArray);
    cob.invokeinterface(CD_JsonArray, "elements", MTD_List);
    int listSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(listSlot);

    cob.aload(listSlot);
    cob.invokeinterface(CD_List, "size", MTD_int);
    int sizeSlot = cob.allocateLocal(TypeKind.INT);
    cob.istore(sizeSlot);

    int iSlot = cob.allocateLocal(TypeKind.INT);
    cob.iconst_0();
    cob.istore(iSlot);

    var loopStart = cob.newLabel();
    var loopEnd = cob.newLabel();

    cob.labelBinding(loopStart);
    cob.iload(iSlot);
    cob.iload(sizeSlot);
    cob.if_icmpge(loopEnd);

    cob.aload(listSlot);
    cob.iload(iSlot);
    cob.invokeinterface(CD_List, "get", MTD_Object_int);
    cob.checkcast(CD_JsonValue);
    int elemSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(elemSlot);

    EmitNode.emitDeferredIdx(cob, e.elements(), elemSlot, errSlot, prefixSlot, iSlot, childSchemaPath);

    cob.iinc(iSlot, 1);
    cob.goto_(loopStart);

    cob.labelBinding(loopEnd);
  }
}
