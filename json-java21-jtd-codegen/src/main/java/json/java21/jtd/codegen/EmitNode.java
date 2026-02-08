package json.java21.jtd.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ConstantDescs;
import java.util.logging.Logger;

import json.java21.jtd.JtdSchema;

import static json.java21.jtd.codegen.Descriptors.*;

/// Dispatches AST node emission to the appropriate emitter.
///
/// This is the central routing point: each [JtdSchema] variant maps to
/// a specific emitter class. Static-path variants bake the instance path
/// as a string constant; dynamic-path variants read it from a local variable.
final class EmitNode {

  private static final Logger LOG = Logger.getLogger(EmitNode.class.getName());

  private EmitNode() {}

  /// Emit bytecode for an AST node where instancePath is a compile-time constant.
  static void emit(CodeBuilder cob, JtdSchema node,
                   int instSlot, int errSlot,
                   String instPath, String schemaPath) {
    switch (node) {
      case JtdSchema.EmptySchema e -> { /* no check */ }
      case JtdSchema.NullableSchema n ->
          emitNullable(cob, n, instSlot, errSlot, instPath, schemaPath);
      case JtdSchema.RefSchema r ->
          emit(cob, r.target(), instSlot, errSlot, instPath, "/definitions/" + r.ref());
      case JtdSchema.TypeSchema t ->
          emitType(cob, t, instSlot, errSlot, instPath, schemaPath);
      case JtdSchema.EnumSchema e ->
          EmitEnum.emit(cob, e, instSlot, errSlot, instPath, schemaPath);
      case JtdSchema.ElementsSchema e ->
          EmitElements.emit(cob, e, instSlot, errSlot, instPath, schemaPath);
      case JtdSchema.PropertiesSchema p ->
          EmitProperties.emit(cob, p, instSlot, errSlot, instPath, schemaPath, null);
      case JtdSchema.ValuesSchema v ->
          EmitValues.emit(cob, v, instSlot, errSlot, instPath, schemaPath);
      case JtdSchema.DiscriminatorSchema d ->
          EmitDiscriminator.emit(cob, d, instSlot, errSlot, instPath, schemaPath);
    }
  }

  /// Emit bytecode for an AST node where instancePath is in a local variable.
  static void emitDynamic(CodeBuilder cob, JtdSchema node,
                          int instSlot, int errSlot,
                          int pathSlot, String schemaPath) {
    switch (node) {
      case JtdSchema.EmptySchema e -> { /* no check */ }
      case JtdSchema.NullableSchema n -> {
        var afterNull = cob.newLabel();
        cob.aload(instSlot);
        cob.instanceOf(CD_JsonNull);
        cob.ifne(afterNull);
        emitDynamic(cob, n.wrapped(), instSlot, errSlot, pathSlot, schemaPath);
        cob.labelBinding(afterNull);
      }
      case JtdSchema.RefSchema r ->
          emitDynamic(cob, r.target(), instSlot, errSlot, pathSlot, "/definitions/" + r.ref());
      case JtdSchema.TypeSchema t ->
          emitTypeDynamic(cob, t, instSlot, errSlot, pathSlot, schemaPath);
      case JtdSchema.EnumSchema e ->
          EmitEnum.emitDynamic(cob, e, instSlot, errSlot, pathSlot, schemaPath);
      case JtdSchema.ElementsSchema e ->
          EmitElements.emitDynamic(cob, e, instSlot, errSlot, pathSlot, schemaPath);
      case JtdSchema.PropertiesSchema p ->
          EmitProperties.emitDynamic(cob, p, instSlot, errSlot, pathSlot, schemaPath, null);
      case JtdSchema.ValuesSchema v ->
          EmitValues.emitDynamic(cob, v, instSlot, errSlot, pathSlot, schemaPath);
      case JtdSchema.DiscriminatorSchema d ->
          EmitDiscriminator.emitDynamic(cob, d, instSlot, errSlot, pathSlot, schemaPath);
    }
  }

  private static void emitNullable(CodeBuilder cob, JtdSchema.NullableSchema n,
                                   int instSlot, int errSlot,
                                   String instPath, String schemaPath) {
    var afterNull = cob.newLabel();
    cob.aload(instSlot);
    cob.instanceOf(CD_JsonNull);
    cob.ifne(afterNull);
    emit(cob, n.wrapped(), instSlot, errSlot, instPath, schemaPath);
    cob.labelBinding(afterNull);
  }

  private static void emitType(CodeBuilder cob, JtdSchema.TypeSchema t,
                                int instSlot, int errSlot,
                                String instPath, String schemaPath) {
    switch (t.type()) {
      case "boolean" -> EmitType.emitBoolean(cob, instSlot, errSlot, instPath, schemaPath);
      case "string" -> EmitType.emitString(cob, instSlot, errSlot, instPath, schemaPath);
      case "float32", "float64" -> EmitType.emitFloat(cob, instSlot, errSlot, instPath, schemaPath);
      case "timestamp" -> EmitType.emitTimestamp(cob, instSlot, errSlot, instPath, schemaPath);
      default -> EmitType.emitInt(cob, t.type(), instSlot, errSlot, instPath, schemaPath);
    }
  }

  private static void emitTypeDynamic(CodeBuilder cob, JtdSchema.TypeSchema t,
                                       int instSlot, int errSlot,
                                       int pathSlot, String schemaPath) {
    switch (t.type()) {
      case "boolean" -> EmitType.emitBooleanDynamic(cob, instSlot, errSlot, pathSlot, schemaPath);
      case "string" -> EmitType.emitStringDynamic(cob, instSlot, errSlot, pathSlot, schemaPath);
      case "float32", "float64" -> EmitType.emitFloatDynamic(cob, instSlot, errSlot, pathSlot, schemaPath);
      default -> EmitType.emitIntDynamic(cob, t.type(), instSlot, errSlot, pathSlot, schemaPath);
    }
  }

  // ------------------------------------------------------------------
  // Deferred-path dispatchers: concat only on error for leaf schemas.
  // Containers materialize the path once, then delegate to emitDynamic.
  // ------------------------------------------------------------------

  /// Deferred string-segment variant: parentPathSlot + segmentStringSlot.
  static void emitDeferredStr(CodeBuilder cob, JtdSchema node,
                              int instSlot, int errSlot,
                              int parentPathSlot, int segmentSlot,
                              String schemaPath) {
    switch (node) {
      case JtdSchema.EmptySchema _ -> { /* no check */ }
      case JtdSchema.NullableSchema n -> {
        var afterNull = cob.newLabel();
        cob.aload(instSlot);
        cob.instanceOf(CD_JsonNull);
        cob.ifne(afterNull);
        emitDeferredStr(cob, n.wrapped(), instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
        cob.labelBinding(afterNull);
      }
      case JtdSchema.RefSchema r ->
          emitDeferredStr(cob, r.target(), instSlot, errSlot, parentPathSlot, segmentSlot, "/definitions/" + r.ref());
      case JtdSchema.TypeSchema t ->
          emitTypeDeferredStr(cob, t, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      case JtdSchema.EnumSchema e ->
          EmitEnum.emitDeferredStr(cob, e, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      case JtdSchema.ElementsSchema _,
           JtdSchema.PropertiesSchema _,
           JtdSchema.ValuesSchema _,
           JtdSchema.DiscriminatorSchema _ -> {
        int pathSlot = materializeStr(cob, parentPathSlot, segmentSlot);
        emitDynamic(cob, node, instSlot, errSlot, pathSlot, schemaPath);
      }
    }
  }

  /// Deferred index-segment variant: prefixSlot + indexIntSlot.
  static void emitDeferredIdx(CodeBuilder cob, JtdSchema node,
                              int instSlot, int errSlot,
                              int prefixSlot, int indexSlot,
                              String schemaPath) {
    switch (node) {
      case JtdSchema.EmptySchema _ -> { /* no check */ }
      case JtdSchema.NullableSchema n -> {
        var afterNull = cob.newLabel();
        cob.aload(instSlot);
        cob.instanceOf(CD_JsonNull);
        cob.ifne(afterNull);
        emitDeferredIdx(cob, n.wrapped(), instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
        cob.labelBinding(afterNull);
      }
      case JtdSchema.RefSchema r ->
          emitDeferredIdx(cob, r.target(), instSlot, errSlot, prefixSlot, indexSlot, "/definitions/" + r.ref());
      case JtdSchema.TypeSchema t ->
          emitTypeDeferredIdx(cob, t, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      case JtdSchema.EnumSchema e ->
          EmitEnum.emitDeferredIdx(cob, e, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      case JtdSchema.ElementsSchema _,
           JtdSchema.PropertiesSchema _,
           JtdSchema.ValuesSchema _,
           JtdSchema.DiscriminatorSchema _ -> {
        int pathSlot = materializeIdx(cob, prefixSlot, indexSlot);
        emitDynamic(cob, node, instSlot, errSlot, pathSlot, schemaPath);
      }
    }
  }

  private static void emitTypeDeferredStr(CodeBuilder cob, JtdSchema.TypeSchema t,
                                          int instSlot, int errSlot,
                                          int parentPathSlot, int segmentSlot,
                                          String schemaPath) {
    switch (t.type()) {
      case "boolean" -> EmitType.emitBooleanDeferredStr(cob, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      case "string" -> EmitType.emitStringDeferredStr(cob, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      case "float32", "float64" -> EmitType.emitFloatDeferredStr(cob, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      case "timestamp" -> EmitType.emitTimestampDeferredStr(cob, instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
      default -> EmitType.emitIntDeferredStr(cob, t.type(), instSlot, errSlot, parentPathSlot, segmentSlot, schemaPath);
    }
  }

  private static void emitTypeDeferredIdx(CodeBuilder cob, JtdSchema.TypeSchema t,
                                          int instSlot, int errSlot,
                                          int prefixSlot, int indexSlot,
                                          String schemaPath) {
    switch (t.type()) {
      case "boolean" -> EmitType.emitBooleanDeferredIdx(cob, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      case "string" -> EmitType.emitStringDeferredIdx(cob, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      case "float32", "float64" -> EmitType.emitFloatDeferredIdx(cob, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      case "timestamp" -> EmitType.emitTimestampDeferredIdx(cob, instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
      default -> EmitType.emitIntDeferredIdx(cob, t.type(), instSlot, errSlot, prefixSlot, indexSlot, schemaPath);
    }
  }

  /// Materialize: parentPath.concat(segment) → new local slot.
  private static int materializeStr(CodeBuilder cob, int parentPathSlot, int segmentSlot) {
    cob.aload(parentPathSlot);
    cob.aload(segmentSlot);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int slot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(slot);
    return slot;
  }

  /// Materialize: prefix.concat(String.valueOf(index)) → new local slot.
  private static int materializeIdx(CodeBuilder cob, int prefixSlot, int indexSlot) {
    cob.aload(prefixSlot);
    cob.iload(indexSlot);
    cob.invokestatic(CD_String, "valueOf", MTD_String_int);
    cob.invokevirtual(CD_String, "concat", MTD_String_String);
    int slot = cob.allocateLocal(TypeKind.REFERENCE);
    cob.astore(slot);
    return slot;
  }
}
