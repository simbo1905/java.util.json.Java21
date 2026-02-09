package json.java21.jsonpath.codegen;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import json.java21.jsonpath.JsonPathAst;

import static json.java21.jsonpath.codegen.Descriptors.*;

/// Emits the class skeleton: constructor, toString, and the query method shell.
///
/// The query method delegates to [EmitSegments] for the actual evaluation logic.
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
                cob.putfield(self, "expression", CD_String);
                cob.return_();
            });
    }

    static void emitToString(ClassBuilder clb, ClassDesc self) {
        clb.withMethodBody("toString",
            MTD_String,
            ClassFile.ACC_PUBLIC,
            cob -> {
                cob.aload(0);
                cob.getfield(self, "expression", CD_String);
                cob.areturn();
            });
    }

    /// Emits: public List<JsonValue> query(JsonValue root)
    ///
    /// Layout: local 0 = this, local 1 = root, local 2 = results (ArrayList)
    static void emitQueryMethod(ClassBuilder clb, ClassDesc self, JsonPathAst.Root ast) {
        clb.withMethodBody("query",
            MTD_List_JsonValue,
            ClassFile.ACC_PUBLIC,
            cob -> {
                // local 0 = this, local 1 = root (input document)
                // allocate local 2 = results (ArrayList)
                int resultsSlot = cob.allocateLocal(TypeKind.REFERENCE);
                cob.new_(CD_ArrayList);
                cob.dup();
                cob.invokespecial(CD_ArrayList, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                cob.astore(resultsSlot);

                // Start evaluation: current = root (slot 1), segments from index 0
                // Emit the segment chain
                EmitSegments.emitSegmentChain(cob, ast.segments(), 0, 1, 1, resultsSlot);

                // Return results list
                cob.aload(resultsSlot);
                cob.areturn();
            });
    }
}
