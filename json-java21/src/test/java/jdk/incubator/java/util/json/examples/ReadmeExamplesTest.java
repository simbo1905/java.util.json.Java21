/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.incubator.java.util.json.examples;

import jdk.incubator.java.util.json.JsonTestLoggingConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The README.md ("Running the Examples") and index.html both promise
/// `jdk.incubator.java.util.json.examples.ReadmeExamples` as a runnable
/// artifact. This test keeps that promise honest by executing the examples
/// and verifying they run to completion without throwing.
public class ReadmeExamplesTest extends JsonTestLoggingConfig {

    private static final Logger LOG = Logger.getLogger(ReadmeExamplesTest.class.getName());

    @Test
    void readmeExamplesRunToCompletion() {
        final var out = new ByteArrayOutputStream();
        final var originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertDoesNotThrow(() -> ReadmeExamples.main(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
        final var output = out.toString(StandardCharsets.UTF_8);
        LOG.info(() -> "ReadmeExamples produced " + output.length() + " chars of output");
        assertTrue(output.contains("All examples completed successfully!"),
                "ReadmeExamples did not report successful completion; output was:\n" + output);
        assertTrue(output.contains("1. Quick Start Example"),
                "ReadmeExamples did not run the first example; output was:\n" + output);
    }
}
