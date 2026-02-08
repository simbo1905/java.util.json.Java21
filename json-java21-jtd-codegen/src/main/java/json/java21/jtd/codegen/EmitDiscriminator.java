package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ConstantDescs;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Discriminator schema (tagged union).
final class EmitDiscriminator {

  private EmitDiscriminator() {}

  static void emit(CodeBuilder cob, JtdSchema.DiscriminatorSchema d,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath) {
    var end = cob.newLabel();

    // Step 1: must be object
    var step1Fail = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(step1Fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    // Step 2: tag must exist
    var step2Fail = cob.newLabel();
    cob.aload(mapSlot);
    cob.ldc(d.discriminator());
    cob.invokeinterface(CD_Map, "containsKey", MTD_boolean_Object);
    cob.ifeq(step2Fail);

    cob.aload(mapSlot);
    cob.ldc(d.discriminator());
    cob.invokeinterface(CD_Map, "get", MTD_Object_Object);
    cob.checkcast(CD_JsonValue);
    int tagValSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagValSlot);

    // Step 3: tag must be string
    var step3Fail = cob.newLabel();
    cob.aload(tagValSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifeq(step3Fail);

    cob.aload(tagValSlot);
    cob.checkcast(CD_JsonString);
    cob.invokeinterface(CD_JsonString, "string", MTD_String);
    int tagStrSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagStrSlot);

    // Step 4: dispatch to variants
    for (final var entry : d.mapping().entrySet()) {
      final var tagValue = entry.getKey();
      final var variantSchema = entry.getValue();
      var nextVariant = cob.newLabel();

      cob.aload(tagStrSlot);
      cob.ldc(tagValue);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifeq(nextVariant);

      if (variantSchema instanceof JtdSchema.PropertiesSchema props) {
        EmitProperties.emit(cob, props, instSlot, errSlot, instPath,
            schemaPath + "/mapping/" + tagValue, d.discriminator());
      } else {
        EmitNode.emit(cob, variantSchema, instSlot, errSlot, instPath,
            schemaPath + "/mapping/" + tagValue);
      }
      cob.goto_(end);

      cob.labelBinding(nextVariant);
    }

    // Step 5: tag not in mapping
    EmitError.addError(cob, errSlot,
        instPath + "/" + d.discriminator(), schemaPath + "/mapping");
    cob.goto_(end);

    // Error paths
    cob.labelBinding(step1Fail);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/discriminator");
    cob.goto_(end);

    cob.labelBinding(step2Fail);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/discriminator");
    cob.goto_(end);

    cob.labelBinding(step3Fail);
    EmitError.addError(cob, errSlot,
        instPath + "/" + d.discriminator(), schemaPath + "/discriminator");

    cob.labelBinding(end);
  }

  /// Dynamic-path variant: parent instancePath from local variable.
  static void emitDynamic(CodeBuilder cob, JtdSchema.DiscriminatorSchema d,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath) {
    var end = cob.newLabel();

    var step1Fail = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonObject);
    cob.ifeq(step1Fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonObject);
    cob.invokeinterface(CD_JsonObject, "members", MTD_Map);
    int mapSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(mapSlot);

    var step2Fail = cob.newLabel();
    cob.aload(mapSlot);
    cob.ldc(d.discriminator());
    cob.invokeinterface(CD_Map, "containsKey", MTD_boolean_Object);
    cob.ifeq(step2Fail);

    cob.aload(mapSlot);
    cob.ldc(d.discriminator());
    cob.invokeinterface(CD_Map, "get", MTD_Object_Object);
    cob.checkcast(CD_JsonValue);
    int tagValSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagValSlot);

    var step3Fail = cob.newLabel();
    cob.aload(tagValSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifeq(step3Fail);

    cob.aload(tagValSlot);
    cob.checkcast(CD_JsonString);
    cob.invokeinterface(CD_JsonString, "string", MTD_String);
    int tagStrSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagStrSlot);

    for (final var entry : d.mapping().entrySet()) {
      final var tagValue = entry.getKey();
      final var variantSchema = entry.getValue();
      var nextVariant = cob.newLabel();

      cob.aload(tagStrSlot);
      cob.ldc(tagValue);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifeq(nextVariant);

      if (variantSchema instanceof JtdSchema.PropertiesSchema props) {
        EmitProperties.emitDynamic(cob, props, instSlot, errSlot, pathSlot,
            schemaPath + "/mapping/" + tagValue, d.discriminator());
      } else {
        EmitNode.emitDynamic(cob, variantSchema, instSlot, errSlot, pathSlot,
            schemaPath + "/mapping/" + tagValue);
      }
      cob.goto_(end);

      cob.labelBinding(nextVariant);
    }

    // tag not in mapping: error at pathSlot + "/" + discriminator
    cob.aload(pathSlot);
    cob.ldc("/" + d.discriminator());
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int tagPathSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagPathSlot);
    EmitError.addErrorDynamic(cob, errSlot, tagPathSlot, schemaPath + "/mapping");
    cob.goto_(end);

    cob.labelBinding(step1Fail);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/discriminator");
    cob.goto_(end);

    cob.labelBinding(step2Fail);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/discriminator");
    cob.goto_(end);

    cob.labelBinding(step3Fail);
    cob.aload(pathSlot);
    cob.ldc("/" + d.discriminator());
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int tagPath2Slot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(tagPath2Slot);
    EmitError.addErrorDynamic(cob, errSlot, tagPath2Slot, schemaPath + "/discriminator");

    cob.labelBinding(end);
  }
}
