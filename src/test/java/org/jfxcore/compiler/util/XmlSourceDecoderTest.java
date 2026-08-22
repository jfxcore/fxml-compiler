// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class XmlSourceDecoderTest {

    @TempDir
    Path tempDir;

    @Test
    public void Utf8_Utf16Le_And_Utf16Be_Files_Decode_To_The_Same_Text() throws IOException {
        String text = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><Root>Grüße</Root>";
        byte[] littleEndian = withPrefix(new byte[] {(byte)0xff, (byte)0xfe}, text.getBytes(StandardCharsets.UTF_16LE));
        byte[] bigEndian = withPrefix(new byte[] {(byte)0xfe, (byte)0xff}, text.getBytes(StandardCharsets.UTF_16BE));

        assertEquals(text, XmlSourceDecoder.decode(littleEndian));
        assertEquals(text, XmlSourceDecoder.decode(bigEndian));

        String utf8Text = text.replace("UTF-16", "UTF-8");
        Path utf8File = tempDir.resolve("sample.fxml");
        Files.write(utf8File, withPrefix(
            new byte[] {(byte)0xef, (byte)0xbb, (byte)0xbf},
            utf8Text.getBytes(StandardCharsets.UTF_8)));

        assertEquals(utf8Text, XmlSourceDecoder.decode(utf8File));
    }

    @Test
    public void Utf16_InitialByte_Pattern_Selects_Endian_Without_A_Bom() throws IOException {
        String text = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><Root/>";

        assertEquals(text, XmlSourceDecoder.decode(text.getBytes(StandardCharsets.UTF_16LE)));
        assertEquals(text, XmlSourceDecoder.decode(text.getBytes(StandardCharsets.UTF_16BE)));
    }

    @Test
    public void Encoding_Declaration_Can_Select_Legacy_Charset() throws IOException {
        String text = "<?xml version=\"1.0\" encoding=\"windows-1252\"?><Root>Grüße</Root>";
        assertEquals(text, XmlSourceDecoder.decode(text.getBytes(Charset.forName("windows-1252"))));
    }

    @Test
    public void Conflicting_Byte_Signature_And_Malformed_Input_Are_Rejected() {
        String conflict = "<?xml version=\"1.0\" encoding=\"windows-1252\"?><Root/>";
        byte[] utf8BomConflict = withPrefix(
            new byte[] {(byte)0xef, (byte)0xbb, (byte)0xbf},
            conflict.getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> XmlSourceDecoder.decode(utf8BomConflict));
        assertThrows(IOException.class, () -> XmlSourceDecoder.decode(
            new byte[] {'<', 'R', 'o', 'o', 't', '>', (byte)0xc3, '(', '<', '/', 'R', 'o', 'o', 't', '>'}));
    }

    private byte[] withPrefix(byte[] prefix, byte[] content) {
        byte[] result = new byte[prefix.length + content.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        return result;
    }
}
