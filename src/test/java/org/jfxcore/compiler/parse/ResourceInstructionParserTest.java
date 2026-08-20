// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.jfxcore.compiler.resource.EmbeddedResourceTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceInstructionParserTest {

    @Test
    public void Omitted_Media_Type_Defaults_To_Utf8() {
        EmbeddedResource resource = parse(" styles.css:\n    Grüße\n");

        assertEquals("styles.css", resource.logicalName());
        assertArrayEquals("Grüße".getBytes(StandardCharsets.UTF_8), resource.content());
        assertSame(resource.content(), resource.content());
        assertEquals("sample/View$styles.css", resource.logicalPath());
    }

    @Test
    public void Explicit_Media_Type_Is_Validated_And_Its_Charset_Encodes_The_Payload() {
        EmbeddedResource resource = parse(
            " data.xyz application/x-example;Version=2; note=\"a:b\";CHARSET='UTF-16LE':Grüße € 漢");

        assertArrayEquals("Grüße € 漢".getBytes(StandardCharsets.UTF_16LE), resource.content());
    }

    @Test
    public void Quoted_And_Bare_Names_Have_The_Same_Identity() {
        EmbeddedResource bare = parse(" theme.css:text");
        EmbeddedResource quoted = parse(" \"theme.css\":text");
        EmbeddedResource spaced = parse(" \"dark theme.css\":text");

        assertEquals(bare.logicalName(), quoted.logicalName());
        assertEquals("dark theme.css", spaced.logicalName());
        assertEquals("sample/View$dark theme.css", spaced.logicalPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", ".", "..", "subdir/file", "subdir\\file", "bad:name", "bad*name", "bad?name",
        "bad\"name", "bad<name", "bad>name", "bad|name", "trailing ", "trailing.", "CON",
        "con.txt", "PRN.log", "AUX", "NUL", "COM1.dat", "COM9", "LPT1", "lpt9.css",
        "control\u0001name", "delete\u007fname"
    })
    public void NonPortable_Filenames_Are_Rejected(String logicalName) {
        char quote = logicalName.indexOf('"') >= 0 ? '\'' : '"';
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" " + quote + logicalName + quote + ":payload"));

        assertEquals(ErrorCode.INVALID_RESOURCE_NAME, exception.getDiagnostic().getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        " text/plain;:payload",
        " text/:payload",
        " /plain:payload",
        " */plain:payload",
        " text/*:payload",
        " text/plain;charset=:payload"
    })
    public void Malformed_Media_Types_Are_Rejected(String descriptor) {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" file.txt" + descriptor));

        assertEquals(ErrorCode.INVALID_RESOURCE_MEDIA_TYPE, exception.getDiagnostic().getCode());
    }

    @Test
    public void Unterminated_Descriptor_Quote_Is_Rejected() {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" file.txt text/plain;charset=\"unterminated:payload"));

        assertEquals(ErrorCode.INVALID_RESOURCE_DECLARATION, exception.getDiagnostic().getCode());
    }

    @ParameterizedTest
    @CsvSource({
        "text/plain;charset=UTF-8;Charset=US-ASCII, Charset",
        "application/x-example;version=1;VERSION=2, VERSION"
    })
    public void Duplicate_Media_Type_Parameter_Names_Are_Rejected_Case_Insensitively(
            String mediaType, String duplicateName) {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" file.txt " + mediaType + ":value"));

        assertEquals(ErrorCode.DUPLICATE_RESOURCE_MEDIA_TYPE_PARAMETER, exception.getDiagnostic().getCode());
        assertEquals(
            "Resource 'file.txt' declares media type parameter '%s' more than once".formatted(duplicateName),
            exception.getMessage());
    }

    @Test
    public void Unsupported_Charset_Is_Rejected() {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" file.txt text/plain;charset=x-no-such-charset:value"));

        assertEquals(ErrorCode.UNSUPPORTED_RESOURCE_CHARSET, exception.getDiagnostic().getCode());
    }

    @Test
    public void Unrepresentable_Character_Is_Rejected() {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> parse(" file.txt text/plain;charset=US-ASCII:Grüße"));

        assertEquals(ErrorCode.UNREPRESENTABLE_RESOURCE_CHARACTER, exception.getDiagnostic().getCode());
    }

    @Test
    public void Multiline_Layout_Is_Removed_And_Blank_Line_Content_Is_Preserved() {
        EmbeddedResource resource = parse(
            " file.txt:\r\n\t  first\r\n\t      second\r\n\t  \r\n\t  third\r\n");

        assertEquals("first\n    second\n\nthird", utf8(resource));
    }

    @Test
    public void SameLine_Spaces_And_An_Intentional_Trailing_Newline_Are_Preserved() {
        assertEquals("  value", utf8(parse(" file.txt:  value")));
        assertEquals("value\n", utf8(parse(" file.txt:\n    value\n    \n")));
    }

    @Test
    public void Resource_Table_Rejects_Exact_And_CaseOnly_Duplicates() {
        EmbeddedResourceTable table = new EmbeddedResourceTable();
        table.register(parse(" Foo.txt:first"));

        MarkupException exact = assertThrows(MarkupException.class, () -> table.register(parse(" Foo.txt:second")));
        MarkupException caseOnly = assertThrows(MarkupException.class, () -> table.register(parse(" foo.txt:second")));

        assertEquals(ErrorCode.DUPLICATE_RESOURCE_DECLARATION, exact.getDiagnostic().getCode());
        assertEquals(ErrorCode.DUPLICATE_RESOURCE_DECLARATION, caseOnly.getDiagnostic().getCode());
        assertEquals("Resource 'Foo.txt' conflicts with declaration at 1:2", exact.getMessage());
        assertEquals("Resource 'foo.txt' conflicts with declaration at 1:2", caseOnly.getMessage());
        assertEquals(List.of("Foo.txt"), table.declarations().stream().map(EmbeddedResource::logicalName).toList());
        assertThrows(UnsupportedOperationException.class, () -> table.declarations().clear());
    }

    private String utf8(EmbeddedResource resource) {
        return new String(resource.content(), StandardCharsets.UTF_8);
    }

    private EmbeddedResource parse(String data) {
        return new ResourceInstructionParser(data, Path.of("sample", "View.fxml")).parse();
    }
}
