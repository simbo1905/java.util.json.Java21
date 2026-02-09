/// boolean-schema.test.js - JUnit JS test for the boolean type validator
/// Runs via junit-js JSRunner (GraalVM polyglot, no bun/node required)
///
/// Tests that the generated boolean validator correctly accepts booleans
/// and rejects all other JSON value types.

// Load the expected fixture, stripping ESM export keywords for plain eval
var Files = Java.type('java.nio.file.Files');
var Paths = Java.type('java.nio.file.Paths');
var fixtureContent = Files.readString(
    Paths.get('src/test/resources/expected/boolean-schema.js')
);
// Strip 'export ' prefix so the function is declared in global scope
eval(fixtureContent.replace(/^export /gm, ''));

tests({
    validateReturnEmptyArrayForTrue: function() {
        var errors = validate(true);
        assert.assertEquals(0, errors.length);
    },

    validateReturnEmptyArrayForFalse: function() {
        var errors = validate(false);
        assert.assertEquals(0, errors.length);
    },

    validateReturnErrorForString: function() {
        var errors = validate('hello');
        assert.assertEquals(1, errors.length);
        assert.assertEquals('', errors[0].instancePath);
        assert.assertEquals('/type', errors[0].schemaPath);
    },

    validateReturnErrorForNumber: function() {
        var errors = validate(42);
        assert.assertEquals(1, errors.length);
    },

    validateReturnErrorForNull: function() {
        var errors = validate(null);
        assert.assertEquals(1, errors.length);
    },

    validateReturnErrorForObject: function() {
        var errors = validate({});
        assert.assertEquals(1, errors.length);
    },

    validateReturnErrorForArray: function() {
        var errors = validate([]);
        assert.assertEquals(1, errors.length);
    }
});
