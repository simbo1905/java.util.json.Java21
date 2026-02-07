/// Example test for simple boolean schema
/// Tests that the generated validator correctly validates boolean values

import { test, assertEq, assertArrayEq, runTests } from './test-runner.js';
import { validate } from '../resources/expected/boolean-schema.js';

test('validate returns empty array for true', () => {
    const errors = validate(true);
    assertArrayEq(errors, [], 'Should have no errors for boolean true');
});

test('validate returns empty array for false', () => {
    const errors = validate(false);
    assertArrayEq(errors, [], 'Should have no errors for boolean false');
});

test('validate returns error for string', () => {
    const errors = validate('hello');
    assertEq(errors.length, 1, 'Should have one error');
    assertEq(errors[0].instancePath, '', 'instancePath should be empty');
    assertEq(errors[0].schemaPath, '/type', 'schemaPath should be /type');
});

test('validate returns error for number', () => {
    const errors = validate(42);
    assertEq(errors.length, 1, 'Should have one error');
});

test('validate returns error for null', () => {
    const errors = validate(null);
    assertEq(errors.length, 1, 'Should have one error');
});

test('validate returns error for object', () => {
    const errors = validate({});
    assertEq(errors.length, 1, 'Should have one error');
});

test('validate returns error for array', () => {
    const errors = validate([]);
    assertEq(errors.length, 1, 'Should have one error');
});

runTests();
