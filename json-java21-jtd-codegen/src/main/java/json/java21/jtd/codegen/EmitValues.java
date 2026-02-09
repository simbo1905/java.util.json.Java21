package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Values schema (object with homogeneous values).
final class EmitValues {

  private EmitValues() {}

  static void emit(CodeBuilder cob, JtdSchema.ValuesSchema v,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(fail);

    emitLoop(cob, v, instSlot, errSlot, instPath + "/", schemaPath + "/values");
    cob.goto_(ok);

    cob.labelBinding(fail);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/values");
    cob.labelBinding(ok);
  }

  /// Dynamic-path variant: parent instancePath from local variable.
  static void emitDynamic(CodeBuilder cob, JtdSchema.ValuesSchema v,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(fail);

    // Build prefix: parentPath + "/"
    cob.aload(pathSlot);
    cob.ldc("/");
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int prefixSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(prefixSlot);

    emitLoopDynamic(cob, v, instSlot, errSlot, prefixSlot, schemaPath + "/values");
    cob.goto_(ok);

    cob.labelBinding(fail);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/values");
    cob.labelBinding(ok);
  }

  private static void emitLoop(CodeBuilder cob, JtdSchema.ValuesSchema v,
                               int instSlot, int errSlot,
                               String prefix, String childSchemaPath) {
    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    emitEntryLoop(cob, v, mapSlot, errSlot, prefix, childSchemaPath, false, -1);
  }

  private static void emitLoopDynamic(CodeBuilder cob, JtdSchema.ValuesSchema v,
                                      int instSlot, int errSlot,
                                      int prefixSlot, String childSchemaPath) {
    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    emitEntryLoop(cob, v, mapSlot, errSlot, null, childSchemaPath, true, prefixSlot);
  }

  private static void emitEntryLoop(CodeBuilder cob, JtdSchema.ValuesSchema v,
                                    int mapSlot, int errSlot,
                                    String staticPrefix, String childSchemaPath,
                                    boolean dynamic, int prefixSlot) {
    if (!dynamic) {
      cob.ldc(staticPrefix);
      prefixSlot = cob.allocateLocal(TypeKind.REFERENCE);
      cob.astore(prefixSlot);
    }

    cob.aload(mapSlot);
    cob.invokeinterface(CD_Map, "entrySet", MTD_Set);
    cob.invokeinterface(CD_Set, "iterator", MTD_Iterator);
    int iterSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(iterSlot);

    var loopStart = cob.newLabel();
    var loopEnd = cob.newLabel();

    cob.labelBinding(loopStart);
    cob.aload(iterSlot);
    cob.invokeinterface(CD_Iterator, "hasNext", MTD_boolean);
    cob.ifeq(loopEnd);

    cob.aload(iterSlot);
    cob.invokeinterface(CD_Iterator, "next", MTD_Object);
    cob.checkcast(CD_MapEntry);
    int entrySlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(entrySlot);

    cob.aload(entrySlot);
    cob.invokeinterface(CD_MapEntry, "getKey", MTD_Object);
    cob.checkcast(CD_String);
    int keySlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(keySlot);

    cob.aload(entrySlot);
    cob.invokeinterface(CD_MapEntry, "getValue", MTD_Object);
    cob.checkcast(CD_JsonValue);
    int valSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(valSlot);

    EmitNode.emitDeferredStr(cob, v.values(), valSlot, errSlot, prefixSlot, keySlot, childSchemaPath);

    cob.goto_(loopStart);
    cob.labelBinding(loopEnd);
  }
}
