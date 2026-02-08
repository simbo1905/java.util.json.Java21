package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Enum schema checks.
///
/// Checks: instance is a JsonString whose value is in the enum's value set.
final class EmitEnum {

  private EmitEnum() {}

  static void emit(CodeBuilder cob, JtdSchema.EnumSchema e,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath) {
    emitEnumCore(cob, e, instSlot, errSlot, schemaPath,
        (c, es, sp) -> EmitError.addError(c, es, instPath, sp));
  }

  static void emitDynamic(CodeBuilder cob, JtdSchema.EnumSchema e,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath) {
    emitEnumCore(cob, e, instSlot, errSlot, schemaPath,
        (c, es, sp) -> EmitError.addErrorDynamic(c, es, pathSlot, sp));
  }

  static void emitDeferredStr(CodeBuilder cob, JtdSchema.EnumSchema e,
                              int instSlot, int errSlot,
                              int parentPathSlot, int segmentSlot,
                              String schemaPath) {
    emitEnumCore(cob, e, instSlot, errSlot, schemaPath,
        (c, es, sp) -> EmitError.addErrorDeferred(c, es, parentPathSlot, segmentSlot, sp));
  }

  static void emitDeferredIdx(CodeBuilder cob, JtdSchema.EnumSchema e,
                              int instSlot, int errSlot,
                              int prefixSlot, int indexSlot,
                              String schemaPath) {
    emitEnumCore(cob, e, instSlot, errSlot, schemaPath,
        (c, es, sp) -> EmitError.addErrorDeferredIdx(c, es, prefixSlot, indexSlot, sp));
  }

  private static void emitEnumCore(CodeBuilder cob, JtdSchema.EnumSchema e,
                                   int instSlot, int errSlot,
                                   String schemaPath,
                                   EmitType.ErrorEmitter onError) {
    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifeq(fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonString);
    cob.invokeinterface(CD_JsonString, "string", MTD_String);
    int strSlot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(strSlot);

    for (final var val : e.values()) {
      cob.aload(strSlot);
      cob.ldc(val);
      cob.invokevirtual(CD_String, "equals", MTD_boolean_Object);
      cob.ifne(ok);
    }

    cob.labelBinding(fail);
    onError.emit(cob, errSlot, schemaPath + "/enum");
    cob.labelBinding(ok);
  }
}
