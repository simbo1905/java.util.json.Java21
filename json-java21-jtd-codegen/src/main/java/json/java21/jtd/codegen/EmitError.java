package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ConstantDescs;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits bytecode to add a [JtdValidationError] to the errors list.
///
/// Four variants:
/// - [#addError]: both instancePath and schemaPath are compile-time string constants.
/// - [#addErrorDynamic]: instancePath comes from a local variable slot (runtime string).
/// - [#addErrorDeferred]: instancePath is `parentPath.concat(segment)`, only materialized at error time.
/// - [#addErrorDeferredIdx]: instancePath is `prefix.concat(String.valueOf(index))`, only materialized at error time.
final class EmitError {

  private EmitError() {}

  /// Emit: errors.add(new JtdValidationError(instPath, schemaPath))
  /// Both paths are compile-time constants baked into the classfile.
  static void addError(CodeBuilder cob, int errSlot,
                       String instPath, String schemaPath) {
    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.ldc(instPath);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, ConstantDescs.INIT_NAME, MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();
  }

  /// Emit: errors.add(new JtdValidationError(localPathSlot, schemaPath))
  /// instancePath comes from a local variable, schemaPath is a constant.
  static void addErrorDynamic(CodeBuilder cob, int errSlot,
                              int pathSlot, String schemaPath) {
    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.aload(pathSlot);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, ConstantDescs.INIT_NAME, MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();
  }

  /// Deferred string-segment variant: instancePath = parentPath.concat(segment).
  /// Both parentPath and segment are local string variables; concat only happens here.
  static void addErrorDeferred(CodeBuilder cob, int errSlot,
                               int parentPathSlot, int segmentSlot,
                               String schemaPath) {
    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.aload(parentPathSlot);
    cob.aload(segmentSlot);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, ConstantDescs.INIT_NAME, MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();
  }

  /// Deferred index-segment variant: instancePath = prefix.concat(String.valueOf(index)).
  /// prefix is a local string variable, index is a local int; only materialized here.
  static void addErrorDeferredIdx(CodeBuilder cob, int errSlot,
                                  int prefixSlot, int indexSlot,
                                  String schemaPath) {
    cob.aload(errSlot);
    cob.new_(CD_JtdValidationError);
    cob.dup();
    cob.aload(prefixSlot);
    cob.iload(indexSlot);
    cob.invokestatic(CD_String, "valueOf", MTD_String_int);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    cob.ldc(schemaPath);
    cob.invokespecial(CD_JtdValidationError, ConstantDescs.INIT_NAME, MTD_void_String_String);
    cob.invokevirtual(CD_ArrayList, "add", MTD_boolean_Object);
    cob.pop();
  }
}
