package json.java21.jtd.codegen;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Emits the class skeleton: constructor, toString, and the validate method shell.
///
/// The validate method delegates to [EmitNode.emit] for the actual schema logic.
final class EmitScaffold {

  private EmitScaffold() {}

  static void emitConstructor(ClassBuilder clb, ClassDesc self) {
    clb.withMethodBody(ConstantDescs.INIT_NAME,
        MTD_void_String,
        ClassFile.ACC_PUBLIC,
        cob -> {
          cob.aload(0);
          cob.invokespecial(CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
          cob.aload(0);
          cob.aload(1);
          cob.putfield(self, "schemaJson", CD_String);
          cob.return_();
        });
  }

  static void emitToString(ClassBuilder clb, ClassDesc self) {
    clb.withMethodBody("toString",
        MTD_String,
        ClassFile.ACC_PUBLIC,
        cob -> {
          cob.aload(0);
          cob.getfield(self, "schemaJson", CD_String);
          cob.areturn();
        });
  }

  /// Emits: public JtdValidationResult validate(JsonValue instance)
  ///
  /// Layout: local 0 = this, local 1 = instance, local 2 = errors (ArrayList)
  static void emitValidateMethod(ClassBuilder clb, ClassDesc self, JtdSchema schema) {
    clb.withMethodBody("validate",
        MTD_JtdValidationResult_JsonValue,
        ClassFile.ACC_PUBLIC,
        cob -> {
          // local 0 = this, local 1 = instance
          // allocate local 2 = errors (ArrayList)
          int errSlot = cob.allocateLocal(TypeKind.REFERENCE);
          cob.new_(CD_ArrayList);
          cob.dup();
          cob.invokespecial(CD_ArrayList, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
          cob.astore(errSlot);

          // Emit the root schema validation
          EmitNode.emit(cob, schema, 1, errSlot, "", "");

          // Build result: errors.isEmpty() ? success() : failure(errors)
          cob.aload(errSlot);
          cob.invokevirtual(CD_ArrayList, "isEmpty", MTD_boolean);
          var failLabel = cob.newLabel();
          cob.ifeq(failLabel);

          cob.invokestatic(CD_JtdValidationResult, "success", MTD_JtdValidationResult);
          cob.areturn();

          cob.labelBinding(failLabel);
          cob.aload(errSlot);
          cob.invokestatic(CD_JtdValidationResult, "failure", MTD_JtdValidationResult_List);
          cob.areturn();
        });
  }
}
