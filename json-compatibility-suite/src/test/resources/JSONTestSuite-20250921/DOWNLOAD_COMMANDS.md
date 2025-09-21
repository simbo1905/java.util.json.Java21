# Download Commands

The test data in this directory was downloaded and extracted using the following commands on 2025-09-21:

```bash
# Download JSON Test Suite from GitHub
curl -L -o json-test-suite.zip https://github.com/nst/JSONTestSuite/archive/refs/heads/master.zip

# Extract and organize files
unzip -q json-test-suite.zip
mv JSONTestSuite-master/* .
rmdir JSONTestSuite-master
rm json-test-suite.zip
```

**Source**: https://github.com/nst/JSONTestSuite  
**Branch**: master  
**Download Date**: 2025-09-21