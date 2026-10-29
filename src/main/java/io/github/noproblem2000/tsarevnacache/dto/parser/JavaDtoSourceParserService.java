package io.github.noproblem2000.tsarevnacache.dto.parser;

import io.github.noproblem2000.tsarevnacache.api.error.ApiException;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import io.github.noproblem2000.tsarevnacache.dto.DtoClassSchema;
import io.github.noproblem2000.tsarevnacache.dto.DtoFieldSchema;
import io.github.noproblem2000.tsarevnacache.dto.DtoFieldType;
import io.github.noproblem2000.tsarevnacache.dto.DtoSchemaRegistryService;
import io.github.noproblem2000.tsarevnacache.dto.parser.JavaSourceScanner.MethodBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns Java source pasted from an IDE into DTO schemas: the project's
 * {@code DataSerializableFactory} (factory id, class id constants, which class each id creates)
 * and DTO classes (class name, class id, field order and wire types from {@code readData} or,
 * as a fallback, {@code writeData}).
 *
 * <p>The source is only analysed textually; it is never compiled or executed.
 */
@Service
public class JavaDtoSourceParserService {

    private static final Pattern CLASS_NAME = Pattern.compile("\\b(?:class|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern FACTORY_ID = Pattern.compile("\\bint\\s+FACTORY_ID\\s*=\\s*(-?\\d+)\\s*;");
    private static final Pattern INT_CONSTANT = Pattern.compile(
            "\\b(?:(?:public|protected|private)\\s+)?(?:static\\s+final|final\\s+static)\\s+int\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(-?\\d+)\\s*;");
    private static final Pattern INTERFACE_CONSTANT = Pattern.compile("^\\s*int\\s+([A-Z_$][\\w$]*)\\s*=\\s*(-?\\d+)\\s*;", Pattern.MULTILINE);
    private static final Pattern SWITCH_CASE = Pattern.compile(
            "\\bcase\\s+([\\w$.\\s,]+?)\\s*(?:->|:)\\s*(?:\\{\\s*)?(?:return\\s+)?new\\s+([\\w$.]+)\\s*(?:<[^>]*>)?\\s*\\(");
    private static final Pattern ARRAY_CONSTRUCTOR = Pattern.compile(
            "\\[\\s*([\\w$.]+)\\s*]\\s*=\\s*(?:\\([^)]*\\)|[\\w$]+)\\s*->\\s*(?:\\{\\s*return\\s+)?new\\s+([\\w$.]+)\\s*\\(");
    private static final Pattern MAP_CONSTRUCTOR = Pattern.compile(
            "\\bput\\s*\\(\\s*([\\w$.]+)\\s*,\\s*(?:([\\w$.]+)\\s*::\\s*new|(?:\\([^)]*\\)|[\\w$]+)\\s*->\\s*new\\s+([\\w$.]+)\\s*\\()");

    private static final Pattern READ_DATA = Pattern.compile(
            "\\bvoid\\s+readData\\s*\\(\\s*(?:final\\s+)?[\\w.$]+\\s+([\\w$]+)\\s*\\)");
    private static final Pattern WRITE_DATA = Pattern.compile(
            "\\bvoid\\s+writeData\\s*\\(\\s*(?:final\\s+)?[\\w.$]+\\s+([\\w$]+)\\s*\\)");
    private static final Pattern GET_CLASS_ID = Pattern.compile("\\bint\\s+getClassId\\s*\\(\\s*\\)");
    private static final Pattern GET_FACTORY_ID = Pattern.compile("\\bint\\s+getFactoryId\\s*\\(\\s*\\)");
    private static final Pattern RETURN_EXPRESSION = Pattern.compile("\\breturn\\s+([^;]+?)\\s*;");

    private static final Pattern HELPER_READ = Pattern.compile(
            "(?:\\b[\\w$]*SerializationUtils\\s*\\.\\s*)?(?:<[^;()]*>\\s*)?\\b(readNullable[A-Za-z]+)\\s*\\(\\s*([\\w$]+)\\s*\\)");
    private static final Pattern RAW_READ = Pattern.compile("\\b([\\w$]+)\\s*\\.\\s*(read[A-Za-z]+)\\s*\\(\\s*\\)");
    private static final Pattern HELPER_WRITE = Pattern.compile(
            "(?:\\b[\\w$]*SerializationUtils\\s*\\.\\s*)?\\b(writeNullable[A-Za-z]+)\\s*\\(\\s*([\\w$]+)\\s*,\\s*([^;]+?)\\)\\s*;");
    private static final Pattern RAW_WRITE = Pattern.compile("\\b([\\w$]+)\\s*\\.\\s*(write[A-Za-z]+)\\s*\\(\\s*([^;]+?)\\)\\s*;");
    private static final Pattern ASSIGNMENT_TARGET = Pattern.compile(
            "(?:this\\s*\\.\\s*)?([\\w$]+)\\s*=\\s*(?:\\(\\s*([\\w$.<>\\[\\], ?]+?)\\s*\\)\\s*)?$");
    private static final Pattern SETTER_TARGET = Pattern.compile(
            "(?:this\\s*\\.\\s*)?set([\\w$]+)\\s*\\(\\s*(?:\\(\\s*([\\w$.<>\\[\\], ?]+?)\\s*\\)\\s*)?$");
    private static final Pattern CONTROL_FLOW = Pattern.compile("\\b(for|while|if|switch|do)\\b\\s*[({]");
    private static final Pattern ANNOTATION = Pattern.compile("@[\\w.$]+(?:\\s*\\([^)]*\\))?");
    private static final Pattern MODIFIER = Pattern.compile("^(?:public|protected|private|static|final|transient|volatile)\\s+");

    private static final Map<String, DtoFieldType> READ_METHODS = new LinkedHashMap<>();
    private static final Map<String, DtoFieldType> WRITE_METHODS = new LinkedHashMap<>();

    static {
        for (DtoFieldType type : DtoFieldType.values()) {
            READ_METHODS.put(type.readMethod(), type);
            WRITE_METHODS.put(type.writeMethod(), type);
        }
        READ_METHODS.put("readUTF", DtoFieldType.RAW_STRING);
        WRITE_METHODS.put("writeUTF", DtoFieldType.RAW_STRING);
    }

    private final DtoSchemaRegistryService registry;

    public JavaDtoSourceParserService(DtoSchemaRegistryService registry) {
        this.registry = registry;
    }

    // ------------------------------------------------------------------ factory

    /**
     * Parses {@code DataSerializableFactory.java}: {@code FACTORY_ID}, all {@code int} constants and
     * the class created for each id ({@code case X -> new SomeDTO()}, classic {@code case X: return
     * new SomeDTO();}, constructor arrays and {@code map.put(X, SomeDTO::new)}).
     *
     * @throws ApiException with {@link ErrorCode#DTO_SOURCE_NOT_RECOGNIZED} when nothing usable is found
     */
    public FactoryParseResult parseFactorySource(String javaCode) {
        String code = JavaSourceScanner.stripComments(requireSource(javaCode));
        List<String> warnings = new ArrayList<>();

        Matcher factoryMatcher = FACTORY_ID.matcher(code);
        int factoryId;
        if (factoryMatcher.find()) {
            factoryId = Integer.parseInt(factoryMatcher.group(1));
        } else {
            factoryId = registry.factoryId();
            warnings.add("FACTORY_ID not found, keeping factory id " + factoryId);
        }

        Map<String, Integer> constants = new LinkedHashMap<>();
        collectConstants(INT_CONSTANT.matcher(code), constants);
        collectConstants(INTERFACE_CONSTANT.matcher(code), constants);
        constants.remove("FACTORY_ID");

        Map<String, Integer> classes = new LinkedHashMap<>();
        Matcher cases = SWITCH_CASE.matcher(code);
        while (cases.find()) {
            for (String label : cases.group(1).split(",")) {
                mapClass(label.trim(), cases.group(2), constants, classes, warnings);
            }
        }
        Matcher arrays = ARRAY_CONSTRUCTOR.matcher(code);
        while (arrays.find()) {
            mapClass(arrays.group(1), arrays.group(2), constants, classes, warnings);
        }
        Matcher puts = MAP_CONSTRUCTOR.matcher(code);
        while (puts.find()) {
            mapClass(puts.group(1), puts.group(2) != null ? puts.group(2) : puts.group(3), constants, classes, warnings);
        }

        if (constants.isEmpty() && classes.isEmpty()) {
            throw new ApiException(ErrorCode.DTO_SOURCE_NOT_RECOGNIZED,
                    "No class id constants or 'case X -> new SomeDTO()' mappings found in the factory source");
        }
        for (Map.Entry<String, Integer> constant : constants.entrySet()) {
            if (!classes.containsValue(constant.getValue())) {
                warnings.add("Constant " + constant.getKey() + " = " + constant.getValue() + " is not mapped to a class");
            }
        }
        return new FactoryParseResult(factoryId, constants, classes, warnings);
    }

    private static void collectConstants(Matcher matcher, Map<String, Integer> constants) {
        while (matcher.find()) {
            constants.putIfAbsent(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
    }

    private static void mapClass(String label, String classType, Map<String, Integer> constants,
                                 Map<String, Integer> classes, List<String> warnings) {
        if (label.isEmpty() || label.equals("default")) {
            return;
        }
        String constant = lastSegment(label);
        Integer id = constant.matches("-?\\d+") ? Integer.valueOf(constant) : constants.get(constant);
        String className = JavaSourceScanner.simpleTypeName(classType);
        if (id == null) {
            warnings.add("Unknown constant '" + label + "' for class " + className);
            return;
        }
        classes.put(className, id);
    }

    // ------------------------------------------------------------------ DTO class

    /**
     * Parses a DTO class: class name, {@code getClassId()}/{@code getFactoryId()}, declared field
     * types (for nested DTO classes) and the ordered fields of {@code readData} (or {@code writeData}).
     *
     * @throws ApiException with {@link ErrorCode#DTO_SOURCE_NOT_RECOGNIZED} without a class or fields
     */
    public DtoParseResult parseDtoClassSource(String javaCode) {
        String raw = requireSource(javaCode);
        String code = JavaSourceScanner.stripComments(raw);
        List<String> warnings = new ArrayList<>();

        Matcher classMatcher = CLASS_NAME.matcher(code);
        if (!classMatcher.find()) {
            throw new ApiException(ErrorCode.DTO_SOURCE_NOT_RECOGNIZED, "No class declaration found");
        }
        String className = classMatcher.group(1);

        Map<String, Integer> classConstants = new LinkedHashMap<>();
        Map<String, String> declaredTypes = declaredFieldTypes(code, classConstants);

        Integer classId = idFromMethod(code, GET_CLASS_ID, classConstants, warnings, "getClassId()");
        if (classId == null) {
            classId = registry.resolveClassId(className).orElse(null);
            if (classId != null) {
                warnings.removeIf(warning -> warning.startsWith("getClassId()"));
                warnings.add("classId taken from the factory mapping of " + className);
            }
        }
        Integer factoryId = idFromMethod(code, GET_FACTORY_ID, classConstants, new ArrayList<>(), "getFactoryId()");
        if (factoryId == null) {
            factoryId = registry.factoryId();
        }

        Optional<MethodBody> readData = JavaSourceScanner.methodBody(code, READ_DATA);
        Optional<MethodBody> writeData = JavaSourceScanner.methodBody(code, WRITE_DATA);
        List<DtoFieldSchema> readFields = readData.map(body -> fieldsFromRead(body, declaredTypes, warnings)).orElse(List.of());
        List<String> writeWarnings = new ArrayList<>();
        List<DtoFieldSchema> writeFields = writeData.map(body -> fieldsFromWrite(body, declaredTypes, writeWarnings)).orElse(List.of());

        List<DtoFieldSchema> fields;
        String source;
        if (!readFields.isEmpty()) {
            fields = readFields;
            source = "readData";
            if (!writeFields.isEmpty() && !sameTypes(readFields, writeFields)) {
                warnings.add("readData and writeData use different field types/order - the schema follows readData");
            }
        } else if (!writeFields.isEmpty()) {
            fields = writeFields;
            source = "writeData";
            warnings.addAll(writeWarnings);
            if (readData.isPresent()) {
                warnings.add("readData was not understood, the field order is taken from writeData");
            }
        } else {
            throw new ApiException(ErrorCode.DTO_SOURCE_NOT_RECOGNIZED, "No SerializationUtils / ObjectDataInput calls found in "
                    + "readData or writeData of " + className);
        }
        if (classId == null) {
            warnings.add("classId could not be resolved; parse the DataSerializableFactory first or enter it manually");
        }

        DtoClassSchema schema = new DtoClassSchema(factoryId, classId, className, fields, raw);
        boolean complete = classId != null && warnings.stream().noneMatch(warning -> warning.startsWith("Unsupported"));
        return new DtoParseResult(schema, source, complete, warnings);
    }

    private Integer idFromMethod(String code, Pattern signature, Map<String, Integer> classConstants,
                                 List<String> warnings, String label) {
        Optional<MethodBody> body = JavaSourceScanner.methodBody(code, signature);
        if (body.isEmpty()) {
            warnings.add(label + " not found");
            return null;
        }
        Matcher returned = RETURN_EXPRESSION.matcher(body.get().body());
        if (!returned.find()) {
            warnings.add(label + " has no return statement");
            return null;
        }
        String expression = returned.group(1).trim();
        if (expression.matches("-?\\d+")) {
            return Integer.valueOf(expression);
        }
        String constant = lastSegment(expression);
        if (constant.equals("FACTORY_ID")) {
            return registry.factoryId();
        }
        Integer local = classConstants.get(constant);
        if (local != null) {
            return local;
        }
        Optional<Integer> fromFactory = registry.resolveConstant(constant);
        if (fromFactory.isPresent()) {
            return fromFactory.get();
        }
        warnings.add(label + " returns '" + expression + "', which is not a known factory constant");
        return null;
    }

    /** Field name -> declared type of the instance fields (static int constants go to {@code constants}). */
    private static Map<String, String> declaredFieldTypes(String code, Map<String, Integer> constants) {
        Map<String, String> types = new LinkedHashMap<>();
        for (String statement : JavaSourceScanner.classLevelStatements(code)) {
            String text = ANNOTATION.matcher(statement).replaceAll(" ").trim();
            boolean isStatic = false;
            Matcher modifier = MODIFIER.matcher(text);
            while (modifier.find()) {
                isStatic |= modifier.group().trim().equals("static");
                text = text.substring(modifier.end()).trim();
                modifier = MODIFIER.matcher(text);
            }
            int equals = text.indexOf('=');
            String head = equals >= 0 ? text.substring(0, equals) : text;
            if (head.contains("(")) {
                continue; // method declaration
            }
            List<String> declarators = JavaSourceScanner.splitTopLevel(text);
            Matcher first = Pattern.compile("^(.+?)\\s+([A-Za-z_$][\\w$]*)\\s*(?:\\[\\s*])*\\s*(?:=(.*))?$", Pattern.DOTALL)
                    .matcher(declarators.get(0).trim());
            if (!first.matches()) {
                continue;
            }
            String type = first.group(1).trim();
            List<String[]> names = new ArrayList<>();
            names.add(new String[] {first.group(2), first.group(3)});
            for (int i = 1; i < declarators.size(); i++) {
                Matcher more = Pattern.compile("^\\s*([A-Za-z_$][\\w$]*)\\s*(?:\\[\\s*])*\\s*(?:=(.*))?$", Pattern.DOTALL)
                        .matcher(declarators.get(i));
                if (more.matches()) {
                    names.add(new String[] {more.group(1), more.group(2)});
                }
            }
            for (String[] name : names) {
                if (isStatic) {
                    if (type.equals("int") && name[1] != null && name[1].trim().matches("-?\\d+")) {
                        constants.put(name[0], Integer.parseInt(name[1].trim()));
                    }
                } else {
                    types.put(name[0], type);
                }
            }
        }
        return types;
    }

    private List<DtoFieldSchema> fieldsFromRead(MethodBody method, Map<String, String> declaredTypes, List<String> warnings) {
        String body = method.body();
        String input = method.parameter();
        if (CONTROL_FLOW.matcher(body).find()) {
            warnings.add("readData contains conditions or loops; only straight-line SerializationUtils calls are modelled - verify the schema");
        }
        List<Call> calls = new ArrayList<>();
        Matcher helpers = HELPER_READ.matcher(body);
        while (helpers.find()) {
            if (!helpers.group(2).equals(input)) {
                continue;
            }
            DtoFieldType type = READ_METHODS.get(helpers.group(1));
            if (type == null || type.isRaw()) {
                warnings.add("Unsupported helper " + helpers.group(1) + "(" + input + ") - not part of SerializationUtils");
                continue;
            }
            calls.add(new Call(helpers.start(), type, helpers.group(1)));
        }
        Matcher raw = RAW_READ.matcher(body);
        while (raw.find()) {
            if (!raw.group(1).equals(input)) {
                continue;
            }
            DtoFieldType type = READ_METHODS.get(raw.group(2));
            if (type == null || !type.isRaw()) {
                warnings.add("Unsupported call " + input + "." + raw.group(2) + "() - add the field manually if it is needed");
                continue;
            }
            calls.add(new Call(raw.start(), type, raw.group(2)));
        }
        calls.sort(Comparator.comparingInt(Call::position));

        List<DtoFieldSchema> fields = new ArrayList<>();
        for (Call call : calls) {
            String prefix = statementPrefix(body, call.position());
            String fieldName = null;
            String cast = null;
            Matcher assignment = ASSIGNMENT_TARGET.matcher(prefix);
            Matcher setter = SETTER_TARGET.matcher(prefix);
            if (assignment.find()) {
                fieldName = assignment.group(1);
                cast = assignment.group(2);
            } else if (setter.find()) {
                fieldName = JavaSourceScanner.decapitalize(setter.group(1));
                cast = setter.group(2);
            }
            fields.add(field(fields.size(), fieldName, call, declaredTypes, cast, warnings));
        }
        return fields;
    }

    private List<DtoFieldSchema> fieldsFromWrite(MethodBody method, Map<String, String> declaredTypes, List<String> warnings) {
        String body = method.body();
        String output = method.parameter();
        List<Call> calls = new ArrayList<>();
        Matcher helpers = HELPER_WRITE.matcher(body);
        while (helpers.find()) {
            if (!helpers.group(2).equals(output)) {
                continue;
            }
            DtoFieldType type = WRITE_METHODS.get(helpers.group(1));
            if (type == null || type.isRaw()) {
                warnings.add("Unsupported helper " + helpers.group(1) + " - not part of SerializationUtils");
                continue;
            }
            calls.add(new Call(helpers.start(), type, helpers.group(3)));
        }
        Matcher raw = RAW_WRITE.matcher(body);
        while (raw.find()) {
            if (!raw.group(1).equals(output)) {
                continue;
            }
            DtoFieldType type = WRITE_METHODS.get(raw.group(2));
            if (type == null || !type.isRaw()) {
                warnings.add("Unsupported call " + output + "." + raw.group(2) + "(...) - add the field manually if it is needed");
                continue;
            }
            calls.add(new Call(raw.start(), type, raw.group(3)));
        }
        calls.sort(Comparator.comparingInt(Call::position));

        List<DtoFieldSchema> fields = new ArrayList<>();
        for (Call call : calls) {
            fields.add(field(fields.size(), fieldFromExpression(call.detail()), call, declaredTypes, null, warnings));
        }
        return fields;
    }

    private DtoFieldSchema field(int order, String fieldName, Call call, Map<String, String> declaredTypes,
                                 String cast, List<String> warnings) {
        String name = fieldName;
        if (name == null) {
            name = "field" + order;
            warnings.add("Field #" + order + " (" + call.type() + "): target not recognized, named '" + name + "'");
        }
        String nestedClassName = null;
        Integer nestedClassId = null;
        if (call.type().hasNestedObjects()) {
            String declared = declaredTypes.getOrDefault(name, cast);
            if (declared != null) {
                nestedClassName = call.type() == DtoFieldType.NULLABLE_LIST
                        ? JavaSourceScanner.firstTypeArgument(declared).orElse(null)
                        : JavaSourceScanner.simpleTypeName(declared);
            }
            if (nestedClassName != null && (nestedClassName.equals("Object") || nestedClassName.equals("Serializable"))) {
                nestedClassName = null;
            }
            if (nestedClassName != null) {
                nestedClassId = registry.resolveClassId(nestedClassName).orElse(null);
            }
        }
        return new DtoFieldSchema(order, name, call.type(), nestedClassId, nestedClassName);
    }

    /** {@code this.amount} / {@code amount} / {@code getAmount()} / {@code isActive()} -> field name. */
    private static String fieldFromExpression(String expression) {
        String text = expression.trim();
        Matcher getter = Pattern.compile("^(?:this\\s*\\.\\s*)?(?:get|is)([A-Z][\\w$]*)\\s*\\(\\s*\\)$").matcher(text);
        if (getter.matches()) {
            return JavaSourceScanner.decapitalize(getter.group(1));
        }
        Matcher field = Pattern.compile("^(?:\\([^)]*\\)\\s*)?(?:this\\s*\\.\\s*)?([A-Za-z_$][\\w$]*)$").matcher(text);
        return field.matches() ? field.group(1) : null;
    }

    private static String statementPrefix(String body, int position) {
        int start = Math.max(body.lastIndexOf(';', position - 1), Math.max(body.lastIndexOf('{', position - 1),
                body.lastIndexOf('}', position - 1)));
        return body.substring(start + 1, position).trim();
    }

    private static boolean sameTypes(List<DtoFieldSchema> left, List<DtoFieldSchema> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).fieldType() != right.get(i).fieldType()) {
                return false;
            }
        }
        return true;
    }

    private static String lastSegment(String expression) {
        String trimmed = expression.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(dot + 1).trim() : trimmed;
    }

    private static String requireSource(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "Java source is empty");
        }
        if (javaCode.length() > 1_000_000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Java source is larger than 1 MB");
        }
        return javaCode;
    }

    /** A recognized read/write call: position in the body, wire type, method name or written expression. */
    private record Call(int position, DtoFieldType type, String detail) {
    }
}
