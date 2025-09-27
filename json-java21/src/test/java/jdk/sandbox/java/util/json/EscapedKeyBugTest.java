/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @enablePreview
 * @summary Test escaped characters in JSON object keys
 * @run junit EscapedKeyBugTest
 */

package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.logging.Logger;

public class EscapedKeyBugTest {
    private static final Logger LOGGER = Logger.getLogger(EscapedKeyBugTest.class.getName());

    @Test
    public void testEscapedCharactersInKeys() {
        LOGGER.info("Executing testEscapedCharactersInKeys");
        // Should parse successfully with escaped characters in keys
        String json = """
            {"foo\\nbar":1,"foo\\tbar":2}
            """;
        
        JsonValue result = Json.parse(json);
        JsonObject obj = (JsonObject) result;
        
        // Verify both keys are parsed correctly
        assertEquals(1L, ((JsonNumber) obj.members().get("foo\nbar")).toNumber());
        assertEquals(2L, ((JsonNumber) obj.members().get("foo\tbar")).toNumber());
    }

    @Test
    public void testEscapedQuoteInKey() {
        LOGGER.info("Executing testEscapedQuoteInKey");
        // Test escaped quotes in keys
        String json = """
            {"foo\\"bar":1}
            """;
        
        JsonValue result = Json.parse(json);
        JsonObject obj = (JsonObject) result;
        
        // Verify key with escaped quote is parsed correctly
        assertEquals(1L, ((JsonNumber) obj.members().get("foo\"bar")).toNumber());
    }

    @Test
    public void testEscapedBackslashInKey() {
        LOGGER.info("Executing testEscapedBackslashInKey");
        // Test escaped backslashes in keys
        String json = """
            {"foo\\\\bar":1}
            """;
        
        JsonValue result = Json.parse(json);
        JsonObject obj = (JsonObject) result;
        
        // Verify key with escaped backslash is parsed correctly
        assertEquals(1L, ((JsonNumber) obj.members().get("foo\\bar")).toNumber());
    }

    @Test
    public void testMultipleEscapedCharactersInKey() {
        LOGGER.info("Executing testMultipleEscapedCharactersInKey");
        // Test multiple escaped characters in one key
        String json = """
            {"foo\\n\\t\\"bar":1}
            """;
        
        JsonValue result = Json.parse(json);
        JsonObject obj = (JsonObject) result;
        
        // Verify key with multiple escaped characters is parsed correctly
        assertEquals(1L, ((JsonNumber) obj.members().get("foo\n\t\"bar")).toNumber());
    }
}