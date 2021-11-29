// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.util.StringHelper;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.parse.XmlTokenType.*;

public class XmlReader {

    public final static String SOURCE_INFO_KEY = XmlReader.class.getName() + "$sourceInfo";
    public final static String ATTR_VALUE_SOURCE_INFO_KEY = XmlReader.class.getName() + "$attrValueSourceInfo";
    public final static String NAMESPACE_TO_PREFIX_MAP_KEY = XmlReader.class.getName() + "$namespaceToPrefix";

    private final Deque<Map<String, String>> namespaceStack = new ArrayDeque<>();
    private final XmlTokenizer tokenizer;
    private final Document document;

    public XmlReader(String source) {
        tokenizer = new XmlTokenizer(source);

        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            throw GeneralErrors.internalError(ex.getMessage());
        }

        XmlToken token = tokenizer.peek();
        boolean allowXmlDeclaration = true;

        while (token != null) {
            if (token.getType() == OPEN_PROCESSING_INSTRUCTION) {
                ProcessingInstruction pi = readProcessingInstruction(allowXmlDeclaration);
                if (pi != null) {
                    document.appendChild(pi);
                }

                allowXmlDeclaration = false;
            } else if (token.getType() == OPEN_BRACKET) {
                document.appendChild(readElement());
            } else if (token.getType() == WHITESPACE) {
                tokenizer.remove();
            } else if (token.getType() == COMMENT_START) {
                eatComment();
            } else {
                throw ParserErrors.unexpectedToken(token);
            }

            token = tokenizer.peek();
        }
    }

    public Document getDocument() {
        return document;
    }

    private ProcessingInstruction readProcessingInstruction(boolean allowXmlDeclaration) {
        SourceInfo start = tokenizer.removeSkipWS(OPEN_PROCESSING_INSTRUCTION).getSourceInfo();
        XmlToken token = tokenizer.remove(IDENTIFIER);

        if (token.getValue().equals("xml")) {
            if (allowXmlDeclaration) {
                while (tokenizer.peekNotNull().getType() != CLOSE_PROCESSING_INSTRUCTION) {
                    tokenizer.remove();
                }

                tokenizer.remove();
                return null;
            }

            throw ParserErrors.unexpectedToken(token);
        }

        String data = tokenizer.removeQualifiedIdentifierSkipWS(true).getValue();
        SourceInfo end = tokenizer.removeSkipWS(CLOSE_PROCESSING_INSTRUCTION).getSourceInfo();
        ProcessingInstruction pi = document.createProcessingInstruction(token.getValue(), data);
        pi.setUserData(SOURCE_INFO_KEY, SourceInfo.span(start, end), null);
        return pi;
    }

    private Node readText() {
        XmlToken nextToken = tokenizer.peekNotNull();
        if (nextToken.getType() == OPEN_BRACKET || nextToken.getType() == COMMENT_START) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        SourceInfo start = nextToken.getSourceInfo();
        boolean characterData = false;

        do {
            if (characterData && nextToken.getType() == CDATA_END) {
                characterData = false;
                tokenizer.remove();
                nextToken = tokenizer.peekNotNull();
            }

            if (!characterData && nextToken.getType() == CDATA_START) {
                characterData = true;
                tokenizer.remove();
            }

            builder.append(tokenizer.remove().getValue());
            nextToken = tokenizer.peekNotNull();
        } while (characterData || nextToken.getType() != OPEN_BRACKET && nextToken.getType() != COMMENT_START);

        String value;
        SourceInfo sourceInfo = SourceInfo.span(start, tokenizer.peekNotNull().getSourceInfo());

        try {
            value = StringHelper.unescapeXml(builder.toString());
        } catch (NumberFormatException ex) {
            throw ParserErrors.invalidExpression(sourceInfo);
        }

        Node node = document.createTextNode(StringHelper.removeInsignificantWhitespace(value));
        node.setUserData(SOURCE_INFO_KEY, sourceInfo, null);
        return node;
    }

    private Element readElement() {
        if (tokenizer.peek(OPEN_BRACKET) == null || tokenizer.containsAheadSkipWS(OPEN_BRACKET, SLASH)) {
            return null;
        }

        namespaceStack.push(namespaceStack.isEmpty() ? new HashMap<>() : new HashMap<>(namespaceStack.peek()));
        SourceInfo start = tokenizer.removeSkipWS(OPEN_BRACKET).getSourceInfo(), end;
        QName name = readQName();

        List<Attribute> attributes = new ArrayList<>();

        while (true) {
            Attribute attribute = readAttribute();
            if (attribute == null) {
                break;
            }

            attributes.add(attribute);
        }

        processNamespaces(attributes);

        Element element = document.createElementNS(namespaceStack.getFirst().get(name.prefix), name.toString());

        element.setUserData(
            NAMESPACE_TO_PREFIX_MAP_KEY,
            namespaceStack.getFirst().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)),
            null);

        for (Attribute attribute : attributes) {
            if (attribute.name.prefix != null) {
                String uri = namespaceStack.getFirst().get(attribute.name.prefix);

                Attr attr = element.getAttributeNodeNS(uri, attribute.name.toString());
                if (attr != null) {
                    throw PropertyAssignmentErrors.duplicateProperty(
                        attribute.sourceInfo, name.toString(), attribute.name.toString());
                }

                element.setAttributeNS(uri, attribute.name.toString(), attribute.value);

                attr = element.getAttributeNodeNS(uri, attribute.name.localName);
                attr.setUserData(SOURCE_INFO_KEY, attribute.sourceInfo, null);
                attr.setUserData(ATTR_VALUE_SOURCE_INFO_KEY, attribute.valueSourceInfo, null);
            }
        }

        XmlToken token = tokenizer.peekNotNullSkipWS();

        if (token.getType() == SLASH) {
            end = tokenizer.removeSkipWS(SLASH, CLOSE_BRACKET).getSourceInfo();
        } else {
            tokenizer.removeSkipWS(CLOSE_BRACKET);

            while (!tokenizer.containsAheadSkipWS(OPEN_BRACKET, SLASH)) {
                Node text = readText();
                if (text != null) {
                    element.appendChild(text);
                } else {
                    eatComment();
                    Element child = readElement();
                    if (child != null) {
                        element.appendChild(child);
                    }
                }
            }

            tokenizer.removeSkipWS(OPEN_BRACKET, SLASH);

            QName endName = readQName();
            if (!name.equals(endName)) {
                throw ParserErrors.unmatchedTag(endName.sourceInfo, name.toString());
            }

            end = tokenizer.removeSkipWS(CLOSE_BRACKET).getSourceInfo();
        }

        element.setUserData(SOURCE_INFO_KEY, SourceInfo.span(start, end), null);
        namespaceStack.pop();

        return element;
    }

    private void eatComment() {
        if (tokenizer.peek(COMMENT_START) == null) {
            return;
        }

        while (tokenizer.peekNotNull().getType() != COMMENT_END) {
            tokenizer.remove();
        }

        tokenizer.remove(COMMENT_END);
    }

    private void processNamespaces(List<Attribute> attributes) {
        Iterator<Attribute> it = attributes.iterator();
        while (it.hasNext()) {
            Attribute attribute = it.next();

            if ("xmlns".equals(attribute.name.prefix)) {
                namespaceStack.getFirst().put(attribute.name.localName, attribute.value);
                it.remove();
            } else if (attribute.name.prefix.isEmpty() && "xmlns".equals(attribute.name.localName)) {
                namespaceStack.getFirst().put("", attribute.value);
                it.remove();
            }
        }
    }

    private Attribute readAttribute() {
        if (tokenizer.peekSkipWS(IDENTIFIER) == null) {
            return null;
        }

        QName name = readQName();
        tokenizer.removeSkipWS(EQUALS);
        XmlToken token = tokenizer.removeSkipWS(QUOTED_STRING);
        String value;

        try {
            value = StringHelper.unescapeXml(StringHelper.unquote(token.getValue()));
        } catch (NumberFormatException ex) {
            throw ParserErrors.invalidExpression(token.getSourceInfo());
        }

        return new Attribute(
            name,
            value,
            SourceInfo.span(name.sourceInfo, token.getSourceInfo()),
            SourceInfo.shrink(token.getSourceInfo()));
    }

    private QName readQName() {
        SourceInfo start = null;
        String prefix = "";
        XmlToken[] tokens = tokenizer.peekAheadSkipWS(2);
        XmlToken token;

        if (tokens != null && tokens[0].getType() == IDENTIFIER && tokens[1].getType() == COLON) {
            start = tokens[0].getSourceInfo();
            prefix = tokens[0].getValue();
            tokenizer.removeSkipWS(IDENTIFIER, COLON);
            token = tokenizer.removeQualifiedIdentifier(false);
        } else {
            token = tokenizer.removeQualifiedIdentifierSkipWS(false);
        }

        SourceInfo sourceInfo = start != null ? SourceInfo.span(start, token.getSourceInfo()) : token.getSourceInfo();

        return new QName(prefix, token.getValue(), sourceInfo);
    }

    private static class Attribute {
        final QName name;
        final String value;
        final SourceInfo sourceInfo;
        final SourceInfo valueSourceInfo;

        Attribute(QName name, String value, SourceInfo sourceInfo, SourceInfo valueSourceInfo) {
            this.name = name;
            this.value = value;
            this.sourceInfo = sourceInfo;
            this.valueSourceInfo = valueSourceInfo;
        }

        @Override
        public String toString() {
            return name.toString() + "=\"" + value + "\"";
        }
    }

    private static class QName {
        final String prefix;
        final String localName;
        final SourceInfo sourceInfo;

        QName(String prefix, String localName, SourceInfo sourceInfo) {
            this.prefix = prefix;
            this.localName = localName;
            this.sourceInfo = sourceInfo;
        }

        @Override
        public String toString() {
            return prefix.isEmpty() ? localName : prefix + ":" + localName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QName)) return false;
            QName other = (QName)o;
            return Objects.equals(prefix, other.prefix) && Objects.equals(localName, other.localName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prefix, localName, sourceInfo);
        }
    }

}
