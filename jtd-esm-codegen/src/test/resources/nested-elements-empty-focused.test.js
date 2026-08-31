/// Focused test for nested elements with empty schema
/// Schema: elements[elements[empty]]
/// This verifies the fix for "validate_inline_X is not defined" error

var Files = Java.type('java.nio.file.Files');
var Paths = Java.type('java.nio.file.Paths');
var StandardCharsets = Java.type('java.nio.charset.StandardCharsets');
var JtdToEsmCli = Java.type('io.github.simbo1905.json.jtd.codegen.JtdToEsmCli');

function generateValidator(schemaJson) {
    var tempDir = Files.createTempDirectory('jtd-esm-test-');
    var schemaFile = tempDir.resolve('schema.json');
    Files.writeString(schemaFile, schemaJson, StandardCharsets.UTF_8);
    var outJs = JtdToEsmCli.run(schemaFile, tempDir);
    var jsContent = Files.readString(outJs, StandardCharsets.UTF_8);
    
    // Cleanup
    try {
        Files.walk(tempDir).sorted(function(a, b) { return -1; }).forEach(function(p) {
            try { Files.deleteIfExists(p); } catch (e) {}
        });
    } catch (e) {}
    
    return jsContent;
}

tests({
    nestedElementsWithEmptySchemaGeneratesInlineValidators: function() {
        var schemaJson = '{"elements":{"elements":{}}}';
        var jsContent = generateValidator(schemaJson);
        
        // Should reference validate_inline_0 for inner elements
        var hasInlineRef = jsContent.indexOf('validate_inline_0') !== -1;
        // Should define validate_inline_0 function
        var hasInlineDef = jsContent.indexOf('function validate_inline_0') !== -1;
        
        assert.assertTrue('Generated JS should reference inline validator', hasInlineRef);
        assert.assertTrue('Generated JS should define inline validator', hasInlineDef);
    },
    
    tripleNestedElementsGeneratesMultipleInlineValidators: function() {
        var schemaJson = '{"elements":{"elements":{"elements":{}}}}';
        var jsContent = generateValidator(schemaJson);
        
        // Should have validate_inline_0 and validate_inline_1
        var hasInline0 = jsContent.indexOf('function validate_inline_0') !== -1;
        var hasInline1 = jsContent.indexOf('function validate_inline_1') !== -1;
        
        assert.assertTrue('Should generate validate_inline_0', hasInline0);
        assert.assertTrue('Should generate validate_inline_1', hasInline1);
    },
    
    generatedJavaScriptIsValid: function() {
        var schemaJson = '{"elements":{"elements":{}}}';
        var jsContent = generateValidator(schemaJson);
        
        // Strip export and check syntax
        try {
            var testJs = jsContent.replace(/^export /gm, '');
            eval(testJs);
            assert.assertTrue(true);
        } catch (e) {
            assert.fail('Generated JS has syntax error: ' + e.message);
        }
    }
});
