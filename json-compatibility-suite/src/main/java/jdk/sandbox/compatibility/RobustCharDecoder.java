package jdk.sandbox.compatibility;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

/// Robust decoder for converting byte arrays to char arrays with multiple encoding fallback strategies.
/// Handles BOM detection, various Unicode encodings, and graceful degradation to byte-level conversion.
class RobustCharDecoder {
    
    private static final Logger LOGGER = Logger.getLogger(RobustCharDecoder.class.getName());
    
    // BOM signatures
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF32_BE_BOM = {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF32_LE_BOM = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00};
    
    /// Converts byte array to char array using multiple encoding strategies.
    /// 
    /// @param rawBytes the bytes to convert
    /// @param filename filename for logging purposes
    /// @return char array representing the content
    static char[] decodeToChars(byte[] rawBytes, String filename) {
        LOGGER.fine(() -> "Attempting robust decoding for " + filename + " (" + rawBytes.length + " bytes)");
        
        // Stage 1: BOM Detection
        BomResult bom = detectBOM(rawBytes);
        if (bom.encoding != null) {
            LOGGER.fine(() -> "BOM detected for " + filename + ": " + bom.encoding.name());
            char[] result = tryDecodeWithCharset(rawBytes, bom.offset, bom.encoding, filename);
            if (result != null) {
                return result;
            }
        }
        
        // Stage 2: Try standard encodings without BOM
        Charset[] encodings = {
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE,
            StandardCharsets.UTF_16, // Auto-detect endianness
            Charset.forName("UTF-32BE"),
            Charset.forName("UTF-32LE")
        };
        
        for (Charset encoding : encodings) {
            char[] result = tryDecodeWithCharset(rawBytes, 0, encoding, filename);
            if (result != null) {
                LOGGER.fine(() -> "Successfully decoded " + filename + " using " + encoding.name());
                return result;
            }
        }
        
        // Stage 3: Byte-level conversion with UTF-8 sequence awareness
        LOGGER.fine(() -> "Using permissive byte-to-char conversion for " + filename);
        return convertBytesToCharsPermissively(rawBytes);
    }
    
    private static BomResult detectBOM(byte[] bytes) {
        if (bytes.length >= 4 && Arrays.equals(Arrays.copyOf(bytes, 4), UTF32_BE_BOM)) {
            return new BomResult(Charset.forName("UTF-32BE"), 4);
        }
        if (bytes.length >= 4 && Arrays.equals(Arrays.copyOf(bytes, 4), UTF32_LE_BOM)) {
            return new BomResult(Charset.forName("UTF-32LE"), 4);
        }
        if (bytes.length >= 3 && Arrays.equals(Arrays.copyOf(bytes, 3), UTF8_BOM)) {
            return new BomResult(StandardCharsets.UTF_8, 3);
        }
        if (bytes.length >= 2 && Arrays.equals(Arrays.copyOf(bytes, 2), UTF16_BE_BOM)) {
            return new BomResult(StandardCharsets.UTF_16BE, 2);
        }
        if (bytes.length >= 2 && Arrays.equals(Arrays.copyOf(bytes, 2), UTF16_LE_BOM)) {
            return new BomResult(StandardCharsets.UTF_16LE, 2);
        }
        return new BomResult(null, 0);
    }
    
    private static char[] tryDecodeWithCharset(byte[] bytes, int offset, Charset charset, String filename) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
            CharBuffer charBuffer = decoder.decode(byteBuffer);
            
            char[] result = new char[charBuffer.remaining()];
            charBuffer.get(result);
            return result;
            
        } catch (CharacterCodingException e) {
            LOGGER.fine(() -> "Failed to decode " + filename + " with " + charset.name() + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.fine(() -> "Unexpected error decoding " + filename + " with " + charset.name() + ": " + e.getMessage());
            return null;
        }
    }
    
    /// Converts bytes to chars by attempting to interpret UTF-8 sequences properly,
    /// falling back to individual byte conversion for invalid sequences.
    private static char[] convertBytesToCharsPermissively(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF; // Convert to unsigned
            
            try {
                // Single-byte ASCII (0xxxxxxx)
                if ((b & 0x80) == 0) {
                    result.append((char) b);
                    i++;
                }
                // Multi-byte UTF-8 sequence
                else if ((b & 0xE0) == 0xC0) {
                    // 2-byte sequence (110xxxxx 10xxxxxx)
                    if (i + 1 < bytes.length && isValidContinuation(bytes[i + 1])) {
                        int codePoint = ((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
                        appendCodePoint(result, codePoint);
                        i += 2;
                    } else {
                        result.append((char) b); // Treat as individual byte
                        i++;
                    }
                }
                else if ((b & 0xF0) == 0xE0) {
                    // 3-byte sequence (1110xxxx 10xxxxxx 10xxxxxx)
                    if (i + 2 < bytes.length && 
                        isValidContinuation(bytes[i + 1]) && 
                        isValidContinuation(bytes[i + 2])) {
                        int codePoint = ((b & 0x0F) << 12) | 
                                      ((bytes[i + 1] & 0x3F) << 6) | 
                                      (bytes[i + 2] & 0x3F);
                        appendCodePoint(result, codePoint);
                        i += 3;
                    } else {
                        result.append((char) b);
                        i++;
                    }
                }
                else if ((b & 0xF8) == 0xF0) {
                    // 4-byte sequence (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
                    if (i + 3 < bytes.length && 
                        isValidContinuation(bytes[i + 1]) && 
                        isValidContinuation(bytes[i + 2]) && 
                        isValidContinuation(bytes[i + 3])) {
                        int codePoint = ((b & 0x07) << 18) | 
                                      ((bytes[i + 1] & 0x3F) << 12) |
                                      ((bytes[i + 2] & 0x3F) << 6) |
                                      (bytes[i + 3] & 0x3F);
                        appendCodePoint(result, codePoint);
                        i += 4;
                    } else {
                        result.append((char) b);
                        i++;
                    }
                }
                else {
                    // Invalid start byte or continuation byte out of place
                    result.append((char) b);
                    i++;
                }
            } catch (Exception e) {
                // Any error in UTF-8 parsing, fall back to byte-as-char
                result.append((char) b);
                i++;
            }
        }
        
        return result.toString().toCharArray();
    }
    
    private static boolean isValidContinuation(byte b) {
        return (b & 0xC0) == 0x80; // 10xxxxxx
    }
    
    private static void appendCodePoint(StringBuilder sb, int codePoint) {
        if (Character.isValidCodePoint(codePoint)) {
            if (Character.isBmpCodePoint(codePoint)) {
                sb.append((char) codePoint);
            } else {
                // Surrogate pair for code points > 0xFFFF
                sb.append(Character.highSurrogate(codePoint));
                sb.append(Character.lowSurrogate(codePoint));
            }
        } else {
            // Invalid code point, append replacement character
            sb.append('\uFFFD');
        }
    }
    
    private record BomResult(Charset encoding, int offset) {}
}