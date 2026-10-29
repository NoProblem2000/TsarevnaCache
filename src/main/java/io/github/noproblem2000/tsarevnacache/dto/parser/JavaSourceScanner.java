package io.github.noproblem2000.tsarevnacache.dto.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, dependency free helpers to read the structure of Java source text: comments are removed
 * (string literals, char literals and text blocks are respected), method bodies are located by
 * brace matching and class level statements are collected. It is not a full Java parser, it only
 * needs to understand DTO classes and factories as found in typical code bases.
 */
final class JavaSourceScanner {

    private JavaSourceScanner() {
    }

    /** Replaces comments with spaces (newlines are kept so positions stay meaningful). */
    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            char next = i + 1 < length ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < length && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < length && !(source.charAt(i) == '*' && i + 1 < length && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                int end = literalEnd(source, i);
                out.append(source, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Index just after the string, char or text block literal starting at {@code start}. */
    static int literalEnd(String source, int start) {
        char quote = source.charAt(start);
        if (quote == '"' && source.startsWith("\"\"\"", start)) {
            int end = source.indexOf("\"\"\"", start + 3);
            return end < 0 ? source.length() : end + 3;
        }
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return i + 1;
            }
            i++;
        }
        return source.length();
    }

    /** Index of the brace closing the one at {@code open}, or -1. */
    static int matchingBrace(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = literalEnd(source, i) - 1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** A located method: its parameter name (group 1 of the signature pattern) and body. */
    record MethodBody(String parameter, String body) {
    }

    /**
     * Finds the first method matching {@code signature} (group 1 = parameter name, may be absent)
     * and returns its body without the outer braces.
     */
    static Optional<MethodBody> methodBody(String source, Pattern signature) {
        Matcher matcher = signature.matcher(source);
        while (matcher.find()) {
            int open = source.indexOf('{', matcher.end());
            if (open < 0) {
                return Optional.empty();
            }
            // only whitespace / throws clause between the signature and the body
            String between = source.substring(matcher.end(), open);
            if (!between.matches("\\s*(throws\\s+[\\w.$,\\s]+)?")) {
                continue;
            }
            int close = matchingBrace(source, open);
            if (close < 0) {
                return Optional.empty();
            }
            String parameter = matcher.groupCount() >= 1 ? matcher.group(1) : null;
            return Optional.of(new MethodBody(parameter, source.substring(open + 1, close)));
        }
        return Optional.empty();
    }

    /**
     * Statements directly inside the body of the first class declaration (fields, constants,
     * abstract method declarations); method bodies, initializers and nested types are skipped.
     */
    static List<String> classLevelStatements(String source) {
        Matcher classDeclaration = Pattern.compile("\\b(?:class|enum|record)\\s+[A-Za-z_$][\\w$]*[^{;]*\\{").matcher(source);
        if (!classDeclaration.find()) {
            return List.of();
        }
        int bodyStart = classDeclaration.end();
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int parens = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int end = literalEnd(source, i);
                if (depth == 0) {
                    current.append(source, i, end);
                }
                i = end - 1;
                continue;
            }
            if (depth == 0) {
                if (c == '}' && parens == 0) {
                    break; // end of the class
                }
                if (c == '{' && parens == 0) {
                    depth = 1;
                    current.setLength(0); // method / initializer / nested type header
                    continue;
                }
                if (c == '(') {
                    parens++;
                } else if (c == ')') {
                    parens--;
                } else if (c == ';' && parens == 0) {
                    String statement = current.toString().trim();
                    if (!statement.isEmpty()) {
                        statements.add(statement);
                    }
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    current.setLength(0);
                }
            }
        }
        return statements;
    }

    /** Splits on commas that are not inside {@code <>}, {@code ()} or {@code {}}. */
    static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' || c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** {@code com.example.Some2DTO} / {@code Some2DTO<T>} / {@code Some2DTO[]} -> {@code Some2DTO}. */
    static String simpleTypeName(String type) {
        String withoutGenerics = type.replaceAll("<.*>", "").replace("[]", "").trim();
        int dot = withoutGenerics.lastIndexOf('.');
        return dot >= 0 ? withoutGenerics.substring(dot + 1) : withoutGenerics;
    }

    /** First type argument of a generic type: {@code List<Some2DTO>} -> {@code Some2DTO}. */
    static Optional<String> firstTypeArgument(String type) {
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close < open) {
            return Optional.empty();
        }
        List<String> arguments = splitTopLevel(type.substring(open + 1, close));
        String first = arguments.get(0).trim().replaceFirst("^\\?\\s+extends\\s+", "");
        return first.isEmpty() || first.equals("?") ? Optional.empty() : Optional.of(simpleTypeName(first));
    }

    static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name; // URL -> URL, as java.beans.Introspector does
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
