// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.intrinsic.IntrinsicProperty;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
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
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.NumberUtil;
import org.jfxcore.compiler.util.StringHelper;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FxmlParser {

    // Intrinsic properties that are always interpreted as paths.
    private static final IntrinsicProperty[] PATH_INTRINSICS = new IntrinsicProperty[] {
        Intrinsics.EVALUATE.findProperty("path"),
        Intrinsics.OBSERVE.findProperty("path"),
        Intrinsics.SYNCHRONIZE.findProperty("path")
    };

    // Intrinsics that are not interpreted by the inline parser.
    private static final Intrinsic[] VERBATIM_INTRINSICS = new Intrinsic[] {
        Intrinsics.ID,
        Intrinsics.TYPE,
        Intrinsics.STYLESHEET
    };

    private static final String[] INLINE_EXPR_TOKENS = new String[] {
        "{",
        InlineParser.SYNCHRONIZE_EXPR_PREFIX,
        InlineParser.OBSERVE_EXPR_PREFIX,
        InlineParser.EVALUATE_EXPR_PREFIX
    };

    private static final Map<Character, String> DEFAULT_PREFIX_MAPPINGS = Map.of(
        '%', "org.jfxcore.markup.resource.StaticResource",
        '@', "org.jfxcore.markup.resource.ClassPathResource"
    );

    private static final String RESERVED_PREFIX_CHARACTERS = "{}()[]<>,;:=*/.#&\"'?";

    private final String sourceText;
    private final Path documentFile;
    private final @Nullable EmbeddingContext embeddingContext;

    private Map<Character, String> prefixMappings;

    public FxmlParser(String sourceText) {
        this.sourceText = sourceText;
        this.documentFile = Paths.get(".");
        this.embeddingContext = null;
    }

    public FxmlParser(Path sourceFile, String sourceText, @Nullable EmbeddingContext embeddingContext) {
        this.sourceText = sourceText;
        this.documentFile = sourceFile;
        this.embeddingContext = embeddingContext;
    }

    public DocumentNode parseDocument() {
        Map<String, String> implicitNamespaces = embeddingContext != null
            ? FxmlNamespace.getDefaultMap()
            : Map.of();

        Document document = new XmlReader(sourceText, implicitNamespaces).getDocument();
        NodeList nodes = document.getChildNodes();
        List<String> imports = new ArrayList<>();
        Map<Character, String> prefixMappings = new HashMap<>();

        for (int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            if (node instanceof ProcessingInstruction pi) {
                if ("import".equals(pi.getTarget())) {
                    imports.add(pi.getData());
                } else if ("prefix".equals(pi.getTarget())) {
                    parsePrefixInstruction(pi, prefixMappings);
                }
            }
        }

        if (embeddingContext != null && !embeddingContext.imports().isEmpty()) {
            Set<String> existingImports = new HashSet<>(imports);
            List<String> newImports = new ArrayList<>(embeddingContext.imports().size());

            for (String additionalImport : embeddingContext.imports()) {
                if (!existingImports.contains(additionalImport)) {
                    newImports.add(additionalImport);
                }
            }

            imports.addAll(0, newImports);
        }

        for (var entry : DEFAULT_PREFIX_MAPPINGS.entrySet()) {
            prefixMappings.putIfAbsent(entry.getKey(), entry.getValue());
        }

        this.prefixMappings = Map.copyOf(prefixMappings);

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

        ObjectNode rootNode = parseElementNode(rootElement);

        if (embeddingContext != null) {
            PropertyNode classProperty = rootNode.findIntrinsicProperty(Intrinsics.CLASS);
            if (classProperty != null) {
                throw GeneralErrors.unexpectedIntrinsic(classProperty.getSourceInfo(), classProperty.getMarkupName());
            }

            rootNode.getProperties().add(new PropertyNode(
                new String[] { Intrinsics.CLASS.getName() }, Intrinsics.CLASS.getName(),
                List.of(new TextNode(embeddingContext.embeddingHost().fullName(), getSourceInfo(rootElement))),
                true, false, getSourceInfo(rootElement)));
        }

        return new DocumentNode(documentFile, imports, rootNode);
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
                        return createTextNode(text, sourceInfo, true);
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
                return new InlineParser(text, getFxmlNamespacePrefix(node), sourceInfo.getStart(), prefixMappings).parsePath();
            }

            String trimmed = text.trim();
            if (isInlineExpression(trimmed)) {
                if (trimmed.startsWith("{}")) {
                    return createTextNode(text.substring(text.indexOf("{}") + 2), sourceInfo, true);
                }

                return new InlineParser(text, getFxmlNamespacePrefix(node), sourceInfo.getStart(), prefixMappings).parseObject();
            }
        }

        return createTextNode(text, sourceInfo, true);
    }

    private TextNode createTextNode(String text, SourceInfo sourceInfo, boolean allowList) {
        try {
            NumberUtil.parse(text);
            return new NumberNode(text, sourceInfo);
        } catch (NumberFormatException ignored) {
        }

        if (!allowList) {
            return new TextNode(text, sourceInfo);
        }

        List<StringHelper.Part> items = StringHelper.splitList(text);
        if (items.size() == 1) {
            return new TextNode(text, sourceInfo);
        }

        TextNode[] textNodes = new TextNode[items.size()];
        int column = sourceInfo.getStart().getColumn();

        for (int i = 0; i < items.size(); i++) {
            StringHelper.Part item = items.get(i);
            int startLine = sourceInfo.getStart().getLine() + item.line();
            int startColumn = column + item.column();
            var itemSourceInfo = new SourceInfo(startLine, startColumn, startLine, startColumn + item.text().length());
            textNodes[i] = createTextNode(item.text(), itemSourceInfo, false);

            if (item.lineBreak()) {
                column = 0;
            }
        }

        return new ListNode(text, Arrays.asList(textNodes), sourceInfo);
    }

    private boolean isInlineExpression(String text) {
        for (String token : INLINE_EXPR_TOKENS) {
            if (text.startsWith(token)) {
                return true;
            }
        }

        return !text.isEmpty() && prefixMappings.containsKey(text.charAt(0));
    }

    private void parsePrefixInstruction(ProcessingInstruction pi, Map<Character, String> prefixMappings) {
        String data = pi.getData();
        int separator = data.indexOf('=');

        if (separator < 0) {
            throw ParserErrors.invalidExpression(getSourceInfo(pi));
        }

        String prefixText = data.substring(0, separator).trim();
        String typeName = data.substring(separator + 1).trim();

        if (prefixText.length() != 1
                || typeName.isEmpty()
                || !isValidPrefixCharacter(prefixText.charAt(0))
                || !NameHelper.isQualifiedIdentifier(typeName)) {
            throw ParserErrors.invalidExpression(getSourceInfo(pi));
        }

        char prefix = prefixText.charAt(0);
        String previousType = prefixMappings.putIfAbsent(prefix, typeName);
        if (previousType != null) {
            throw ParserErrors.duplicatePrefixDeclaration(getSourceInfo(pi), prefix, previousType);
        }
    }

    private boolean isValidPrefixCharacter(char character) {
        return !Character.isWhitespace(character)
            && !Character.isJavaIdentifierPart(character)
            && RESERVED_PREFIX_CHARACTERS.indexOf(character) < 0;
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
