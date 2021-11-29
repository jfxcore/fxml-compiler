// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
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
import java.util.stream.Collectors;

public class FxmlParser {

    // Intrinsics that are not interpreted by the markup extension parser.
    private static final Intrinsic[] VERBATIM_INTRINSICS = new Intrinsic[] {
        Intrinsics.ID,
        Intrinsics.TYPE,
        Intrinsics.STYLESHEET
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
        try {
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
            } else if (!namespace.startsWith(FxmlNamespace.JAVAFX)) {
                throw new FxmlParseAbortException(
                    getSourceInfo(rootElement),
                    Diagnostic.newDiagnostic(ErrorCode.UNKNOWN_NAMESPACE, rootElement.getNamespaceURI()));
            }

            if (!imports.contains("java.lang.*")) {
                imports.add(0, "java.lang.*");
            }

            return new DocumentNode(documentFile, imports, parseElementNode(rootElement));
        } catch (MarkupException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GeneralErrors.internalError(ex.getMessage());
        }
    }

    private ObjectNode parseElementNode(Element element) {
        String namespace = element.getNamespaceURI();

        if (!FxmlNamespace.JAVAFX.equals(namespace) && !FxmlNamespace.FXML.equals(namespace)) {
            throw ParserErrors.unknownNamespace(getSourceInfo(element), namespace);
        }

        boolean isProperty = isPropertyName(element);

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
                boolean isChildProperty;
                Intrinsic childIntrinsic = FxmlNamespace.FXML.equals(child.getNamespaceURI()) ?
                    Intrinsics.find(child.getLocalName()) : null;

                if (isProperty) {
                    if (childIntrinsic != null && !childIntrinsic.getUsage().isElement()) {
                        throw GeneralErrors.unexpectedIntrinsic(getSourceInfo(child), child.getNodeName());
                    }

                    if (childIntrinsic == null && isPropertyName(child)) {
                        throw ParserErrors.elementCannotStartWithLowercaseLetter(
                            getSourceInfo(child), child.getNodeName());
                    }

                    isChildProperty = false;
                } else { // isElement
                    if (childIntrinsic != null) {
                        isChildProperty = childIntrinsic.getUsage().isAttribute();
                    } else {
                        isChildProperty = isPropertyName(child);
                    }
                }

                if (isChildProperty) {
                    properties.add(
                        createPropertyNode(
                            child.getPrefix(),
                            child.getNamespaceURI(),
                            child.getLocalName(),
                            parseElementNode((Element)child).getChildren().stream()
                                .map(c -> (ValueNode)c).collect(Collectors.toList()),
                            getSourceInfo(child)));
                } else {
                    children.add(parseElementNode((Element)child));
                }
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
                FxmlNamespace.FXML.equals(namespace),
                getSourceInfo(element)),
            properties, children, getSourceInfo(element));
    }

    private SourceInfo getSourceInfo(Node node) {
        return (SourceInfo)node.getUserData(XmlReader.SOURCE_INFO_KEY);
    }

    private boolean isPropertyName(Node node) {
        String[] parts = node.getLocalName().split("\\.");
        String name = parts[parts.length - 1];

        if (FxmlNamespace.FXML.equals(node.getNamespaceURI())) {
            if (parts.length != 1) {
                return false;
            }

            Intrinsic intrinsic = Intrinsics.find(parts[0]);
            return intrinsic != null && intrinsic.getUsage().isAttribute();
        }

        return Character.isLowerCase(name.charAt(0));
    }

    private ValueNode nodeFromText(Node node, String text, SourceInfo sourceInfo) {
        Node parent = node.getParentNode();
        if (parent instanceof Element && FxmlNamespace.FXML.equals(parent.getNamespaceURI())) {
            for (Intrinsic intrinsic : VERBATIM_INTRINSICS) {
                if (intrinsic.getName().equals(parent.getLocalName())) {
                    return new TextNode(text, sourceInfo);
                }
            }
        }

        String prefix = getFxmlNamespacePrefix(node, sourceInfo);

        if (node instanceof Attr) {
            sourceInfo = (SourceInfo)node.getUserData(XmlReader.ATTR_VALUE_SOURCE_INFO_KEY);
        }

        if (text.length() > 1) {
            ValueNode value = new MeParser(text, prefix, sourceInfo.getStart()).tryParseObject();
            if (value != null) {
                return value;
            }
        }

        return new TextNode(unescape(text), sourceInfo);
    }

    @SuppressWarnings("unchecked")
    private String getFxmlNamespacePrefix(Node node, SourceInfo sourceInfo) {
        if (node instanceof Element element) {
            var prefixMap = (Map<String, String>)element.getUserData(XmlReader.NAMESPACE_TO_PREFIX_MAP_KEY);
            if (prefixMap != null && prefixMap.get(FxmlNamespace.FXML) != null) {
                return prefixMap.get(FxmlNamespace.FXML);
            }
        }

        Node parent = node instanceof Attr ? ((Attr)node).getOwnerElement() : node.getParentNode();
        if (parent == null) {
            throw ParserErrors.namespaceNotSpecified(sourceInfo);
        }

        return getFxmlNamespacePrefix(parent, sourceInfo);
    }

    private PropertyNode createPropertyNode(
            String prefix,
            String namespace,
            String name,
            Collection<? extends ValueNode> values,
            SourceInfo sourceInfo) {
        if (!FxmlNamespace.JAVAFX.equals(namespace) && !FxmlNamespace.FXML.equals(namespace)) {
            throw ParserErrors.unknownNamespace(sourceInfo, namespace);
        }

        return new PropertyNode(
            name.split("\\."),
            prefix != null ? prefix + ":" + name : name,
            values,
            prefix != null && FxmlNamespace.FXML.equals(namespace),
            sourceInfo);
    }

    private String unescape(String value) {
        String trimmedValue = value.trim();

        if (trimmedValue.startsWith("\\{")) {
            return value.replaceFirst("\\\\\\{", "\\{");
        }

        if (trimmedValue.startsWith("\\$")) {
            return value.replaceFirst("\\\\\\$", "\\$");
        }

        if (trimmedValue.startsWith("\\#")) {
            return value.replaceFirst("\\\\#", "#");
        }

        if (trimmedValue.startsWith("\\@")) {
            return value.replaceFirst("\\\\@", "@");
        }

        if (trimmedValue.startsWith("\\%")) {
            return value.replaceFirst("\\\\%", "%");
        }

        return value;
    }

}
