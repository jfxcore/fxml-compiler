// Copyright (c) 2021, 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.parse.CssTokenizer;
import org.jfxcore.compiler.parse.CurlyToken;
import org.jfxcore.compiler.parse.CurlyTokenClass;
import org.jfxcore.compiler.parse.CurlyTokenType;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.StringHelper;
import org.jfxcore.compiler.util.TypeHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

/**
 * Compiles stylesheets defined in markup using the fx:stylesheet intrinsic and stores the binary stylesheets
 * embedded into data-URLs. The result is returned as a {@link TextNode}.
 *
 * Inline styles specified in {@link javafx.scene.Node#styleProperty()} are minified.
 */
public class StylesheetTransform implements Transform {

    private static final String DATA_URI_PREFIX = "data:application/octet-stream;charset=utf-8;base64,";

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node instanceof ObjectNode objectNode) {
            if (objectNode.isIntrinsic(Intrinsics.STYLESHEET)) {
                return processStylesheet(objectNode);
            }
        }

        if (node instanceof TextNode textNode) {
            PropertyNode propertyNode = context.getParent().as(PropertyNode.class);
            if (propertyNode != null && propertyNode.getName().equals("style")) {
                ObjectNode objectNode = context.getParent(1).as(ObjectNode.class);
                if (objectNode != null && unchecked(node.getSourceInfo(),
                        () -> TypeHelper.getJvmType(objectNode).subtypeOf(Classes.NodeType()))) {
                    return processInlineStyle(textNode);
                }
            }
        }

        return node;
    }

    private Node processStylesheet(ObjectNode node) {
        if (node.getChildren().isEmpty()) {
            node.remove();
            return node;
        }

        byte[] stylesheet;

        TextNode source = getSourceText(node);
        verifyStylesheet(source);
        stylesheet = convertStylesheetToBinary(source.getText());

        String dataUrl = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(stylesheet);

        return TextNode.createRawResolved(dataUrl, node.getSourceInfo());
    }

    private TextNode processInlineStyle(TextNode node) {
        return TextNode.createRawResolved(formatStylesheet(node), node.getSourceInfo());
    }

    private TextNode getSourceText(ObjectNode node) {
        List<TextNode> textNodes = new ArrayList<>();

        for (Node child : node.getChildren()) {
            if (child instanceof TextNode) {
                textNodes.add((TextNode)child);
            } else {
                throw GeneralErrors.invalidContentInStylesheet(child.getSourceInfo());
            }
        }

        if (textNodes.size() == 1) {
            return textNodes.get(0);
        }

        StringBuilder builder = new StringBuilder();
        for (TextNode textNode : textNodes) {
            builder.append(textNode.getText());
        }

        return new TextNode(
            builder.toString(),
            SourceInfo.span(textNodes.get(0).getSourceInfo(), textNodes.get(textNodes.size() - 1).getSourceInfo()));
    }

    private void verifyStylesheet(TextNode source) {
        CssParser.errorsProperty().clear();
        new CssParser().parse(source.getText());
        if (!CssParser.errorsProperty().isEmpty()) {
            throw GeneralErrors.stylesheetError(
                source.getSourceInfo(), CssParser.errorsProperty().get(0).getMessage());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private byte[] convertStylesheetToBinary(String source) {
        File inputFile = null, outputFile = null;
        byte[] output;

        try {
            inputFile = File.createTempFile("jfxcore-compiler-", ".css");
            outputFile = File.createTempFile("jfxcore-compiler-", ".bss");

            try (var writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(inputFile), StandardCharsets.UTF_8))) {
                writer.write(source);
            }

            Stylesheet.convertToBinary(inputFile, outputFile);
            output = Files.readAllBytes(outputFile.toPath());
        } catch (IOException ex) {
            throw unchecked(ex);
        } finally {
            if (inputFile != null) {
                inputFile.delete();
            }

            if (outputFile != null) {
                outputFile.delete();
            }
        }

        return output;
    }

    private String formatStylesheet(TextNode source) {
        StringBuilder builder = new StringBuilder();
        CssTokenizer tokenizer = new CssTokenizer(source.getText(), source.getSourceInfo().getStart());
        CurlyToken last = null;

        while (!tokenizer.isEmpty()) {
            CurlyToken token = tokenizer.peekNotNull();

            if (token.getType() == CurlyTokenType.NEWLINE) {
                tokenizer.remove();
                continue;
            }

            if (last != null
                    && last.getType().getTokenClass() == CurlyTokenClass.LITERAL
                    && token.getType().getTokenClass() == CurlyTokenClass.LITERAL) {
                builder.append(' ');
            }

            if (token.getType() == CurlyTokenType.STRING) {
                builder.append(StringHelper.quote(tokenizer.remove().getValue()));
            } else {
                builder.append(tokenizer.remove().getValue());
            }

            last = token;
        }

        return builder.toString();
    }

}
