package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Properties schema (object validation).
///
/// Two variants:
/// - [#emit]: instance path is a compile-time constant.
/// - [#emitDynamic]: instance path is in a local variable (used inside loops).
final class EmitProperties {

  private EmitProperties() {}

  static void emit(CodeBuilder cob, JtdSchema.PropertiesSchema p,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath,
                   String discriminatorTag) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    for (final var entry : p.properties().entrySet()) {
      final var key = entry.getKey();
      var present = cob.newLabel();
      cob.aload(mapSlot);
      cob.ldc(key);
      cob.invokeinterface(CD_Map, "containsKey", MTD_boolean_Object);
      cob.ifne(present);
      EmitError.addError(cob, errSlot, instPath, schemaPath + "/properties/" + key);
      cob.labelBinding(present);
    }

    if (!p.additionalProperties()) {
      emitAdditionalCheck(cob, p, mapSlot, errSlot, instPath, schemaPath, discriminatorTag);
    }

    for (final var entry : p.properties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discriminatorTag)) continue;
      emitPropertyChild(cob, entry.getValue(), mapSlot, errSlot, key,
          instPath + "/" + key, schemaPath + "/properties/" + key);
    }

    for (final var entry : p.optionalProperties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discriminatorTag)) continue;
      emitPropertyChild(cob, entry.getValue(), mapSlot, errSlot, key,
          instPath + "/" + key, schemaPath + "/optionalProperties/" + key);
    }

    cob.goto_(ok);

    cob.labelBinding(fail);
    final var guardSuffix = p.properties().isEmpty() ? "/optionalProperties" : "/properties";
    EmitError.addError(cob, errSlot, instPath, schemaPath + guardSuffix);
    cob.labelBinding(ok);
  }

  /// Dynamic-path variant: parent instancePath comes from a local variable.
  static void emitDynamic(CodeBuilder cob, JtdSchema.PropertiesSchema p,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath,
                          String discriminatorTag) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    // Missing required keys: error path = pathSlot + "/properties/" + key
    for (final var entry : p.properties().entrySet()) {
      final var key = entry.getKey();
      var present = cob.newLabel();
      cob.aload(mapSlot);
      cob.ldc(key);
      cob.invokeinterface(CD_Map, "containsKey", MTD_boolean_Object);
      cob.ifne(present);
      EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/properties/" + key);
      cob.labelBinding(present);
    }

    if (!p.additionalProperties()) {
      emitAdditionalCheckDynamic(cob, p, mapSlot, errSlot, pathSlot, schemaPath, discriminatorTag);
    }

    // Child property values: build child path = pathSlot + "/" + key
    for (final var entry : p.properties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discriminatorTag)) continue;
      emitPropertyChildDynamic(cob, entry.getValue(), mapSlot, errSlot, pathSlot, key,
          schemaPath + "/properties/" + key);
    }

    for (final var entry : p.optionalProperties().entrySet()) {
      final var key = entry.getKey();
      if (key.equals(discriminatorTag)) continue;
      emitPropertyChildDynamic(cob, entry.getValue(), mapSlot, errSlot, pathSlot, key,
          schemaPath + "/optionalProperties/" + key);
    }

    cob.goto_(ok);

    cob.labelBinding(fail);
    final var dynGuardSuffix = p.properties().isEmpty() ? "/optionalProperties" : "/properties";
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + dynGuardSuffix);
    cob.labelBinding(ok);
  }

  private static void emitPropertyChild(CodeBuilder cob, JtdSchema childSchema,
                                        int mapSlot, int errSlot, String key,
                                        String childInstPath, String childSchemaPath) {
    var absent = cob.newLabel();
    var after = cob.newLabel();

    cob.aload(mapSlot);
    cob.ldc(key);
    cob.invokeinterface(CD_Map, "get", MTD_Object_Object);
    cob.dup();
    cob.ifnull(absent);
    cob.checkcast(CD_JsonValue);
    int childSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(childSlot);
    EmitNode.emit(cob, childSchema, childSlot, errSlot, childInstPath, childSchemaPath);
    cob.goto_(after);

    cob.labelBinding(absent);
    cob.pop();
    cob.labelBinding(after);
  }

  /// Dynamic child: uses deferred path for leaf schemas, materialized path for containers.
  private static void emitPropertyChildDynamic(CodeBuilder cob, JtdSchema childSchema,
                                               int mapSlot, int errSlot,
                                               int parentPathSlot, String key,
                                               String childSchemaPath) {
    var absent = cob.newLabel();
    var after = cob.newLabel();

    cob.aload(mapSlot);
    cob.ldc(key);
    cob.invokeinterface(CD_Map, "get", MTD_Object_Object);
    cob.dup();
    cob.ifnull(absent);
    cob.checkcast(CD_JsonValue);
    int childSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(childSlot);

    cob.ldc("/" + key);
    int segmentSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(segmentSlot);

    EmitNode.emitDeferredStr(cob, childSchema, childSlot, errSlot,
        parentPathSlot, segmentSlot, childSchemaPath);
    cob.goto_(after);

    cob.labelBinding(absent);
    cob.pop();
    cob.labelBinding(after);
  }

  private static void emitAdditionalCheck(CodeBuilder cob, JtdSchema.PropertiesSchema p,
                                          int mapSlot, int errSlot,
                                          String instPath, String schemaPath,
                                          String discriminatorTag) {
    cob.aload(mapSlot);
    cob.invokeinterface(CD_Map, "keySet", MTD_Set);
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
    cob.checkcast(CD_String);
    int keySlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(keySlot);

    var known = cob.newLabel();
    emitKnownKeyChecks(cob, p, keySlot, known, discriminatorTag);

    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.ldc(instPath + "/");
    cob.aload(keySlot);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, "<init>", MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();

    cob.labelBinding(known);
    cob.goto_(loopStart);

    cob.labelBinding(loopEnd);
  }

  /// Dynamic-path additional properties check: parent path from local variable.
  private static void emitAdditionalCheckDynamic(CodeBuilder cob, JtdSchema.PropertiesSchema p,
                                                 int mapSlot, int errSlot,
                                                 int parentPathSlot, String schemaPath,
                                                 String discriminatorTag) {
    cob.aload(mapSlot);
    cob.invokeinterface(CD_Map, "keySet", MTD_Set);
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
    cob.checkcast(CD_String);
    int keySlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(keySlot);

    var known = cob.newLabel();
    emitKnownKeyChecks(cob, p, keySlot, known, discriminatorTag);

    // Build error path: parentPath + "/" + key
    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.aload(parentPathSlot);
    cob.ldc("/");
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    cob.aload(keySlot);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, "<init>", MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();

    cob.labelBinding(known);
    cob.goto_(loopStart);

    cob.labelBinding(loopEnd);
  }

  private static void emitKnownKeyChecks(CodeBuilder cob, JtdSchema.PropertiesSchema p,
                                         int keySlot, Label known,
                                         String discriminatorTag) {
    for (final var k : p.properties().keySet()) {
      cob.aload(keySlot);
      cob.ldc(k);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifne(known);
    }
    for (final var k : p.optionalProperties().keySet()) {
      cob.aload(keySlot);
      cob.ldc(k);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifne(known);
    }
    if (discriminatorTag != null) {
      cob.aload(keySlot);
      cob.ldc(discriminatorTag);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifne(known);
    }
  }
}
