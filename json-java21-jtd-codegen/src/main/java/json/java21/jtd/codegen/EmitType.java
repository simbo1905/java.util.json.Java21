package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ConstantDescs;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode for JTD Type schema checks (RFC 8927 §2.2.3).
///
/// Each method is independently testable: feed a small `{"type":"X"}` AST
/// fragment and verify the generated validator produces correct error pairs.
final class EmitType {

  private EmitType() {}

  /// Emit: if (!(instance instanceof JsonBoolean)) addError
  static void emitBoolean(CodeBuilder cob, int instSlot, int errSlot,
                          String instPath, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonBoolean);
    cob.ifne(ok);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  /// Emit: if (!(instance instanceof JsonString)) addError
  static void emitString(CodeBuilder cob, int instSlot, int errSlot,
                         String instPath, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifne(ok);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  /// Emit: if (!(instance instanceof JsonNumber)) addError
  static void emitFloat(CodeBuilder cob, int instSlot, int errSlot,
                        String instPath, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNumber);
    cob.ifne(ok);
    EmitError.addError(cob, errSlot, instPath, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  /// Emit integer range check for int8..uint32.
  ///
  /// Checks: instance is JsonNumber, zero fractional part, and in [min, max].
  static void emitInt(CodeBuilder cob, String type, int instSlot, int errSlot,
                      String instPath, String schemaPath) {
    emitIntCore(cob, type, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addError(c, e, instPath, sp));
  }

  /// Emit timestamp check: JsonString + RFC 3339 regex + OffsetDateTime.parse.
  static void emitTimestamp(CodeBuilder cob, int instSlot, int errSlot,
                            String instPath, String schemaPath) {
    emitTimestampCore(cob, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addError(c, e, instPath, sp));
  }

  // ------------------------------------------------------------------
  // Dynamic-path variants (materialized pathSlot)
  // ------------------------------------------------------------------

  static void emitBooleanDynamic(CodeBuilder cob, int instSlot, int errSlot,
                                 int pathSlot, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonBoolean);
    cob.ifne(ok);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitStringDynamic(CodeBuilder cob, int instSlot, int errSlot,
                                int pathSlot, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifne(ok);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitFloatDynamic(CodeBuilder cob, int instSlot, int errSlot,
                               int pathSlot, String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNumber);
    cob.ifne(ok);
    EmitError.addErrorDynamic(cob, errSlot, pathSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitIntDynamic(CodeBuilder cob, String type, int instSlot, int errSlot,
                             int pathSlot, String schemaPath) {
    emitIntCore(cob, type, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addErrorDynamic(c, e, pathSlot, sp));
  }

  // ------------------------------------------------------------------
  // Deferred string-segment variants (parentPathSlot + segmentSlot)
  // Concat only on error.
  // ------------------------------------------------------------------

  static void emitBooleanDeferredStr(CodeBuilder cob, int instSlot, int errSlot,
                                     int parentPathSlot, int segmentSlot,
                                     String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonBoolean);
    cob.ifne(ok);
    EmitError.addErrorDeferred(cob, errSlot, parentPathSlot, segmentSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitStringDeferredStr(CodeBuilder cob, int instSlot, int errSlot,
                                    int parentPathSlot, int segmentSlot,
                                    String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifne(ok);
    EmitError.addErrorDeferred(cob, errSlot, parentPathSlot, segmentSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitFloatDeferredStr(CodeBuilder cob, int instSlot, int errSlot,
                                   int parentPathSlot, int segmentSlot,
                                   String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNumber);
    cob.ifne(ok);
    EmitError.addErrorDeferred(cob, errSlot, parentPathSlot, segmentSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitIntDeferredStr(CodeBuilder cob, String type, int instSlot, int errSlot,
                                 int parentPathSlot, int segmentSlot,
                                 String schemaPath) {
    emitIntCore(cob, type, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addErrorDeferred(c, e, parentPathSlot, segmentSlot, sp));
  }

  static void emitTimestampDeferredStr(CodeBuilder cob, int instSlot, int errSlot,
                                       int parentPathSlot, int segmentSlot,
                                       String schemaPath) {
    emitTimestampCore(cob, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addErrorDeferred(c, e, parentPathSlot, segmentSlot, sp));
  }

  // ------------------------------------------------------------------
  // Deferred index-segment variants (prefixSlot + indexIntSlot)
  // Concat + valueOf only on error.
  // ------------------------------------------------------------------

  static void emitBooleanDeferredIdx(CodeBuilder cob, int instSlot, int errSlot,
                                     int prefixSlot, int indexSlot,
                                     String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonBoolean);
    cob.ifne(ok);
    EmitError.addErrorDeferredIdx(cob, errSlot, prefixSlot, indexSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitStringDeferredIdx(CodeBuilder cob, int instSlot, int errSlot,
                                    int prefixSlot, int indexSlot,
                                    String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonString);
    cob.ifne(ok);
    EmitError.addErrorDeferredIdx(cob, errSlot, prefixSlot, indexSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitFloatDeferredIdx(CodeBuilder cob, int instSlot, int errSlot,
                                   int prefixSlot, int indexSlot,
                                   String schemaPath) {
    var ok = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNumber);
    cob.ifne(ok);
    EmitError.addErrorDeferredIdx(cob, errSlot, prefixSlot, indexSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  static void emitIntDeferredIdx(CodeBuilder cob, String type, int instSlot, int errSlot,
                                 int prefixSlot, int indexSlot,
                                 String schemaPath) {
    emitIntCore(cob, type, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addErrorDeferredIdx(c, e, prefixSlot, indexSlot, sp));
  }

  static void emitTimestampDeferredIdx(CodeBuilder cob, int instSlot, int errSlot,
                                       int prefixSlot, int indexSlot,
                                       String schemaPath) {
    emitTimestampCore(cob, instSlot, errSlot, schemaPath,
        (c, e, sp) -> EmitError.addErrorDeferredIdx(c, e, prefixSlot, indexSlot, sp));
  }

  // ------------------------------------------------------------------
  // Shared core logic extracted to avoid duplication
  // ------------------------------------------------------------------

  @FunctionalInterface
  interface ErrorEmitter {
    void emit(CodeBuilder cob, int errSlot, String schemaPath);
  }

  private static void emitIntCore(CodeBuilder cob, String type,
                                  int instSlot, int errSlot,
                                  String schemaPath, ErrorEmitter onError) {
    long min, max;
    switch (type) {
      case "int8" -> { min = -128; max = 127; }
      case "uint8" -> { min = 0; max = 255; }
      case "int16" -> { min = -32768; max = 32767; }
      case "uint16" -> { min = 0; max = 65535; }
      case "int32" -> { min = Integer.MIN_VALUE; max = Integer.MAX_VALUE; }
      case "uint32" -> { min = 0; max = 4294967295L; }
      default -> throw new IllegalArgumentException("Unknown int type: " + type);
    }

    var ok = cob.newLabel();
    var fail = cob.newLabel();

    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNumber);
    cob.ifeq(fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonNumber);
    cob.invokeinterface(CD_JsonNumber, "toDouble", MTD_double);
    int dSlot = cob.allocateLocal(TypeKind.DOUBLE);
    cob.dstore(dSlot);

    cob.dload(dSlot);
    cob.dload(dSlot);
    cob.invokestatic(CD_Math, "floor", MTD_double_double);
    cob.dcmpl();
    cob.ifne(fail);

    cob.aload(instSlot);
    cob.checkcast(CD_JsonNumber);
    cob.invokeinterface(CD_JsonNumber, "toLong", MTD_long);
    int lSlot = cob.allocateLocal(TypeKind.LONG);
    cob.lstore(lSlot);

    cob.lload(lSlot);
    cob.ldc(min);
    cob.lcmp();
    cob.iflt(fail);

    cob.lload(lSlot);
    cob.ldc(max);
    cob.lcmp();
    cob.ifgt(fail);

    cob.goto_(ok);

    cob.labelBinding(fail);
    onError.emit(cob, errSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }

  private static void emitTimestampCore(CodeBuilder cob, int instSlot, int errSlot,
                                        String schemaPath, ErrorEmitter onError) {
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

    cob.ldc("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:(\\d{2}|60)(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2}))$");
    cob.invokestatic(CD_Pattern, "compile", MTD_Pattern_String);
    cob.aload(strSlot);
    cob.invokevirtual(CD_Pattern, "matcher", MTD_Matcher_CharSequence);
    cob.invokevirtual(CD_Matcher, "matches", MTD_boolean);
    cob.ifeq(fail);

    cob.aload(strSlot);
    cob.ldc(":60");
    cob.ldc(":59");
    cob.invokevirtual(CD_String, "replace", MTD_String_CharSeq_CharSeq);
    cob.getstatic(CD_DateTimeFormatter, "ISO_OFFSET_DATE_TIME", CD_DateTimeFormatter);
    cob.invokestatic(CD_OffsetDateTime, "parse", MTD_OffsetDateTime_CharSeq_DTF);
    cob.pop();
    cob.goto_(ok);

    cob.labelBinding(fail);
    onError.emit(cob, errSlot, schemaPath + "/type");
    cob.labelBinding(ok);
  }
}
