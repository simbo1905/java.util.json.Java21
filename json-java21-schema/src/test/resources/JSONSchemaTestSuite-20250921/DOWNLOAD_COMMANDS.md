# Download Commands

The test data in this directory was downloaded and extracted using the following commands on 2025-09-21:

```bash
# Download JSON Schema Test Suite from GitHub
curl -L -o json-schema-test-suite.zip https://github.com/json-schema-org/JSON-Schema-Test-Suite/archive/refs/tags/23.1.0.zip

# Extract and organize files
unzip -q json-schema-test-suite.zip
cp -r "JSON-Schema-Test-Suite-23.1.0"/. . 2>/dev/null || true
rm -rf "JSON-Schema-Test-Suite-23.1.0" json-schema-test-suite.zip
```

**Source**: https://github.com/json-schema-org/JSON-Schema-Test-Suite  
**Tag**: 23.1.0  
**Download Date**: 2025-09-21