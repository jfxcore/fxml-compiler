// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractTokenizer<TTokenType extends TokenType, TToken extends AbstractToken<TTokenType>>
        extends ArrayDeque<TToken> {

    private enum OperationType {
        ADD_FIRST, ADD_LAST, REMOVE_FIRST, REMOVE_LAST
    }

    private final class Operation {
        final OperationType type;
        final TToken value;

        Operation(OperationType type, TToken value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type.toString() + ": " + value;
        }
    }

    private final class State {
        final List<Operation> operations = new ArrayList<>();
        final List<TToken> skippedTokens;
        final TToken lastRemoved;

        State(List<TToken> skippedTokens, TToken lastRemoved) {
            this.skippedTokens = skippedTokens.isEmpty() ? Collections.emptyList() : new ArrayList<>(skippedTokens);
            this.lastRemoved = lastRemoved;
        }

        void undo() {
            AbstractTokenizer.this.suppressRecording = true;

            for (int i = operations.size() - 1; i >= 0; --i) {
                switch (operations.get(i).type) {
                    case ADD_FIRST -> AbstractTokenizer.this.removeFirst();
                    case ADD_LAST -> AbstractTokenizer.this.removeLast();
                    case REMOVE_FIRST -> AbstractTokenizer.this.addFirst(operations.get(i).value);
                    case REMOVE_LAST -> AbstractTokenizer.this.addLast(operations.get(i).value);
                }
            }

            AbstractTokenizer.this.suppressRecording = false;
        }
    }

    private final ArrayDeque<State> storedStates = new ArrayDeque<>();
    private final Class<TToken> tokenClass;
    private final List<Line> lines;
    private final List<String> textLines;
    private final List<TToken> skippedTokens;
    private TToken lastRemoved;
    private boolean suppressRecording;

    AbstractTokenizer(String text, Class<TToken> tokenClass) {
        this.tokenClass = tokenClass;
        lines = splitLines(text);
        textLines = lines.stream().map(line -> line.value).collect(Collectors.toList());
        skippedTokens = new ArrayList<>();
    }

    public void mark() {
        storedStates.push(new State(skippedTokens, lastRemoved));
    }

    public void resetToMark() {
        if (storedStates.isEmpty()) {
            throw new IllegalStateException();
        }

        State storedState = storedStates.pop();
        storedState.undo();
        skippedTokens.clear();
        skippedTokens.addAll(storedState.skippedTokens);
        lastRemoved = storedState.lastRemoved;
    }

    public void forgetMark() {
        State forgot = storedStates.pop();
        State top = storedStates.peek();

        if (top != null) {
            top.operations.addAll(forgot.operations);
        }
    }

    @Override
    public TToken pollFirst() {
        TToken token = super.pollFirst();
        State storedState = storedStates.peek();

        if (!suppressRecording && token != null && storedState != null) {
            storedState.operations.add(new Operation(OperationType.REMOVE_FIRST, token));
        }

        return token;
    }

    @Override
    public TToken pollLast() {
        TToken token = super.pollLast();
        State storedState = storedStates.peek();

        if (!suppressRecording && token != null && storedState != null) {
            storedState.operations.add(new Operation(OperationType.REMOVE_LAST, token));
        }

        return token;
    }

    @Override
    public TToken removeFirst() {
        TToken token = super.removeFirst();
        State storedState = storedStates.peek();

        if (!suppressRecording && storedState != null) {
            storedState.operations.add(new Operation(OperationType.REMOVE_FIRST, token));
        }

        return token;
    }

    @Override
    public TToken removeLast() {
        TToken token = super.removeLast();
        State storedState = storedStates.peek();

        if (!suppressRecording && storedState != null) {
            storedState.operations.add(new Operation(OperationType.REMOVE_LAST, token));
        }

        return token;
    }

    @Override
    public void addFirst(@NotNull TToken token) {
        super.addFirst(token);
        State storedState = storedStates.peek();

        if (!suppressRecording && storedState != null) {
            storedState.operations.add(new Operation(OperationType.ADD_FIRST, token));
        }
    }

    @Override
    public void addLast(@NotNull TToken token) {
        super.addLast(token);
        State storedState = storedStates.peek();

        if (!suppressRecording && storedState != null) {
            storedState.operations.add(new Operation(OperationType.ADD_LAST, token));
        }
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        throw new UnsupportedOperationException();
    }

    public TToken getLastRemoved() {
        return lastRemoved;
    }

    public List<String> getLines() {
        return textLines;
    }

    public TToken peekNotNull() {
        TToken token = peek();
        if (token == null) {
            throw ParserErrors.unexpectedEndOfFile(
                lastRemoved != null ? SourceInfo.after(lastRemoved.getSourceInfo()) : SourceInfo.none());
        }

        return token;
    }

    public TToken peekNotNullSkipWS() {
        try {
            suppressRecording = true;
            skipWS();
            return peekNotNull();
        } finally {
            restoreWS();
            suppressRecording = false;
        }
    }

    @Nullable
    public TToken peekSkipWS() {
        try {
            suppressRecording = true;
            skipWS();
            return peek();
        } finally {
            restoreWS();
            suppressRecording = false;
        }
    }

    @Nullable
    public TToken peek(TTokenType expected) {
        TToken token = peek();
        if (token == null || token.getType() != expected) {
            return null;
        }

        return token;
    }

    @Nullable
    public TToken peekSkipWS(TTokenType expected) {
        try {
            suppressRecording = true;
            skipWS();
            return peek(expected);
        } finally {
            restoreWS();
            suppressRecording = false;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public TToken[] peekAhead(int count) {
        if (size() < count) {
            return null;
        }

        TToken[] tokens = (TToken[])Array.newInstance(tokenClass, count);

        try {
            suppressRecording = true;

            for (int i = 0; i < count; ++i) {
                TToken token = remove();
                tokens[i] = token;
                add(token);
            }
        } finally {
            for (int i = 0; i < count; ++i) {
                addFirst(removeLast());
            }

            suppressRecording = false;
        }

        return tokens;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public TToken[] peekAheadSkipWS(int count) {
        TToken[] tokens = (TToken[])Array.newInstance(tokenClass, count);
        int size = size();
        int added = 0;

        try {
            suppressRecording = true;

            for (int i = 0; i < count; ++i) {
                List<TToken> skipped = skipWS();
                size -= skipped.size();
                added += skipped.size();

                for (int j = skipped.size() - 1; j >= 0; --j) {
                    add(skipped.get(j));
                }

                skipped.clear();

                if (size <= 0) {
                    return null;
                }

                TToken token = remove();
                tokens[i] = token;
                add(token);
                size--;
                added++;
            }
        } finally {
            for (int i = 0; i < added; ++i) {
                addFirst(removeLast());
            }

            suppressRecording = false;
        }

        return tokens;
    }

    public TToken[] peekAheadNotNull(int count) {
        TToken[] tokens = peekAhead(count);
        if (tokens == null) {
            while (!isEmpty()) {
                remove();
            }

            throw ParserErrors.unexpectedEndOfFile(
                lastRemoved != null ? SourceInfo.after(lastRemoved.getSourceInfo()) : SourceInfo.none());
        }

        return tokens;
    }

    @SafeVarargs
    public final boolean containsAhead(TTokenType... expected) {
        TToken[] tokens = peekAheadNotNull(expected.length);

        for (int i = 0; i < expected.length; ++i) {
            if (tokens[i].getType() != expected[i]) {
                return false;
            }
        }

        return true;
    }

    @SafeVarargs
    public final boolean containsAheadSkipWS(TTokenType... expected) {
        int size = size();
        int added = 0;

        try {
            suppressRecording = true;

            for (TTokenType type : expected) {
                List<TToken> skipped = skipWS();
                size -= skipped.size();
                added += skipped.size();

                for (int j = skipped.size() - 1; j >= 0; --j) {
                    add(skipped.get(j));
                }

                skipped.clear();

                if (size <= 0) {
                    return false;
                }

                TToken token = poll(type);
                if (token == null) {
                    return false;
                }

                add(token);
                size--;
                added++;
            }
        } finally {
            for (int i = 0; i < added; ++i) {
                addFirst(removeLast());
            }

            suppressRecording = false;
        }

        return true;
    }

    public TToken remove(TTokenType expected) {
        lastRemoved = remove();
        checkExpected(lastRemoved, expected);
        return lastRemoved;
    }

    public TToken removeSkipWS(TTokenType expected) {
        skipWS().clear();
        return remove(expected);
    }

    @SafeVarargs
    public final TToken remove(TTokenType... expected) {
        TToken token = null;

        for (TTokenType type : expected) {
            token = remove(type);
        }

        return token;
    }

    @SafeVarargs
    public final TToken removeSkipWS(TTokenType... expected) {
        TToken last = null;

        for (TTokenType type : expected) {
            skipWS().clear();
            last = remove(type);
        }

        return last;
    }

    @Override
    public TToken remove() {
        TToken token = super.poll();
        if (token != null) {
            lastRemoved = token;
        } else {
            throw ParserErrors.unexpectedEndOfFile(
                lastRemoved != null ? SourceInfo.after(lastRemoved.getSourceInfo()) : SourceInfo.none());
        }

        return token;
    }

    @Override
    public TToken poll() {
        TToken token = super.poll();
        if (token != null) {
            lastRemoved = token;
        }

        return token;
    }

    @Nullable
    public TToken poll(TTokenType type) {
        TToken token = peek();
        if (token != null && token.getType() == type) {
            return lastRemoved = super.poll();
        }

        return null;
    }

    public SourceInfo getSourceInfo(int offsetStart, int offsetEnd) {
        int startLine = -1, endLine = -1, startCol = -1, endCol = -1;

        for (int i = 0; i < lines.size(); ++i) {
            Line line = lines.get(i);

            if (startLine < 0 && line.position + line.value.length() >= offsetStart) {
                startLine = i;
                startCol = offsetStart - line.position;
            }

            if (line.position + line.value.length() >= offsetEnd) {
                endLine = i;
                endCol = offsetEnd - line.position;
                break;
            }
        }

        return new SourceInfo(startLine, startCol, endLine, endCol);
    }

    public boolean isNewline(String value) {
        switch (value) {
            case "\n":
            case "\r":
            case "\u000B":
            case "\u000C":
            case "\u0085":
            case "\u2028":
            case "\u2029":
                return true;
        }

        return false;
    }

    public TToken removeQualifiedIdentifier(boolean allowStar) {
        TToken token = pollQualifiedIdentifier(allowStar);
        if (token == null) {
            if (isEmpty()) {
                throw ParserErrors.unexpectedEndOfFile(
                    lastRemoved != null ? SourceInfo.after(lastRemoved.getSourceInfo()) : SourceInfo.none());
            } else {
                throw ParserErrors.expectedIdentifier(peek());
            }
        }

        return token;
    }

    public TToken removeQualifiedIdentifierSkipWS(boolean allowStar) {
        while (peekNotNull().getType().isWhitespace()) {
            remove();
        }

        return removeQualifiedIdentifier(allowStar);
    }

    @Nullable
    public TToken pollQualifiedIdentifier(boolean allowStar) {
        int tokens = parseQualifiedIdentifierTokens(allowStar);
        if (tokens < 0) {
            return null;
        }

        TToken first = remove();
        TToken last = first;
        StringBuilder builder = new StringBuilder(first.getValue());

        for (int i = 1; i < tokens; ++i) {
            builder.append((last = remove()).getValue());
        }

        first.setContent(builder.toString(), SourceInfo.span(first.getSourceInfo(), last.getSourceInfo()));

        return first;
    }

    private int parseQualifiedIdentifierTokens(boolean allowStar) {
        int count = 0;
        TToken oldLastRemoved = lastRemoved;

        try {
            TToken token = peek();
            if (token == null || !token.getType().isIdentifier()) {
                return -1;
            }

            add(remove());
            token = peek();
            count++;

            while (token != null && token.getValue().equals(".")) {
                add(remove());
                token = peek();
                count++;

                if (token == null || count >= size()) {
                    return -1;
                }

                token = peek();

                if (token != null && token.getType().isIdentifier()) {
                    remove();
                } else {
                    token = null;
                }

                if (token == null) {
                    if (allowStar) {
                        token = peek();

                        if (token != null && token.getValue().equals("*")) {
                            remove();
                        } else {
                            token = null;
                        }
                    }
                }

                if (token != null) {
                    add(token);
                    count++;

                    if (token.getValue().equals("*")) {
                        break;
                    }
                } else {
                    return -1;
                }

                token = peek();
            }

            return count;
        } finally {
            for (int i = 0; i < count; ++i) {
                addFirst(removeLast());
            }

            lastRemoved = oldLastRemoved;
        }
    }

    private List<TToken> skipWS() {
        TToken token;
        while ((token = poll()) != null && token.getType().isWhitespace()) {
            skippedTokens.add(token);
        }

        if (token != null && !token.getType().isWhitespace()) {
            addFirst(token);
        }

        return skippedTokens;
    }

    private void restoreWS() {
        for (int i = skippedTokens.size() - 1; i >= 0; --i) {
            addFirst(skippedTokens.get(i));
        }

        skippedTokens.clear();
    }

    private void checkExpected(TToken token, TTokenType expected) {
        if (token == null) {
            TToken lastRemoved = getLastRemoved();
            throw ParserErrors.unexpectedEndOfFile(
                lastRemoved != null ? SourceInfo.after(lastRemoved.getSourceInfo()) : SourceInfo.none());
        }

        if (token.getType() != expected) {
            if (expected.isIdentifier()) {
                throw ParserErrors.expectedIdentifier(token);
            }

            if (expected.getSymbol() != null) {
                throw ParserErrors.expectedToken(token, expected.getSymbol());
            }

            throw ParserErrors.unexpectedToken(token);
        }
    }

    private List<Line> splitLines(String text) {
        List<Line> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder(120);
        int position = 0;

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            boolean newline = false;

            switch (c) {
                case 0x000A:
                case 0x000B:
                case 0x000C:
                case 0x0085:
                case 0x2028:
                case 0x2029:
                    newline = true;
                    break;

                case 0x000D:
                    if (i < text.length() - 1 && text.charAt(i + 1) == 0x000A) {
                        ++i;
                    }

                    newline = true;
                    break;
            }

            if (newline) {
                lines.add(new Line(position, line.toString()));
                line = new StringBuilder(120);
                position = i + 1;
            } else {
                line.append(c);
            }
        }

        if (line.length() > 0) {
            lines.add(new Line(position, line.toString()));
        }

        return lines;
    }

    private static class Line {
        final int position;
        final String value;

        Line(int position, String value) {
            this.position = position;
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

}
