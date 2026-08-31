// boolean-schema.js
// Generated from JTD schema: boolean-schema
// SHA-256: (test fixture)

export function validate(instance) {
    const errors = [];
    const instancePath = "";
    
    // Type check for boolean
    if (typeof instance !== "boolean") {
        errors.push({ instancePath: "", schemaPath: "/type" });
    }
    
    return errors;
}
