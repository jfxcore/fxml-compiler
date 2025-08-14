// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.intrinsic.IntrinsicProperty;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FxmlParser {

    // Intrinsic properties that are always interpreted as paths.
    private static final IntrinsicProperty[] PATH_INTRINSICS = new IntrinsicProperty[] {
        Intrinsics.ONCE.findProperty("path"),
        Intrinsics.CONTENT.findProperty("path"),
        Intrinsics.BIND.findProperty("path"),
        Intrinsics.BIND_CONTENT.findProperty("path"),
        Intrinsics.BIND_BIDIRECTIONAL.findProperty("path"),
        Intrinsics.BIND_CONTENT_BIDIRECTIONAL.findProperty("path")
    };

    // Intrinsics that are not interpreted by the inline parser.
    private static final Intrinsic[] VERBATIM_INTRINSICS = new Intrinsic[] {
        Intrinsics.ID,
        Intrinsics.TYPE,
        Intrinsics.STYLESHEET
    };

    private static final String[] INLINE_EXPR_TOKENS = new String[] {
        "{",
        InlineParser.BIND_BIDIRECTIONAL_EXPR_PREFIX,
        InlineParser.BIND_EXPR_PREFIX,
        InlineParser.ONCE_EXPR_PREFIX
    };

    private final String source;
    private final Path documentFile;

    public FxmlParser(String source) {
        this.source = source;
        this.documentFile = Paths.get(".");
    }

    public FxmlParser(Path baseDir, Path sourceFile) throws IOException {
        this.source = Files.readString(sourceFile);
        this.documentFile = baseDir.relativize(sourceFile);
    }

    public FxmlParser(Path baseDir, Path sourceFile, String source) {
        this.source = source;
        this.documentFile = baseDir.relativize(sourceFile);
    }

    public DocumentNode parseDocument() {
        Document document = new XmlReader(source).getDocument();
        NodeList nodes = document.getChildNodes();
        List<String> imports = new ArrayList<>();

        for (int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            if (node instanceof ProcessingInstruction pi) {
                if ("import".equals(pi.getTarget())) {
                    imports.add(pi.getData());
                }
            }
        }

        Element rootElement = document.getDocumentElement();
        String namespace = rootElement.getNamespaceURI();

        if (namespace == null) {
            throw new FxmlParseAbortException(
                getSourceInfo(rootElement),
                Diagnostic.newDiagnostic(ErrorCode.NAMESPACE_NOT_SPECIFIED));
        } else if (!FxmlNamespace.JAVAFX.isParentOf(namespace)) {
            throw new FxmlParseAbortException(
                getSourceInfo(rootElement),
                Diagnostic.newDiagnostic(ErrorCode.UNKNOWN_NAMESPACE, rootElement.getNamespaceURI()));
        }

        if (!imports.contains("java.lang.*")) {
            imports.add(0, "java.lang.*");
        }

        return new DocumentNode(documentFile, imports, parseElementNode(rootElement));
    }

    private ObjectNode parseElementNode(Element element) {
        String namespace = element.getNamespaceURI();

        if (!FxmlNamespace.JAVAFX.isParentOf(namespace) && !FxmlNamespace.FXML.equalsIgnoreCase(namespace)) {
            throw ParserErrors.unknownNamespace(getSourceInfo(element), namespace);
        }

        List<PropertyNode> properties = new ArrayList<>();
        List<org.jfxcore.compiler.ast.ValueNode> children = new ArrayList<>();

        for (int i = 0; i < element.getAttributes().getLength(); ++i) {
            Node attribute = element.getAttributes().item(i);
            if (attribute != null) {
                SourceInfo sourceInfo = getSourceInfo(attribute);
                properties.add(
                    createPropertyNode(
                        attribute.getPrefix(),
                        attribute.getNamespaceURI(),
                        attribute.getLocalName(),
                        List.of(nodeFromText(attribute, attribute.getNodeValue(), sourceInfo)),
                        sourceInfo));
            }
        }

        for (int i = 0; i < element.getChildNodes().getLength(); ++i) {
            Node child = element.getChildNodes().item(i);

            if (child instanceof Element) {
                children.add(parseElementNode((Element)child));
            } else if (child instanceof Text) {
                String value = child.getNodeValue();
                if (!value.isBlank()) {
                    children.add(nodeFromText(child, value, getSourceInfo(child)));
                }
            }
        }

        return new ObjectNode(
            new TypeNode(
                element.getLocalName(),
                element.getNodeName(),
                FxmlNamespace.FXML.equalsIgnoreCase(namespace),
                (SourceInfo)element.getUserData(XmlReader.ELEMENT_NAME_SOURCE_INFO_KEY)),
            properties, children, false, getSourceInfo(element));
    }

    private SourceInfo getSourceInfo(Node node) {
        return (SourceInfo)node.getUserData(XmlReader.SOURCE_INFO_KEY);
    }

    private ValueNode nodeFromText(Node node, String text, SourceInfo sourceInfo) {
        if (node.getParentNode() instanceof Element parent) {
            if (FxmlNamespace.FXML.equalsIgnoreCase(parent.getNamespaceURI())) {
                for (Intrinsic intrinsic : VERBATIM_INTRINSICS) {
                    if (intrinsic.getName().equals(parent.getLocalName())) {
                        return new TextNode(text, sourceInfo);
                    }
                }
            }
        }

        if (node instanceof Attr attr) {
            sourceInfo = (SourceInfo)node.getUserData(XmlReader.ATTR_VALUE_SOURCE_INFO_KEY);
            boolean parseAsPath = false;

            if (FxmlNamespace.FXML.equalsIgnoreCase(attr.getOwnerElement().getNamespaceURI())) {
                for (IntrinsicProperty intrinsicProperty : PATH_INTRINSICS) {
                    if (intrinsicProperty.getIntrinsic().getName().equals(attr.getOwnerElement().getLocalName())
                            && intrinsicProperty.getName().equals(attr.getLocalName())) {
                        parseAsPath = true;
                        break;
                    }
                }
            }

            if (parseAsPath) {
                return new InlineParser(text, getFxmlNamespacePrefix(node), sourceInfo.getStart()).parsePath();
            }

            String trimmed = text.trim();
            if (isInlineExpression(trimmed)) {
                if (trimmed.startsWith("{}")) {
                    return new TextNode(text.substring(text.indexOf("{}") + 2), sourceInfo);
                }

                return new InlineParser(text, getFxmlNamespacePrefix(node), sourceInfo.getStart()).parseObject();
            }
        }

        return new TextNode(text, sourceInfo);
    }

    private boolean isInlineExpression(String text) {
        for (String token : INLINE_EXPR_TOKENS) {
            if (text.startsWith(token)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private String getFxmlNamespacePrefix(Node node) {
        if (node instanceof Element element) {
            var prefixMap = (Map<String, String>)element.getUserData(XmlReader.NAMESPACE_TO_PREFIX_MAP_KEY);
            if (prefixMap != null && prefixMap.get(FxmlNamespace.FXML.toString()) != null) {
                return prefixMap.get(FxmlNamespace.FXML.toString());
            }
        }

        Node parent = node instanceof Attr ? ((Attr)node).getOwnerElement() : node.getParentNode();
        if (parent != null) {
            return getFxmlNamespacePrefix(parent);
        }

        return null;
    }

    private PropertyNode createPropertyNode(
            String prefix,
            String namespace,
            String name,
            Collection<? extends ValueNode> values,
            SourceInfo sourceInfo) {
        if (!FxmlNamespace.JAVAFX.isParentOf(namespace) && !FxmlNamespace.FXML.equalsIgnoreCase(namespace)) {
            throw ParserErrors.unknownNamespace(sourceInfo, namespace);
        }

        return new PropertyNode(
            name.split("\\."),
            prefix != null ? prefix + ":" + name : name,
            values,
            prefix != null && FxmlNamespace.FXML.equalsIgnoreCase(namespace),
            false,
            sourceInfo);
    }
}
