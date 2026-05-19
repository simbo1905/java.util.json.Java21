package json.java21.jtd.codegen;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/// Shared class descriptors and method type descriptors for bytecode emission.
///
/// All fields are compile-time constants referencing the types the generated
/// classfiles interact with at runtime (JSON API, validation result types, JDK stdlib).
final class Descriptors {

  private Descriptors() {}

  // -- JDK types --
  static final ClassDesc CD_Object = ConstantDescs.CD_Object;
  static final ClassDesc CD_String = ConstantDescs.CD_String;
  static final ClassDesc CD_Math = ClassDesc.of("java.lang.Math");
  static final ClassDesc CD_CharSequence = ClassDesc.of("java.lang.CharSequence");
  static final ClassDesc CD_OffsetDateTime = ClassDesc.of("java.time.OffsetDateTime");
  static final ClassDesc CD_DateTimeFormatter = ClassDesc.of("java.time.format.DateTimeFormatter");
  static final ClassDesc CD_Pattern = ClassDesc.of("java.util.regex.Pattern");
  static final ClassDesc CD_Matcher = ClassDesc.of("java.util.regex.Matcher");

  // -- Collections --
  static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
  static final ClassDesc CD_List = ClassDesc.of("java.util.List");
  static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
  static final ClassDesc CD_MapEntry = ClassDesc.of("java.util.Map$Entry");
  static final ClassDesc CD_Set = ClassDesc.of("java.util.Set");
  static final ClassDesc CD_Iterator = ClassDesc.of("java.util.Iterator");

  // -- JSON API types --
  static final ClassDesc CD_JsonValue = ClassDesc.of("jdk.sandbox.java.util.json.JsonValue");
  static final ClassDesc CD_JsonObject = ClassDesc.of("jdk.sandbox.java.util.json.JsonObject");
  static final ClassDesc CD_JsonArray = ClassDesc.of("jdk.sandbox.java.util.json.JsonArray");
  static final ClassDesc CD_JsonString = ClassDesc.of("jdk.sandbox.java.util.json.JsonString");
  static final ClassDesc CD_JsonNumber = ClassDesc.of("jdk.sandbox.java.util.json.JsonNumber");
  static final ClassDesc CD_JsonBoolean = ClassDesc.of("jdk.sandbox.java.util.json.JsonBoolean");
  static final ClassDesc CD_JsonNull = ClassDesc.of("jdk.sandbox.java.util.json.JsonNull");

  // -- Validation result types --
  static final ClassDesc CD_JtdValidationError = ClassDesc.of("json.java21.jtd.JtdValidationError");
  static final ClassDesc CD_JtdValidationResult = ClassDesc.of("json.java21.jtd.JtdValidationResult");
  static final ClassDesc CD_JtdValidator = ClassDesc.of("json.java21.jtd.JtdValidator");

  // -- Common method type descriptors --
  static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
  static final MethodTypeDesc MTD_boolean = MethodTypeDesc.of(ConstantDescs.CD_boolean);
  static final MethodTypeDesc MTD_double = MethodTypeDesc.of(ConstantDescs.CD_double);
  static final MethodTypeDesc MTD_long = MethodTypeDesc.of(ConstantDescs.CD_long);
  static final MethodTypeDesc MTD_int = MethodTypeDesc.of(ConstantDescs.CD_int);
  static final MethodTypeDesc MTD_boolean_Object = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object);
  static final MethodTypeDesc MTD_Object_Object = MethodTypeDesc.of(CD_Object, CD_Object);
  static final MethodTypeDesc MTD_Object_int = MethodTypeDesc.of(CD_Object, ConstantDescs.CD_int);
  static final MethodTypeDesc MTD_boolean_CharSequence = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_CharSequence);
  static final MethodTypeDesc MTD_String_String = MethodTypeDesc.of(CD_String, CD_String);
  static final MethodTypeDesc MTD_String_int = MethodTypeDesc.of(CD_String, ConstantDescs.CD_int);
  static final MethodTypeDesc MTD_String_CharSeq_CharSeq = MethodTypeDesc.of(CD_String, CD_CharSequence, CD_CharSequence);
  static final MethodTypeDesc MTD_Map = MethodTypeDesc.of(CD_Map);
  static final MethodTypeDesc MTD_List = MethodTypeDesc.of(CD_List);
  static final MethodTypeDesc MTD_Set = MethodTypeDesc.of(CD_Set);
  static final MethodTypeDesc MTD_Iterator = MethodTypeDesc.of(CD_Iterator);
  static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
  static final MethodTypeDesc MTD_double_double = MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_double);
  static final MethodTypeDesc MTD_Pattern_String = MethodTypeDesc.of(CD_Pattern, CD_String);
  static final MethodTypeDesc MTD_Matcher_CharSequence = MethodTypeDesc.of(CD_Matcher, CD_CharSequence);
  static final MethodTypeDesc MTD_OffsetDateTime_CharSeq_DTF = MethodTypeDesc.of(CD_OffsetDateTime, CD_CharSequence, CD_DateTimeFormatter);
  static final MethodTypeDesc MTD_void_String_String = MethodTypeDesc.of(ConstantDescs.CD_void, CD_String, CD_String);
  static final MethodTypeDesc MTD_JtdValidationResult = MethodTypeDesc.of(CD_JtdValidationResult);
  static final MethodTypeDesc MTD_JtdValidationResult_List = MethodTypeDesc.of(CD_JtdValidationResult, CD_List);
  static final MethodTypeDesc MTD_JtdValidationResult_JsonValue = MethodTypeDesc.of(CD_JtdValidationResult, CD_JsonValue);
  static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(ConstantDescs.CD_void, CD_String);
}
