package json.java21.jsonpath.codegen;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/// Shared class descriptors and method type descriptors for bytecode emission.
///
/// All fields are compile-time constants referencing the types the generated
/// classfiles interact with at runtime (JSON API, collections, JDK stdlib).
final class Descriptors {

    private Descriptors() {}

    // -- JDK types --
    static final ClassDesc CD_Object = ConstantDescs.CD_Object;
    static final ClassDesc CD_String = ConstantDescs.CD_String;

    // -- Collections --
    static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
    static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    static final ClassDesc CD_MapEntry = ClassDesc.of("java.util.Map$Entry");
    static final ClassDesc CD_Set = ClassDesc.of("java.util.Set");
    static final ClassDesc CD_Iterator = ClassDesc.of("java.util.Iterator");
    static final ClassDesc CD_Collection = ClassDesc.of("java.util.Collection");

    // -- JSON API types --
    static final ClassDesc CD_JsonValue = ClassDesc.of("jdk.sandbox.java.util.json.JsonValue");
    static final ClassDesc CD_JsonObject = ClassDesc.of("jdk.sandbox.java.util.json.JsonObject");
    static final ClassDesc CD_JsonArray = ClassDesc.of("jdk.sandbox.java.util.json.JsonArray");
    static final ClassDesc CD_JsonString = ClassDesc.of("jdk.sandbox.java.util.json.JsonString");
    static final ClassDesc CD_JsonNumber = ClassDesc.of("jdk.sandbox.java.util.json.JsonNumber");
    static final ClassDesc CD_JsonBoolean = ClassDesc.of("jdk.sandbox.java.util.json.JsonBoolean");
    static final ClassDesc CD_JsonNull = ClassDesc.of("jdk.sandbox.java.util.json.JsonNull");

    // -- JsonPath codegen types --
    static final ClassDesc CD_CompiledJsonPath = ClassDesc.of("json.java21.jsonpath.codegen.CompiledJsonPath");
    static final ClassDesc CD_RecursiveDescentHelper = ClassDesc.of("json.java21.jsonpath.codegen.RecursiveDescentHelper");

    // -- Common method type descriptors --
    static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    static final MethodTypeDesc MTD_boolean = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    static final MethodTypeDesc MTD_int = MethodTypeDesc.of(ConstantDescs.CD_int);
    static final MethodTypeDesc MTD_double = MethodTypeDesc.of(ConstantDescs.CD_double);
    static final MethodTypeDesc MTD_long = MethodTypeDesc.of(ConstantDescs.CD_long);
    static final MethodTypeDesc MTD_boolean_Object = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object);
    static final MethodTypeDesc MTD_Object_Object = MethodTypeDesc.of(CD_Object, CD_Object);
    static final MethodTypeDesc MTD_Object_int = MethodTypeDesc.of(CD_Object, ConstantDescs.CD_int);
    static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(ConstantDescs.CD_void, CD_String);
    static final MethodTypeDesc MTD_Map = MethodTypeDesc.of(CD_Map);
    static final MethodTypeDesc MTD_List = MethodTypeDesc.of(CD_List);
    static final MethodTypeDesc MTD_List_JsonValue = MethodTypeDesc.of(CD_List, CD_JsonValue);
    static final MethodTypeDesc MTD_Set = MethodTypeDesc.of(CD_Set);
    static final MethodTypeDesc MTD_Iterator = MethodTypeDesc.of(CD_Iterator);
    static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    static final MethodTypeDesc MTD_String_String = MethodTypeDesc.of(CD_String, CD_String);
    static final MethodTypeDesc MTD_String_int = MethodTypeDesc.of(CD_String, ConstantDescs.CD_int);

    // -- RecursiveDescentHelper method descriptors --
    static final MethodTypeDesc MTD_void_JsonValue_String_List = MethodTypeDesc.of(
        ConstantDescs.CD_void, CD_JsonValue, CD_String, CD_List);
    static final MethodTypeDesc MTD_void_JsonValue_List = MethodTypeDesc.of(
        ConstantDescs.CD_void, CD_JsonValue, CD_List);
}
