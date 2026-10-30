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
import java.util.Locale;
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
        if (id == null) {
            String wanted = DtoSchemaRegistryService.normalize(constant);
            id = constants.entrySet().stream()
                    .filter(entry -> DtoSchemaRegistryService.normalize(entry.getKey()).equals(wanted))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        String className = JavaSourceScanner.simpleTypeName(classType);
        if (id == null) {
            warnings.add("Unknown constant '" + label + "' for class " + className);
            return;
        }
        classes.put(className, id);
    }

    // ------------------------------------------------------------------ batch import

    /** Reason of files that are no Hazelcast DTO (enums, interfaces, requests, responses, ...). */
    public static final String NOT_A_DTO = "Not a Hazelcast IdentifiedDataSerializable DTO";
    /** Reason of DTOs whose class id is not known to the {@code DataSerializableFactory}. */
    public static final String NOT_REGISTERED = "Not registered in DataSerializableFactory";
    /** Upper bound of files in one batch. */
    public static final int MAX_BATCH_FILES = 5_000;

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?:\\A|[;}\\n])\\s*((?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*)"
                    + "(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)([^{;]*)\\{");
    private static final Pattern ANNOTATION_TYPE = Pattern.compile("@\\s*interface\\s+[A-Za-z_$][\\w$]*");
    private static final Pattern IDENTIFIED_TYPE = Pattern.compile("\\bIdentifiedData(?:Serializable|DTO)\\b");
    private static final Pattern FACTORY_TYPE = Pattern.compile("\\bDataSerializableFactory\\b");
    private static final Pattern CREATE_METHOD = Pattern.compile("\\bcreate\\s*\\(\\s*(?:final\\s+)?int\\s+[\\w$]+\\s*\\)");
    private static final Pattern FIELD_WARNING = Pattern.compile(
            "^(?:Unsupported|Field #|readData contains|readData and writeData|readData was not understood)");

    /**
     * Imports a whole folder of {@code .java} files in three passes, so the order of the files never
     * matters:
     * <ol>
     *   <li><b>Factory.</b> A {@code DataSerializableFactory} (a class with {@code FACTORY_ID} and
     *       {@code create(int typeId)}) is parsed and stored first, so every class id is known before
     *       any DTO is read.</li>
     *   <li><b>Filter and parse.</b> Enums, interfaces, records, abstract classes and plain classes
     *       ({@code CustomRequest}, {@code CustomResponse}, ...) that do not implement
     *       {@code IdentifiedDataSerializable} / {@code IdentifiedDataDTO} or lack
     *       {@code readData}/{@code writeData} and {@code getClassId()} are ignored ({@value #NOT_A_DTO}).
     *       DTOs whose class id the factory does not know are ignored too ({@value #NOT_REGISTERED}).
     *       All others are parsed.</li>
     *   <li><b>Save and link.</b> All parsed schemas are saved in one step and the nested
     *       {@code nestedClassId} of every schema is resolved by class name across the whole registry.</li>
     * </ol>
     */
    public BatchImportResultDTO importJavaFiles(List<JavaFileSourceDTO> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(ErrorCode.VALUE_REQUIRED, "No .java files were sent");
        }
        if (files.size() > MAX_BATCH_FILES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "At most " + MAX_BATCH_FILES + " files per import",
                    Map.of("files", files.size()));
        }
        List<IgnoredFileDTO> ignored = new ArrayList<>();
        List<BatchImportErrorDTO> errors = new ArrayList<>();
        List<ScannedFile> scanned = new ArrayList<>();
        for (JavaFileSourceDTO file : files) {
            String name = file == null || file.fileName() == null || file.fileName().isBlank() ? "(unnamed)" : file.fileName();
            String content = file == null ? null : file.content();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".java")) {
                ignored.add(new IgnoredFileDTO(name, null, "Not a .java file", null));
            } else if (content == null || content.isBlank()) {
                ignored.add(new IgnoredFileDTO(name, null, "Empty file", null));
            } else if (content.length() > 1_000_000) {
                ignored.add(new IgnoredFileDTO(name, null, "Larger than 1 MB", null));
            } else {
                scanned.add(scan(name, content));
            }
        }

        // pass 1: the factory first, so the class ids are known before the DTOs are parsed
        boolean factoryUpdated = false;
        String factoryFile = null;
        FactoryParseResult factory = null;
        for (ScannedFile file : scanned) {
            if (!file.factory()) {
                continue;
            }
            if (factory != null) {
                ignored.add(new IgnoredFileDTO(file.name(), file.className(), "Additional DataSerializableFactory",
                        "only the factory mapping of " + factoryFile + " is kept"));
                continue;
            }
            try {
                factory = parseFactorySource(file.content());
                registry.updateFactory(factory.factoryId(), factory.constants(), factory.classes());
                factoryUpdated = true;
                factoryFile = file.name();
            } catch (ApiException e) {
                errors.add(new BatchImportErrorDTO(file.name(), file.className(), "Factory not understood: " + e.getMessage(), false));
            }
        }

        // pass 2: filter and parse the DTOs
        Map<DtoClassSchema.SchemaKey, String> fileOfKey = new LinkedHashMap<>();
        List<DtoClassSchema> parsed = new ArrayList<>();
        Map<String, Integer> knownIds = new LinkedHashMap<>(registry.factoryConstants());
        knownIds.putAll(registry.factoryClasses());
        boolean factoryKnown = !knownIds.isEmpty();
        for (ScannedFile file : scanned) {
            if (file.factory()) {
                continue;
            }
            String notDto = notADtoReason(file);
            if (notDto != null) {
                ignored.add(new IgnoredFileDTO(file.name(), file.className(), NOT_A_DTO, notDto));
                continue;
            }
            DtoParseResult result;
            try {
                result = parseDtoClassSource(file.content());
            } catch (ApiException e) {
                errors.add(new BatchImportErrorDTO(file.name(), file.className(), e.getMessage(), false));
                continue;
            }
            DtoClassSchema schema = result.schema();
            if (schema.classId() == null) {
                ignored.add(new IgnoredFileDTO(file.name(), schema.className(), NOT_REGISTERED,
                        result.warnings().stream().filter(w -> w.startsWith("getClassId()")).findFirst()
                                .orElse("getClassId() does not return a factory constant")));
                continue;
            }
            if (factoryKnown && schema.factoryId() == registry.factoryId() && !knownIds.containsValue(schema.classId())) {
                ignored.add(new IgnoredFileDTO(file.name(), schema.className(), NOT_REGISTERED,
                        "classId " + schema.classId() + " is not created by the factory"));
                continue;
            }
            String previous = fileOfKey.putIfAbsent(schema.key(), file.name());
            if (previous != null) {
                errors.add(new BatchImportErrorDTO(file.name(), schema.className(), "classId " + schema.classId()
                        + " is already used by " + previous + " in this import; this file was skipped", false));
                continue;
            }
            try {
                registry.validate(schema);
            } catch (ApiException e) {
                fileOfKey.remove(schema.key());
                errors.add(new BatchImportErrorDTO(file.name(), schema.className(), e.getMessage(), false));
                continue;
            }
            parsed.add(schema);
            result.warnings().stream()
                    .filter(warning -> FIELD_WARNING.matcher(warning).find())
                    .forEach(warning -> errors.add(new BatchImportErrorDTO(file.name(), schema.className(), warning, true)));
        }

        // pass 3: save everything at once; nested class ids are linked across the whole registry
        DtoSchemaRegistryService.BatchOutcome outcome = registry.saveAll(parsed);
        List<DtoClassSchema> imported = outcome.saved().stream()
                .sorted(Comparator.comparing(DtoClassSchema::key))
                .toList();
        return new BatchImportResultDTO(files.size(), imported.size(), imported,
                outcome.replaced().stream().map(Object::toString).toList(),
                outcome.relinked().stream().map(Object::toString).toList(),
                factoryUpdated, factoryFile, factory == null ? null : factory.factoryId(),
                factory == null ? 0 : factory.constants().size(), ignored.size(), ignored, errors);
    }

    /** Primary type of a file and whether it is a factory. */
    private ScannedFile scan(String name, String content) {
        String code = JavaSourceScanner.blankLiterals(JavaSourceScanner.stripComments(content));
        String declarations = ANNOTATION.matcher(ANNOTATION_TYPE.matcher(code).replaceAll(" interface Annotation {"))
                .replaceAll(" ");
        Matcher type = TYPE_DECLARATION.matcher(declarations);
        if (!type.find()) {
            return new ScannedFile(name, content, code, null, null, false, "", false);
        }
        String modifiers = type.group(1);
        String kind = type.group(2);
        String header = type.group(4);
        boolean factory = kind.equals("class")
                && (FACTORY_TYPE.matcher(header).find() || FACTORY_ID.matcher(code).find())
                && CREATE_METHOD.matcher(code).find()
                && !GET_CLASS_ID.matcher(code).find();
        return new ScannedFile(name, content, code, type.group(3), kind, modifiers.contains("abstract"), header, factory);
    }

    /** Why a file is not a Hazelcast DTO, or {@code null} when it is one. */
    private static String notADtoReason(ScannedFile file) {
        if (file.kind() == null) {
            return "no class declaration";
        }
        if (!file.kind().equals("class")) {
            return file.kind();
        }
        if (file.isAbstract()) {
            return "abstract class";
        }
        boolean hasReadOrWrite = READ_DATA.matcher(file.code()).find() || WRITE_DATA.matcher(file.code()).find();
        boolean hasClassId = GET_CLASS_ID.matcher(file.code()).find();
        boolean implementsIdentified = IDENTIFIED_TYPE.matcher(file.header()).find()
                || (hasClassId && GET_FACTORY_ID.matcher(file.code()).find());
        if (!implementsIdentified) {
            return "does not implement IdentifiedDataSerializable";
        }
        if (!hasReadOrWrite) {
            return "no readData/writeData";
        }
        if (!hasClassId) {
            return "no getClassId()";
        }
        return null;
    }

    /** A file of a batch, pre-scanned once. */
    private record ScannedFile(String name, String content, String code, String className, String kind,
                               boolean isAbstract, String header, boolean factory) {
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

        Matcher classMatcher = CLASS_NAME.matcher(JavaSourceScanner.blankLiterals(code));
        if (!classMatcher.find()) {
            throw new ApiException(ErrorCode.DTO_SOURCE_NOT_RECOGNIZED, "No class declaration found");
        }
        String className = classMatcher.group(1);

        Map<String, Integer> classConstants = new LinkedHashMap<>();
        Map<String, String> declaredTypes = declaredFieldTypes(code, classConstants);

        // 1) the constant returned by getClassId(), 2) the class name created by the factory
        // (case X -> new SomeDTO()); both matched without regard to case and underscores.
        // There is no default: an unresolved class id stays null and must be chosen in the UI.
        Integer classId = idFromMethod(code, GET_CLASS_ID, classConstants, warnings, "getClassId()");
        if (classId == null) {
            classId = registry.resolveClassId(className).orElse(null);
            if (classId != null) {
                warnings.removeIf(warning -> warning.startsWith("getClassId()"));
                warnings.add("classId " + classId + " taken from the factory mapping of " + className);
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
        if (local == null) {
            String wanted = DtoSchemaRegistryService.normalize(constant);
            local = classConstants.entrySet().stream()
                    .filter(entry -> DtoSchemaRegistryService.normalize(entry.getKey()).equals(wanted))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
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
