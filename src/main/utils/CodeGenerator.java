package main.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import main.database.MetaDataAccessObject;
import main.parser.GroupingInformation;
import main.parser.ParsedQuery;
import main.parser.TableInformation;
import main.readwrite.ReadWrite;

/**
 * The class generates classes that are used to evaluate the query. It uses
 * JavaPoet libraries to generate java code. As the generated classes are
 * complied, the program must run using a JDK and not A JRE.
 *
 * @author R&B
 *
 */
public class CodeGenerator {

    private static final Logger LOG = Logger.getLogger(CodeGenerator.class.getCanonicalName());

    private static final String PACKAGE = "main.generated";

    private static final String INDENT = "    ";

    // Gets the system compiler. Required JDK
    private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

    // Used to retrieve errors during compilation of generated clases
    private static final DiagnosticCollector<JavaFileObject> DIGNOSTIC = new DiagnosticCollector<>();

    private static final StandardJavaFileManager FILE_MANAGER = COMPILER.getStandardFileManager(DIGNOSTIC, null, null);

    private static final File SOURCE = new File("./src/");

    private static final File TARGET = new File("./target/");

    private static final String GENERATED_SOURCE = SOURCE.getAbsolutePath() + "/main/generated/";

    private static final String GENERATED_TARGET = TARGET.getAbsolutePath() + "/main/generated/";

    private static final String AGGREGATE_REGEX = "avg|max|min|sum|count";

    private static final String COMPOSITE_ENTITY_NAME = "CompositeEntity";

    private static final String UTILS_PACKAGE = "java.util";

    private static final String AVG = "avg";

    private static final String MAX = "max";

    private static final String MIN = "min";

    private static final String SUM = "sum";

    private static final String COUNT = "count";

    private static final String MF_TABLE = "mfTable";

    private static final String DAO_NAME = "DataAccessObject";

    private static final String IS_CSV = "if (isCsv)";

    private static final String NEW_ROW = "newRow";

    private CodeGenerator() {
        // Private constructor to prevent object creation
    }

    /**
     * Clears the directory containing the old generated files.
     *
     */
    public static void cleanDirectory() {
        File generatedFileDir = new File(GENERATED_SOURCE);
        if (!generatedFileDir.exists()) {
            return;
        }

        try (Stream<Path> stream = Files.walk(generatedFileDir.toPath())) {
            stream.filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not delete old Java file.", e);
            CommonUtils.exit(1);
        }

        try (Stream<Path> stream = Files.walk(Paths.get(GENERATED_TARGET))) {
            stream.filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not delete old Java file.", e);
            CommonUtils.exit(1);
        }
    }

    /**
     * Creates a POJO file which represents the Java equivalent of the table. It
     * accepts the table name and converts it into Java class name format. The
     * metadata of the table containing the column names and their data type is used
     * to define fields and methods. The resultant file is created in the generated
     * package of the source code.
     *
     * @param parsedQuery
     * @param dao
     * @return name of the generated java file
     */
    public static void createEntities(ParsedQuery parsedQuery, MetaDataAccessObject dao) {
        for (Entry<String, TableInformation> entrySet : parsedQuery.getRelations().entrySet()) {
            TableInformation information = entrySet.getValue();
            dao.populateTableMetadata(information);

            generateEntity(entrySet.getKey(), information);
        }
    }

    /**
     * Creates a POJO file which represents the Java equivalent of the joined
     * tables. This class object is also used as the values in E/MF-Table.
     *
     * @param parsedQuery
     * @return
     */
    public static void createCompositeEntity(ParsedQuery parsedQuery) {
        TypeSpec.Builder entity = TypeSpec.classBuilder(COMPOSITE_ENTITY_NAME).addModifiers(Modifier.PUBLIC);
        TypeName compositeType = ClassName.get(PACKAGE, COMPOSITE_ENTITY_NAME);
        Map<String, Class<?>> allFields = new HashMap<>();

        addFieldsAndConstructor(entity, compositeType, allFields, parsedQuery.getRelations(), parsedQuery.getGroups());
        addEqualsAndHashCodeMethod(entity, compositeType, parsedQuery.getGroupingAttributes());
        addCompareToMetod(entity, compositeType, parsedQuery.getOrderByAttributes(), parsedQuery.getOrderMultiplier());
        addToStringMethod(entity, allFields, parsedQuery.getProjections(), parsedQuery.getHeaders());

        createAndCompileJavaFile(entity.build());
    }

    /**
     * Create a class to connect to the database and fetch rows for all involved
     * tables. The blockSize parameter in the properties file determines the number
     * of rows fetched at one time.
     *
     * @param parsedQuery
     * @return
     */
    public static void createDataAccessObject(ParsedQuery parsedQuery) {
        TypeSpec.Builder entityBuilder = TypeSpec.classBuilder(DAO_NAME).addModifiers(Modifier.PUBLIC);

        addClassFieldsAndConstructor(entityBuilder);
        addMethods(entityBuilder);
        addReadRowMethods(entityBuilder, parsedQuery);

        createAndCompileJavaFile(entityBuilder.build());
    }

    /**
     * Create a class to maintain the MF-Table, fetch rows and update aggregate
     * values. The loops are generated based on the dependencies between the
     * grouping variables.
     *
     * @param parsedQuery
     * @return
     */
    public static void createEvaluationEngine(ParsedQuery parsedQuery) {
        TypeSpec.Builder entityBuilder = TypeSpec.classBuilder("EvaluationEngine").addModifiers(Modifier.PUBLIC);
        TypeName compositeType = ClassName.get(PACKAGE, COMPOSITE_ENTITY_NAME);
        ParameterizedTypeName mfTableType = null;

        if (parsedQuery.getOrderByAttributes().isEmpty()) {
            ClassName map = ClassName.get(UTILS_PACKAGE, "Map");
            ClassName integer = ClassName.get("java.lang", "Integer");
            mfTableType = ParameterizedTypeName.get(map, integer, compositeType);
        } else {
            ClassName set = ClassName.get(UTILS_PACKAGE, "Set");
            mfTableType = ParameterizedTypeName.get(set, compositeType);
        }

        Set<TableInformation> sortedSet = new TreeSet<>();
        sortedSet.addAll(parsedQuery.getRelations().values());

        entityBuilder.addField(getLogField("EvaluationEngine"));
        addComputeGroupsMethod(entityBuilder, parsedQuery, compositeType, mfTableType);
        addStreamTableBlocksMethod(entityBuilder, parsedQuery, compositeType, mfTableType, sortedSet);
        addMainMethod(entityBuilder, parsedQuery, mfTableType, sortedSet);
        addDisplayResultMethod(entityBuilder, parsedQuery, compositeType, mfTableType);

        createAndCompileJavaFile(entityBuilder.build());
    }

    private static void generateEntity(String classsuffix, TableInformation information) {
        String tableName = information.getTableName();
        String generatedClassName = CommonUtils.toCamelCase(tableName, true, false, classsuffix);

        Map<String, Class<?>> colmnDataTypeMap = information.getRequiredColumns();
        TypeSpec.Builder entityBuilder = TypeSpec.classBuilder(generatedClassName).addModifiers(Modifier.PUBLIC)
                .addField(getLogField(generatedClassName));

        for (Entry<String, Class<?>> entrySet : colmnDataTypeMap.entrySet()) {
            String attributeName = entrySet.getKey();
            Class<?> dataType = entrySet.getValue();

            entityBuilder.addField(FieldSpec.builder(dataType, attributeName).addModifiers(Modifier.PRIVATE).build());
            entityBuilder.addMethod(createGetter(attributeName, dataType));
        }

        entityBuilder.addMethod(createAttributeSetter(colmnDataTypeMap));
        information.setClassName(generatedClassName);

        createAndCompileJavaFile(entityBuilder.build());
    }

    private static MethodSpec createGetter(String attributeName, Class<?> dataTypeClass) {
        return MethodSpec.methodBuilder(CommonUtils.firstLetterToUpper(attributeName, "get", ""))
                .addModifiers(Modifier.PUBLIC).returns(dataTypeClass).addStatement("return $L", attributeName).build();
    }

    private static MethodSpec createAttributeSetter(Map<String, Class<?>> colmnDataTypeMap) {
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("setAttributes").addModifiers(Modifier.PUBLIC)
                .returns(void.class).addParameter(String.class, "col").addParameter(Object.class, "val")
                .beginControlFlow("switch(col)");

        for (Entry<String, Class<?>> entrySet : colmnDataTypeMap.entrySet()) {
            String variableName = CommonUtils.toCamelCase(entrySet.getKey(), false);
            methodSpecBuilder.addCode("case $S:\n", variableName)
                    .addStatement(INDENT + "this.$L = ($T) val", variableName, entrySet.getValue())
                    .addStatement("break");
        }

        return methodSpecBuilder.addCode("default:\n")
                .addStatement(INDENT + "LOG.log($T.CONFIG, $S, $N)", Level.class, "Not required Column : {0}", "col")
                .endControlFlow().build();
    }

    private static FieldSpec getLogField(String className) {
        return FieldSpec.builder(Logger.class, "LOG").addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.getLogger($N.class.getCanonicalName())", Logger.class, className).build();
    }

    private static void addToStringMethod(TypeSpec.Builder entity, Map<String, Class<?>> allFields,
            List<String> projections, List<String> headers) {
        MethodSpec.Builder rowString = MethodSpec.methodBuilder("getRowString").addModifiers(Modifier.PUBLIC)
                .returns(String.class).addParameter(int.class, "lineNumber").addParameter(boolean.class, "isCsv")
                .addStatement("StringBuilder builder = new StringBuilder(System.lineSeparator())")
                .beginControlFlow(IS_CSV).addStatement("builder.append(lineNumber).append(\",\")");

        MethodSpec.Builder headerString = MethodSpec.methodBuilder("getHeaderString").addModifiers(Modifier.PUBLIC)
                .addParameter(boolean.class, "isCsv").returns(String.class)
                .addStatement("StringBuilder builder = new StringBuilder()").beginControlFlow(IS_CSV)
                .addStatement("builder.append($S).append(\",\")", "Line");

        CodeBlock.Builder nonCsvHeaderBlock = CodeBlock.builder().addStatement("builder.append(System.lineSeparator())")
                .addStatement("builder.append(String.format(CommonUtils.ROW_NUM_FORMAT, $S))", "");
        CodeBlock.Builder nonCsvRowBlock = CodeBlock.builder()
                .addStatement("builder.append(String.format(CommonUtils.ROW_NUM_FORMAT, lineNumber))");

        String headerFormatTemplate = "builder.append($L$S$L).append($S)";

        for (int index = 0; index < projections.size(); index++) {
            String projection = projections.get(index);
            String header = headers.get(index);
            Class<?> dataType = allFields.getOrDefault(projection, String.class);

            if (Double.class.isAssignableFrom(dataType) || projection.split("[-+*/]").length > 1) {
                rowString.addStatement("builder.append($T.DECIMAL_FORMAT.format(($T) $L)).append(\",\")",
                        CommonUtils.class, double.class, projection);
                headerString.addStatement(headerFormatTemplate, "", header, "", ",");
                nonCsvRowBlock.addStatement(
                        "builder.append(String.format($T.NUMBER_CELL_FORMAT, $T.DECIMAL_FORMAT.format(($T) $L)))",
                        CommonUtils.class, CommonUtils.class, double.class, projection);
                nonCsvHeaderBlock.addStatement(headerFormatTemplate, "String.format(CommonUtils.NUMBER_CELL_FORMAT, ",
                        header, ")", "");
            } else if (Integer.class.isAssignableFrom(dataType) || Long.class.isAssignableFrom(dataType)) {
                rowString.addStatement("builder.append($L).append(\",\")", projection);
                headerString.addStatement(headerFormatTemplate, "", header, "", ",");
                nonCsvRowBlock.addStatement("builder.append(String.format($T.NUMBER_CELL_FORMAT, $L))",
                        CommonUtils.class, projection);
                nonCsvHeaderBlock.addStatement(headerFormatTemplate, "String.format(CommonUtils.NUMBER_CELL_FORMAT, ",
                        header, ")", "");
            } else {
                rowString.addStatement("builder.append($L).append(\",\")", projection);
                headerString.addStatement(headerFormatTemplate, "", header, "", ",");
                nonCsvRowBlock.addStatement("builder.append(String.format($T.STRING_CELL_FORMAT, $L))",
                        CommonUtils.class, projection);
                nonCsvHeaderBlock.addStatement(headerFormatTemplate, "String.format(CommonUtils.STRING_CELL_FORMAT, ",
                        header, ")", "");
            }
        }

        String returnStatement = "return builder.toString()";

        rowString.addStatement(returnStatement).endControlFlow().addCode(nonCsvRowBlock.build())
                .addStatement(returnStatement);
        entity.addMethod(rowString.build());

        headerString.addStatement(returnStatement).endControlFlow().addCode(nonCsvHeaderBlock.build())
                .addStatement(returnStatement);
        entity.addMethod(headerString.build());
    }

    private static void addEqualsAndHashCodeMethod(TypeSpec.Builder entity, TypeName compositeType,
            List<String> groupingAttributes) {
        MethodSpec.Builder hashCode = MethodSpec.methodBuilder("hashCode").addModifiers(Modifier.PUBLIC)
                .returns(int.class).addAnnotation(Override.class).addCode("return ");

        MethodSpec.Builder equals = MethodSpec.methodBuilder("equals").addModifiers(Modifier.PUBLIC)
                .returns(boolean.class).addParameter(Object.class, "obj").addAnnotation(Override.class)
                .beginControlFlow("if (obj == null || this.getClass() != obj.getClass())").addStatement("return false")
                .endControlFlow().addStatement("$T otherEntity = ($T) obj", compositeType, compositeType)
                .addCode("return ");

        int index = 0;
        for (; index < groupingAttributes.size() - 1; index++) {
            String groupingAttribute = groupingAttributes.get(index);
            equals.addCode("$T.equals(this.$L, otherEntity.get$L()) && ", Objects.class, groupingAttribute,
                    CommonUtils.firstLetterToUpper(groupingAttribute, "", ""));
            hashCode.addCode("$T.hashCode($L) + ", Objects.class, groupingAttribute);
        }

        String groupingAttribute = groupingAttributes.get(index);
        equals.addCode("$T.equals(this.$L, otherEntity.get$L());$L", Objects.class, groupingAttribute,
                CommonUtils.firstLetterToUpper(groupingAttribute, "", ""), System.lineSeparator());
        hashCode.addCode("$T.hashCode($L);$L", Objects.class, groupingAttribute, System.lineSeparator());

        entity.addMethod(hashCode.build());
        entity.addMethod(equals.build());
    }

    private static void addCompareToMetod(TypeSpec.Builder entity, TypeName compositeType,
            List<String> orderByAttributes, int orderMultiplier) {
        if (orderByAttributes.isEmpty()) {
            return;
        }

        entity.addSuperinterface(ParameterizedTypeName.get(ClassName.get("java.lang", "Comparable"), compositeType));

        MethodSpec.Builder compareTo = MethodSpec.methodBuilder("compareTo").addModifiers(Modifier.PUBLIC)
                .addParameter(compositeType, "otherEntity").returns(int.class).addAnnotation(Override.class)
                .addCode("int comparison = $T.comparing(CompositeEntity::$L)", Comparator.class,
                        orderByAttributes.get(0))
                .addCode("$L$L$L", System.lineSeparator(), INDENT, INDENT);

        int index = 1;
        for (; index < orderByAttributes.size(); index++) {
            compareTo.addCode(".thenComparing(CompositeEntity::$L)", orderByAttributes.get(index)).addCode("$L$L$L",
                    System.lineSeparator(), INDENT, INDENT);
        }

        compareTo.addCode(".compare(this, otherEntity);").addCode("$Lreturn comparison * $L;$L", System.lineSeparator(),
                orderMultiplier, System.lineSeparator());

        entity.addMethod(compareTo.build());
    }

    private static void addFieldsAndConstructor(TypeSpec.Builder entity, TypeName compositeType,
            Map<String, Class<?>> allFields, Map<String, TableInformation> relationsMap,
            Map<String, GroupingInformation> groups) {

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        for (Entry<String, TableInformation> relations : relationsMap.entrySet()) {
            Map<String, Class<?>> columnData = relations.getValue().getRequiredColumns();
            for (Entry<String, Class<?>> columns : columnData.entrySet()) {
                String attributeName = CommonUtils.append(columns.getKey(), "", relations.getKey(), "_");
                Class<?> dataType = columns.getValue();

                entity.addField(FieldSpec.builder(dataType, attributeName).addModifiers(Modifier.PRIVATE).build());
                entity.addMethod(createGetter(attributeName, dataType));

                allFields.put(attributeName, dataType);
            }
            TypeName typeName = ClassName.get(PACKAGE, relations.getValue().getClassName());
            entity.addMethod(createCompositeAttributeSetter(columnData, typeName, relations.getKey()));
            constructor.addParameter(typeName, relations.getKey()).addStatement("setAttributes_$L($L)",
                    relations.getKey(), relations.getKey());
        }

        entity.addMethod(constructor.build());

        for (Entry<String, GroupingInformation> group : groups.entrySet()) {
            Map<String, Class<?>> variables = new HashMap<>();
            for (String attributeName : group.getValue().getAggregates()) {
                Class<?> dataType = resolveDataTypeOfAggregate(attributeName, relationsMap);
                entity.addField(createAggregateField(attributeName, dataType));
                entity.addMethod(createGetter(attributeName, dataType));
                variables.put(attributeName, dataType);
                allFields.put(attributeName, dataType);
            }
            entity.addMethod(createAggregateSetter(variables, compositeType, "_" + group.getKey(), group.getValue()));
        }
    }

    private static FieldSpec createAggregateField(String attributeName, Class<?> dataType) {
        FieldSpec.Builder builder = FieldSpec.builder(dataType, attributeName).addModifiers(Modifier.PRIVATE);

        if (dataType.isAssignableFrom(Long.class)) {
            return builder.initializer("0L").build();
        }

        if (dataType.isAssignableFrom(Integer.class)) {
            return builder.initializer("0").build();
        }

        if (dataType.isAssignableFrom(Double.class)) {
            return builder.initializer("0D").build();
        }

        return builder.initializer("").build();
    }

    private static MethodSpec createCompositeAttributeSetter(Map<String, Class<?>> columnData, TypeName typeName,
            String tableAlias) {
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("setAttributes_" + tableAlias)
                .addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(typeName, tableAlias);

        for (Entry<String, Class<?>> entrySet : columnData.entrySet()) {
            String variableName = CommonUtils.toCamelCase(entrySet.getKey(), false);
            String methodName = CommonUtils.firstLetterToUpper(entrySet.getKey(), tableAlias + ".get", "()");
            methodSpecBuilder.addStatement("this.$L_$L = $L", variableName, tableAlias, methodName);
        }
        Collections.max(Arrays.asList("a", "b"));

        return methodSpecBuilder.build();
    }

    private static MethodSpec createAggregateSetter(Map<String, Class<?>> colmnDataTypeMap, TypeName compositeType,
            String tableSuffix, GroupingInformation groupingInformation) {
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("incrementAggregates_" + tableSuffix)
                .addModifiers(Modifier.PUBLIC).returns(void.class).addParameter(compositeType, NEW_ROW);

        if (groupingInformation.getBoundryConditions() != null) {
            methodSpecBuilder.beginControlFlow("if ($L)", groupingInformation.getBoundryConditions());
        }

        List<CodeBlock> averageBlocks = new ArrayList<>();
        for (Entry<String, Class<?>> entrySet : colmnDataTypeMap.entrySet()) {
            CodeBlock block = processElement(entrySet, methodSpecBuilder);
            if (block != null) {
                averageBlocks.add(block);
            }
        }

        for (CodeBlock block : averageBlocks) {
            methodSpecBuilder.addCode(block);
        }

        if (groupingInformation.getBoundryConditions() != null) {
            methodSpecBuilder.endControlFlow();
        }

        return methodSpecBuilder.build();
    }

    private static CodeBlock processElement(Entry<String, Class<?>> entrySet, Builder methodSpecBuilder) {
        String[] components = extractComponents(entrySet.getKey());
        String aggregate = components[2];
        String tableAlias = components[0];
        String columnName = components[1];
        String groupingVariable = components[3];

        String variableName = CommonUtils.append(columnName, "", tableAlias, "_");
        String methodName = CommonUtils.firstLetterToUpper(variableName, "newRow.get", "()");

        if (aggregate.equalsIgnoreCase(SUM)) {
            methodSpecBuilder.addStatement("this.$L = this.$L + $L", entrySet.getKey(), entrySet.getKey(), methodName);
            return null;
        }

        if (aggregate.equalsIgnoreCase(COUNT)) {
            methodSpecBuilder.addStatement("this.$L++", entrySet.getKey());
            return null;
        }

        if (aggregate.equalsIgnoreCase(MAX)) {
            methodSpecBuilder.addStatement("this.$L = $T.max(this.$L, $L)", entrySet.getKey(), CommonUtils.class,
                    entrySet.getKey(), methodName);
            return null;
        }

        if (aggregate.equalsIgnoreCase(MIN)) {
            methodSpecBuilder.addStatement("this.$L = $T.min(this.$L, $L)", entrySet.getKey(), CommonUtils.class,
                    entrySet.getKey(), methodName);
            return null;
        }

        if (aggregate.equalsIgnoreCase(AVG)) {
            String sumVariableName = CommonUtils.append(variableName, SUM, groupingVariable, "_");
            String countVariableName = CommonUtils.append(variableName, COUNT, groupingVariable, "_");
            return CodeBlock.builder().addStatement("this.$L = (double) this.$L/this.$L", entrySet.getKey(),
                    sumVariableName, countVariableName).build();
        }

        methodSpecBuilder.addStatement("this.$L = $L", entrySet.getKey(), methodName);
        return null;
    }

    private static Class<?> resolveDataTypeOfAggregate(String attributeName, Map<String, TableInformation> relations) {
        String[] components = extractComponents(attributeName);
        String aggregate = components[2];

        if (aggregate.equalsIgnoreCase(COUNT) || aggregate.equalsIgnoreCase(SUM)) {
            return Long.class;
        }

        if (aggregate.equals(AVG)) {
            return Double.class;
        }

        return relations.get(components[0]).getRequiredColumns().get(components[1]);
    }

    private static void addClassFieldsAndConstructor(TypeSpec.Builder entityBuilder) {
        entityBuilder.addField(FieldSpec.builder(Properties.class, "credentials")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        entityBuilder.addField(
                FieldSpec.builder(int.class, "blockSize").addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        entityBuilder.addField(FieldSpec
                .builder(ParameterizedTypeName.get(Map.class, String.class, ResultSetMetaData.class), "metadatas")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        entityBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
                .addStatement("this.credentials = $T.readProperties($T.DB_PROPERTIES)", ReadWrite.class,
                        CommonUtils.class)
                .addStatement("this.blockSize = Integer.parseInt(credentials.getProperty(\"blockSize\"))")
                .addStatement("this.metadatas = new $T<>()", HashMap.class).build());
    }

    private static void addMethods(TypeSpec.Builder entityBuilder) {
        entityBuilder.addMethod(MethodSpec.methodBuilder("getConnection").addModifiers(Modifier.PUBLIC)
                .returns(Connection.class).addException(SQLException.class)
                .addStatement("return $T.getConnection(credentials.getProperty(\"url\"), credentials)",
                        DriverManager.class)
                .build());

        entityBuilder.addMethod(MethodSpec.methodBuilder("getPreparedStatement").addModifiers(Modifier.PUBLIC)
                .addParameter(Connection.class, "connection").addParameter(String.class, "tableName")
                .returns(PreparedStatement.class).addException(SQLException.class)
                .addStatement("return connection.prepareStatement(String.format($S, tableName))", "select * from %s")
                .build());

        entityBuilder.addMethod(MethodSpec.methodBuilder("addMetadata").addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "tableName").addParameter(ResultSet.class, "resultSet")
                .addException(SQLException.class).returns(void.class)
                .beginControlFlow("if(!metadatas.containsKey(tableName))")
                .addStatement("metadatas.put(tableName, resultSet.getMetaData())").endControlFlow().build());
    }

    private static void addReadRowMethods(TypeSpec.Builder entityBuilder, ParsedQuery parsedQuery) {
        ClassName list = ClassName.get(UTILS_PACKAGE, "List");

        for (Entry<String, TableInformation> entrySet : parsedQuery.getRelations().entrySet()) {
            String className = entrySet.getValue().getClassName();
            ClassName generatedClassName = ClassName.get(PACKAGE, className);
            TypeName listOfGeneratedClass = ParameterizedTypeName.get(list, generatedClassName);

            CodeBlock rowsLogic = CodeBlock.builder()
                    .addStatement("$T rows = new $T<>()", listOfGeneratedClass, ArrayList.class)
                    .addStatement("$T meta = metadatas.get($S)", ResultSetMetaData.class,
                            entrySet.getValue().getTableName())
                    .beginControlFlow("while (rows.size() <= blockSize && resultSet.next())")
                    .addStatement("$T row = new $T()", generatedClassName, generatedClassName)
                    .beginControlFlow("for (int i = 1; i <= meta.getColumnCount(); i++)")
                    .addStatement("row.setAttributes($N, $N)", "meta.getColumnName(i)", "resultSet.getObject(i)")
                    .endControlFlow().addStatement("rows.add(row)").endControlFlow().addStatement("return rows")
                    .build();

            entityBuilder.addMethod(MethodSpec.methodBuilder(String.format("get%sRows", className))
                    .addModifiers(Modifier.PUBLIC).addParameter(ResultSet.class, "resultSet").returns(void.class)
                    .addException(SQLException.class).addCode(rowsLogic).returns(listOfGeneratedClass).build());
        }
    }

    /**
     * Parses the variable string to return individual components.
     *
     * @param attributeName
     * @return returns {tableAlias, columnName, aggregateName, groupingVariable}
     */
    private static String[] extractComponents(String attributeName) {
        String[] components = attributeName.split("_");

        if (components[0].matches(AGGREGATE_REGEX)) {
            return new String[] { components[2], components[1], components[0], components[3] };
        }

        return new String[] { components[1], components[0], "", components[2] };
    }

    /**
     * Creates a java file defined on the TypeSpec object and compiles it.
     *
     * @param typeSpec
     */
    private static void createAndCompileJavaFile(TypeSpec typeSpec) {
        try {
            JavaFile.builder(PACKAGE, typeSpec).skipJavaLangImports(true).indent(INDENT).build().writeTo(SOURCE);

            FILE_MANAGER.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(TARGET));

            Iterable<? extends JavaFileObject> compilationUnits = FILE_MANAGER
                    .getJavaFileObjectsFromStrings(Arrays.asList(GENERATED_SOURCE + typeSpec.name + ".java"));

            if (!COMPILER.getTask(null, FILE_MANAGER, DIGNOSTIC, null, null, compilationUnits).call()) {
                logDignosticReports();
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not generate Java file.", e);
            CommonUtils.exit(1);
        }
    }

    private static void logDignosticReports() {
        for (Diagnostic<? extends JavaFileObject> report : DIGNOSTIC.getDiagnostics()) {
            if (report.getKind() == Kind.ERROR) {
                LOG.log(Level.SEVERE, "Error at : {0}", report);
            }
        }
        CommonUtils.exit(1);
    }

    private static void addMainMethod(TypeSpec.Builder entityBuilder, ParsedQuery parsedQuery,
            ParameterizedTypeName mfTableType, Set<TableInformation> sortedSet) {
        Class<?> mapType = parsedQuery.getOrderByAttributes().isEmpty() ? HashMap.class : TreeSet.class;

        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("main")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC).returns(void.class)
                .addParameter(ArrayTypeName.of(String.class), "args")
                .addStatement("DataAccessObject dao = new DataAccessObject()")
                .addStatement("$T mfTable = new $T<>()", mfTableType, mapType);

        StringBuilder methodCallTemplate = new StringBuilder("streamTableBlocks(dao, mfTable, %d");
        String metaDataCallTemplate = "dao.addMetadata(\"%s\", %s);";
        StringBuilder metaDataCalls = new StringBuilder();

        String statementTemplate = "$T %s = dao.getPreparedStatement(connection, \"%s\");";
        String statementNameTemplate = "statement_%s";
        List<Class<?>> toImportInParent = new ArrayList<>();

        String resultSetTemplate = "$T %s = %s.executeQuery();";
        String resultSetNameTemplate = "resultSet_%s";
        List<Class<?>> toImportInUnit = new ArrayList<>();

        StringBuilder parentTryBlock = new StringBuilder("try ($T connection = dao.getConnection();");
        toImportInParent.add(Connection.class);

        StringBuilder unitTryBlock = new StringBuilder("try (");

        for (TableInformation entry : sortedSet) {
            String statementName = String.format(statementNameTemplate, entry.getAlias());
            String statement = String.format(statementTemplate, statementName, entry.getTableName());
            toImportInParent.add(PreparedStatement.class);

            String resultSetName = String.format(resultSetNameTemplate, entry.getAlias());
            String resultSet = String.format(resultSetTemplate, resultSetName, statementName);
            toImportInUnit.add(ResultSet.class);
            methodCallTemplate.append(",").append(resultSetName);
            metaDataCalls.append(String.format(metaDataCallTemplate, entry.getTableName(), resultSetName))
                    .append(System.lineSeparator());

            parentTryBlock.append(System.lineSeparator()).append(INDENT).append(INDENT).append(statement);
            unitTryBlock.append(resultSet).append(System.lineSeparator()).append(INDENT).append(INDENT);
        }

        methodCallTemplate.append(");").append(System.lineSeparator());

        String parent = parentTryBlock.substring(0, parentTryBlock.lastIndexOf(";")) + ")";
        String unit = unitTryBlock.substring(0, unitTryBlock.lastIndexOf(";")) + ")";

        Class<?>[] parentImport = toImportInParent.toArray(new Class<?>[0]);
        Class<?>[] unitImport = toImportInUnit.toArray(new Class<?>[0]);

        CodeBlock.Builder parentBlock = CodeBlock.builder().beginControlFlow(parent, parentImport)
                .beginControlFlow(unit, unitImport).add(metaDataCalls.toString())
                .add(String.format(methodCallTemplate.toString(), 0)).endControlFlow().add(System.lineSeparator());

        for (Integer executionUnit : parsedQuery.getExecutionGroups().keySet()) {
            parentBlock.beginControlFlow(unit, unitImport)
                    .add(String.format(methodCallTemplate.toString(), executionUnit)).endControlFlow()
                    .add(System.lineSeparator());
        }

        parentBlock.nextControlFlow("catch ($T e)", SQLException.class)
                .addStatement("LOG.log($T.SEVERE, \"Could not execute query.\", e)", Level.class)
                .addStatement("$T.exit(1)", CommonUtils.class).endControlFlow();

        methodSpecBuilder.addCode(parentBlock.build());

        methodSpecBuilder.addCode(System.lineSeparator()).addStatement("String csvFileName = null")
                .beginControlFlow("if (args == null || args.length == 0 || !args[0].equals(\"noCsv\"))")
                .addStatement("csvFileName = $T.getCsvFileName()", ReadWrite.class).endControlFlow()
                .addStatement("displayResult(mfTable, csvFileName)");

        entityBuilder.addMethod(methodSpecBuilder.build());
    }

    private static void addDisplayResultMethod(TypeSpec.Builder entityBuilder, ParsedQuery parsedQuery,
            TypeName compositeType, ParameterizedTypeName mfTableType) {
        String methodCall = parsedQuery.getOrderByAttributes().isEmpty() ? ".values()" : "";
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("displayResult")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(void.class).addParameter(mfTableType, MF_TABLE)
                .addParameter(String.class, "csvFileName").addStatement("StringBuilder result = new StringBuilder()")
                .addStatement("String header = $S", "").addStatement("String rowSeparator = $S", "")
                .addStatement("boolean isCsv = csvFileName != null && !csvFileName.isEmpty()")
                .addStatement("int lineNumber = 0")
                .beginControlFlow("for ($T value : mfTable$L)", compositeType, methodCall)
                .beginControlFlow("if (lineNumber == 0)").addStatement("header = value.getHeaderString(isCsv)")
                .addStatement("rowSeparator = isCsv ? $S : $T.getRowSeparator(header.length())", "", CommonUtils.class)
                .addStatement("result.append(rowSeparator + header + rowSeparator)").addStatement("lineNumber++")
                .endControlFlow();

        if (parsedQuery.getHavingCondition() != null) {
            methodSpecBuilder.beginControlFlow("if (!($L))", parsedQuery.getHavingCondition()).addStatement("continue")
                    .endControlFlow();
        }
        methodSpecBuilder.addStatement("result.append(value.getRowString(lineNumber, isCsv) + rowSeparator)")
                .addStatement("lineNumber++").endControlFlow();

        methodSpecBuilder.beginControlFlow(IS_CSV)
                .addStatement("$T.generateCsv(result.toString(), csvFileName)", ReadWrite.class).nextControlFlow("else")
                .addStatement("System.out.println(result.toString())").endControlFlow();

        methodSpecBuilder.addStatement(
                "System.out.println(String.format(\"$LSuccessfully found $L rows\", System.lineSeparator(), --lineNumber))",
                "%1$s", "%2$d");

        entityBuilder.addMethod(methodSpecBuilder.build());
    }

    private static void addStreamTableBlocksMethod(TypeSpec.Builder entityBuilder, ParsedQuery parsedQuery,
            TypeName compositeType, ParameterizedTypeName map, Set<TableInformation> sortedSet) {
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("streamTableBlocks")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(void.class)
                .addParameter(ClassName.get(PACKAGE, DAO_NAME), "dao").addParameter(map, MF_TABLE)
                .addParameter(int.class, "groupNumber").addException(SQLException.class)
                .addStatement("$T.requireNonNull(dao, \"DAO must not be null\")", Objects.class)
                .addStatement("$T.requireNonNull(mfTable, \"MF Table must not be null\")", Objects.class);

        String parameterTemplate = "resultSet_%s";
        String listTemplate = "rows_%s";
        int loopCount = 0;

        for (TableInformation entry : sortedSet) {
            String parameterName = String.format(parameterTemplate, entry.getAlias());
            String listName = String.format(listTemplate, entry.getAlias());
            TypeName className = ClassName.get(PACKAGE, entry.getClassName());
            methodSpecBuilder.addParameter(ResultSet.class, parameterName)
                    .addStatement("$T<$T> $L", List.class, className, listName)
                    .beginControlFlow("while (!($L = dao.get$LRows($L)).isEmpty())", listName, entry.getClassName(),
                            parameterName)
                    .beginControlFlow("for($T $L : $L)", className, entry.getAlias(), listName);
            loopCount += 2;
        }

        methodSpecBuilder.addStatement("$T newRow = new $T($L)", compositeType, compositeType,
                String.join(", ", parsedQuery.getRelations().keySet()));

        if (parsedQuery.getSelectConditions() != null) {
            methodSpecBuilder.beginControlFlow("if($L)", parsedQuery.getSelectConditions())
                    .addStatement("computeGroups(mfTable, newRow, groupNumber)").endControlFlow();
        } else {
            methodSpecBuilder.addStatement("computeGroups(mfTable, newRow, groupNumber)");
        }

        while (loopCount > 0) {
            methodSpecBuilder.endControlFlow();
            loopCount--;
        }

        entityBuilder.addMethod(methodSpecBuilder.build());

    }

    private static void addComputeGroupsMethod(TypeSpec.Builder entityBuilder, ParsedQuery parsedQuery,
            TypeName compositeType, ParameterizedTypeName mfTableType) {
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("computeGroups")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC).returns(void.class).addParameter(mfTableType, MF_TABLE)
                .addParameter(compositeType, NEW_ROW).addParameter(int.class, "groupNumber")
                .beginControlFlow("if (groupNumber == 0)");

        if (parsedQuery.getOrderByAttributes().isEmpty()) {
            if (parsedQuery.getGroups().containsKey("0")) {
                methodSpecBuilder.addStatement("$T value = mfTable.computeIfAbsent(newRow.hashCode(), k -> newRow)",
                        compositeType).addStatement("value.incrementAggregates__0(newRow)");
            } else {
                methodSpecBuilder.addStatement("mfTable.computeIfAbsent(newRow.hashCode(), k -> newRow)");
            }

            methodSpecBuilder.addStatement("return").endControlFlow().addCode(System.lineSeparator())
                    .beginControlFlow("for ($T group : mfTable.values())", compositeType);
        } else {
            methodSpecBuilder.beginControlFlow("if(!mfTable.contains($L))", NEW_ROW).addStatement("mfTable.add(newRow)")
                    .endControlFlow();
            if (parsedQuery.getGroups().containsKey("0")) {
                methodSpecBuilder.beginControlFlow("for ($T group : mfTable)", compositeType)
                        .addStatement("value.incrementAggregates__0(newRow)").endControlFlow();
            }

            methodSpecBuilder.addStatement("return").endControlFlow().addCode(System.lineSeparator())
                    .beginControlFlow("for ($T group : mfTable)", compositeType);
        }

        String methodStub = "group.incrementAggregates__%s(newRow)";

        if (parsedQuery.getExecutionGroups().size() == 1) {
            for (String group : parsedQuery.getExecutionGroups().get(1)) {
                methodSpecBuilder.addStatement(String.format(methodStub, group));
            }
            entityBuilder.addMethod(methodSpecBuilder.endControlFlow().build());
            return;
        }

        for (Entry<Integer, Set<String>> entry : parsedQuery.getExecutionGroups().entrySet()) {
            methodSpecBuilder.beginControlFlow("if (groupNumber == $L)", entry.getKey());
            for (String group : entry.getValue()) {
                methodSpecBuilder.addStatement(String.format(methodStub, group));
            }
            methodSpecBuilder.endControlFlow();
        }
        entityBuilder.addMethod(methodSpecBuilder.endControlFlow().build());

    }
}
