package main.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.utils.CommonUtils;

/**
 * A Parser that converts input query from SQL language to JAVA programming
 * language. Used REGEX to split the input and parses each component.
 *
 * @author R&B
 *
 */
public class ParsedQuery {

    private static final Logger LOG = Logger.getLogger(ParsedQuery.class.getCanonicalName());

    private static final String SQL_REGEX = "(?i)(select\\s|from\\s|where\\s|group by\\s|such that\\s|having\\s|order by\\s|a\\sasc|\\sdesc)";

    private static final String OPERATORS_REGEX = "[-+*/]";

    private static final String PROJECTION_ELEMENT_REGEX = "(?i)\\sas\\s";

    private static final String PROJECTION_OPERATORS_REGEX = "(?<=[-+*/])|(?=[-+*/])";

    private static final String SQL_EXPRESSION_REGEX = "(?<=[!=<>])|(?=[!=<>])";

    private static final String AGGREGATE_REGEX = "^(?i)(avg|max|min|sum|count).+";

    private static final String CONDITION_SPLIT_REGEX = "(?i)(?<=\\sand\\s|\\sor\\s)|(?i)(?=\\sand\\s|\\sor\\s)|,";

    private final Map<String, TableInformation> relations;

    private final Map<String, GroupingInformation> groups;

    private final Map<Integer, Set<String>> executionGroups;

    private final List<String> projections;

    private final List<String> headers;

    private final List<String> groupingAttributes;

    private final List<String> orderByAttributes;

    private String selectConditions;

    private String havingCondition;

    private int orderMultiplier;

    public ParsedQuery() {
        relations = new HashMap<>();
        groups = new HashMap<>();
        executionGroups = new HashMap<>();
        projections = new ArrayList<>();
        headers = new ArrayList<>();
        groupingAttributes = new ArrayList<>();
        orderByAttributes = new ArrayList<>();
        orderMultiplier = 1;
    }

    public void processSQL(String sql) {
        sql = sql.replaceAll(System.lineSeparator(), "");
        processSqlKeywords(sql.split(String.format("(?<=%s)|(?=%s)", SQL_REGEX, SQL_REGEX)));
    }

    public void processSqlKeywords(String[] components) {
        int index = 0;
        String projectionStatement = "";

        while (index < components.length) {
            String keyword = components[index].trim();
            switch (keyword) {
                case "select":
                    projectionStatement = components[++index].trim();
                    break;
                case "from":
                    processRelations(components[++index].trim());
                    break;
                case "where":
                    processWhereConditions(components[++index].trim());
                    break;
                case "group by":
                    processGroupingParameters(components[++index].trim());
                    break;
                case "such that":
                    processGroupBoundries(components[++index].trim());
                    break;
                case "having":
                    processHavingConditions(components[++index].trim());
                    break;
                case "order by":
                    processOrderByConditions(components[++index].trim());
                    break;
                case "asc":
                    orderMultiplier = 1;
                    break;
                case "desc":
                    orderMultiplier = -1;
                    break;
                default:
                    LOG.log(Level.SEVERE, "Invalid SQL keyword : {0}", keyword);
                    CommonUtils.exit(1);
            }
            index++;
        }

        processPojections(projectionStatement);
        processGroupZeroBoundryCondition();
        processGroupDependency();
    }

    private void processPojections(String statement) {
        String[] elements = statement.split(",");
        for (String element : elements) {
            processProjectionElement(element);
        }
    }

    private void processProjectionElement(String element) {
        String[] components = element.split(PROJECTION_ELEMENT_REGEX);

        String[] projectionComponents = components[0].split(PROJECTION_OPERATORS_REGEX);
        StringBuilder expression = new StringBuilder();

        for (String projection : projectionComponents) {
            projection = projection.trim();
            if (projection.matches(OPERATORS_REGEX) || projection.indexOf('.') == -1) {
                expression.append(projection);
                continue;
            }

            String[] variables = getComponents(projection);
            String columnName = CommonUtils.toCamelCase(variables[0], false);
            String variableName = CommonUtils.append(columnName, "", variables[1], "_");
            String fullVariableName = CommonUtils.append(variableName, variables[2], variables[3], "_");

            TableInformation info = relations.computeIfAbsent(variables[1], k -> new TableInformation(k));
            info.addColumn(columnName);

            if (!variables[3].isEmpty()) {
                GroupingInformation group = addGroupingVariable(variables[3]);
                group.addAggregates(variables[2], variableName, true);
            }
            expression.append(fullVariableName);
        }

        String header = components.length > 1 && !components[1].isEmpty() ? components[1] : expression.toString();

        projections.add(expression.toString());
        headers.add(header);
    }

    private GroupingInformation addGroupingVariable(String variable) {
        return groups.computeIfAbsent(variable, k -> new GroupingInformation(k));
    }

    /**
     * Parses the variable string to return individual components.
     *
     * @param element
     * @return Array of columnName, tableAlias, aggregateFunction, groupingVariable
     */
    private String[] getComponents(String element) {
        String columnReference = element;
        String aggregateFunction = "";
        String groupingVariable = "";

        if (element.matches(AGGREGATE_REGEX)) {
            String[] aggElements = element.split("[()]");
            aggregateFunction = aggElements[0];
            columnReference = aggElements[1];
            groupingVariable = "0";
        }

        int firstDot = columnReference.indexOf('.');
        int secondDot = columnReference.lastIndexOf('.');

        if (firstDot == secondDot) {
            firstDot = -1;
        }

        String columnName = columnReference.substring(secondDot + 1);
        String tableAlias = columnReference.substring(firstDot + 1, secondDot);
        groupingVariable = firstDot > 0 ? columnReference.substring(0, firstDot) : groupingVariable;

        return new String[] { columnName, tableAlias, aggregateFunction, groupingVariable };
    }

    /**
     * Processes the from condition in the query. The input query must provide a
     * table alias for all referenced tables.
     *
     * @param component
     */
    private void processRelations(String component) {
        String[] tables = component.trim().split(",");
        String splitRegex = "\\s+";

        for (String table : tables) {
            // Splits the table name and alias
            String[] tableAndAlias = table.trim().split(splitRegex);
            TableInformation tableInfo = relations.computeIfAbsent(tableAndAlias[1], k -> new TableInformation(k));
            tableInfo.setTableName(tableAndAlias[0]);
        }
    }

    /**
     * Processes the where conditions in the query. The input query must provide a
     * table alias for all referenced tables. IN, NOT IN, EXISTS, IS NULL operators
     * are not supported. Converts to syntax that can be used in Java code Example :
     * S.month=1 is converted to Objects.equals(newRow.getMonth_S(), 1).
     *
     * @param component
     */
    private void processWhereConditions(String statement) {
        String[] elements = statement.split(CONDITION_SPLIT_REGEX);
        StringBuilder conditionBuilder = new StringBuilder();
        for (String element : elements) {
            element = element.trim();
            processConditionElement(conditionBuilder, element, "newRow.");
        }

        selectConditions = conditionBuilder.toString();
    }

    /**
     * Processes the grouping attributes and grouping variables. The input query
     * must provide a table alias for all referenced tables.
     *
     * @param component
     */
    private void processGroupingParameters(String statement) {
        // Splits grouping attributes and grouping variables
        String[] elements = statement.split(";");
        // Splits individual grouping attributes
        String[] groupingAttibutes = elements[0].split(",");
        // Splits individual grouping variables
        String[] variables = elements[1].split(",");

        for (String groupingAttibute : groupingAttibutes) {
            String[] components = getComponents(groupingAttibute.trim());
            String columnName = CommonUtils.toCamelCase(components[0], false);
            relations.get(components[1]).addColumn(columnName);
            addGroupingAttributes(CommonUtils.toCamelCase(columnName, false, true, components[1]));
        }

        for (String variable : variables) {
            addGroupingVariable(variable.trim());
        }

    }

    /**
     * Processes the such that conditions in the query. The input query must provide
     * a table alias for all referenced tables. IN, NOT IN, EXISTS, IS NULL
     * operators are not supported. Converts to syntax that can be used in Java code
     * Example : x.S.month=S.month is converted to
     * Objects.equals(newRow.getMonth_S_X(), getMonth_S()).
     *
     * @param component
     */
    private void processGroupBoundries(String statement) {
        String[] elements = statement.split(CONDITION_SPLIT_REGEX);
        GroupingInformation currentGroup = null;
        for (String element : elements) {
            element = element.trim();
            currentGroup = processBoundryConditionElement(currentGroup, element);
        }
    }

    private GroupingInformation processBoundryConditionElement(GroupingInformation currentGroup, String element) {
        if (element.equalsIgnoreCase("AND")) {
            currentGroup.addBoundryConditions(" && ");
            return currentGroup;
        }

        if (element.equalsIgnoreCase("OR")) {
            currentGroup.addBoundryConditions(" || ");
            return currentGroup;
        }

        String[] sqlExpressionElement = getSqlExpressionElement(element);

        String lhs = sqlExpressionElement[0].trim();
        String[] lhsComponents = getComponents(lhs);

        String columnName = CommonUtils.toCamelCase(lhsComponents[0], false);
        String variableName = CommonUtils.append(columnName, "", lhsComponents[1], "_");
        String fullVariableName = CommonUtils.append(variableName, lhsComponents[2], "", "_");

        String lhsExpression = CommonUtils.firstLetterToUpper(fullVariableName, "newRow.get", "()");

        relations.get(lhsComponents[1]).addColumn(columnName);

        currentGroup = groups.get(lhsComponents[3]);
        currentGroup.addAggregates(lhsComponents[2], variableName, false);

        String rhs = sqlExpressionElement[2].trim();
        String operator = sqlExpressionElement[1].trim();

        // The RHS can be a complex expression. Hence it is treated like an expression.
        Deque<String> rhsParts = new LinkedList<>();
        rhsParts.addAll(Arrays.asList(rhs.split(String.format("(?<=%s)|(?=%s)", OPERATORS_REGEX, OPERATORS_REGEX))));

        currentGroup.addBoundryConditions(
                buildExpression(operator, lhsExpression, processRHS(rhsParts, currentGroup, "", false)));
        return currentGroup;
    }

    /**
     * Processes all the operation in the RHS. The RHS can be 1 or S.month or
     * S.month-1. In case of the later this processing becomes mandatory.
     *
     * @param rhs
     * @param currentGroup
     * @param prefix
     * @param computePrefix
     * @return rhsExpression
     */
    private String processRHS(Deque<String> rhs, GroupingInformation currentGroup, String prefix,
            boolean computePrefix) {
        String lhs = rhs.poll();

        if (lhs == null) {
            return "";
        }

        if (rhs.isEmpty() && lhs.indexOf('.') == -1) {
            return lhs;
        }

        String operator = rhs.poll();

        if (lhs.indexOf('.') == -1) {
            return buildExpression(operator, lhs, processRHS(rhs, currentGroup, prefix, computePrefix));
        }

        String[] partComponents = getComponents(lhs);

        String columnName = CommonUtils.toCamelCase(partComponents[0], false);
        String variableName = CommonUtils.append(columnName, "", partComponents[1], "_");
        String fullVariableName = CommonUtils.append(variableName, partComponents[2], "", "_");

        if (!partComponents[2].isEmpty()) {
            GroupingInformation parentGroup = groups.getOrDefault(partComponents[3],
                    new GroupingInformation(partComponents[3]));
            parentGroup.addAggregates(partComponents[2], variableName, false);
            fullVariableName = CommonUtils.append(fullVariableName, "", partComponents[3], "_");
            groups.put(parentGroup.getName(), parentGroup);

            if (currentGroup != null && !parentGroup.getName().equals("0")) {
                currentGroup.addDependentOn(parentGroup.getName());
            }
        }

        String generatedPrefix = prefix.isEmpty() && computePrefix ? partComponents[1] : prefix;

        relations.get(partComponents[1]).addColumn(columnName);

        String lhsExpression = CommonUtils.firstLetterToUpper(fullVariableName, generatedPrefix + "get", "()");

        return buildExpression(operator, lhsExpression, processRHS(rhs, currentGroup, prefix, computePrefix));
    }

    /**
     * Converts SQL equations to java compatible equations. Example : LHS=RHS is
     * converted to Objects.equals(LHS, RHS).
     *
     * @param operator
     * @param lhsExpression
     * @param rhsExpression
     * @return expression
     */
    private String buildExpression(String operator, String lhsExpression, String rhsExpression) {
        if (operator == null) {
            return lhsExpression;
        }

        if (operator.equals("=")) {
            return String.format("Objects.equals(%1$s, %2$s)", lhsExpression, rhsExpression);
        }

        if (operator.equals("!=") || operator.equals("<>")) {
            return String.format("!Objects.equals(%1$s, %2$s)", lhsExpression, rhsExpression);
        }

        String comparisonTemplate = "Double.compare((double) %1$s, (double) %2$s) %3$s 0";

        if (operator.equals(">")) {
            return String.format(comparisonTemplate, lhsExpression, rhsExpression, ">");
        }

        if (operator.equals(">=")) {
            return String.format(comparisonTemplate, lhsExpression, rhsExpression, ">=");
        }

        if (operator.equals("<")) {
            return String.format(comparisonTemplate, lhsExpression, rhsExpression, "<");
        }

        if (operator.equals("<=")) {
            return String.format(comparisonTemplate, lhsExpression, rhsExpression, "<=");
        }

        String arithmaticTemplate = "(%4$s%1$s %3$s %2$s)";

        if (operator.equals("+")) {
            return String.format(arithmaticTemplate, lhsExpression, rhsExpression, "+", "");
        }

        if (operator.equals("-")) {
            return String.format(arithmaticTemplate, lhsExpression, rhsExpression, "-", "");
        }

        if (operator.equals("*")) {
            return String.format(arithmaticTemplate, lhsExpression, rhsExpression, "*", "");
        }

        if (operator.equals("/")) {
            return String.format(arithmaticTemplate, lhsExpression, rhsExpression, "/", "(double) ");
        }

        return "";
    }

    private String[] getSqlExpressionElement(String element) {
        String[] sqlExpressionElement = element.split(SQL_EXPRESSION_REGEX);
        if (sqlExpressionElement.length == 3) {
            return sqlExpressionElement;
        }

        return new String[] { sqlExpressionElement[0], sqlExpressionElement[1] + sqlExpressionElement[2],
                sqlExpressionElement[3] };
    }

    /**
     * Processes the where conditions in the query. The input query must provide a
     * table alias for all referenced tables. IN, NOT IN, EXISTS, IS NULL operators
     * are not supported. Converts to syntax that can be used in Java code Example :
     * AVG(x.S.quant) > 0 is converted to Double.compare(value.getMonth_S(), 0) > 0.
     *
     * @param component
     */
    private void processHavingConditions(String statement) {
        String[] elements = statement.split(CONDITION_SPLIT_REGEX);
        StringBuilder conditionBuilder = new StringBuilder();
        for (String element : elements) {
            element = element.trim();
            processConditionElement(conditionBuilder, element, "value.");
        }

        havingCondition = conditionBuilder.toString();
    }

    private void processConditionElement(StringBuilder conditionBuilder, String element, String prefix) {
        boolean startsWithBracket = false;
        if (element.charAt(0) == '(') {
            conditionBuilder.append("(");
            element = element.substring(1).trim();
            startsWithBracket = true;
        }

        if (element.equalsIgnoreCase("AND")) {
            conditionBuilder.append(" && ");
            return;
        }

        if (element.equalsIgnoreCase("OR")) {
            conditionBuilder.append(" || ");
            return;
        }

        String[] sqlExpressionElement = getSqlExpressionElement(element);

        String lhs = sqlExpressionElement[0].trim();
        String[] lhsComponents = getComponents(lhs);

        String generatedPrefix = prefix.isEmpty() ? lhsComponents[1] : prefix;

        String columnName = CommonUtils.toCamelCase(lhsComponents[0], false);
        String variableName = CommonUtils.append(columnName, "", lhsComponents[1], "_");
        String fullVariableName = CommonUtils.append(variableName, lhsComponents[2], "", "_");

        if (!lhsComponents[2].isEmpty()) {
            GroupingInformation group = groups.get(lhsComponents[3]);
            group.addAggregates(lhsComponents[2], variableName, false);
            fullVariableName = CommonUtils.append(fullVariableName, "", lhsComponents[3], "_");
        }

        String lhsExpression = CommonUtils.firstLetterToUpper(fullVariableName, generatedPrefix + "get", "()");

        relations.get(lhsComponents[1]).addColumn(columnName);

        String rhs = sqlExpressionElement[2].trim();
        String operator = sqlExpressionElement[1].trim();

        String closingParanthesis = "";

        if (rhs.endsWith(")") && startsWithBracket) {
            closingParanthesis = ")";
            rhs = rhs.substring(0, rhs.length() - 1).trim();
        }

        Deque<String> rhsParts = new LinkedList<>();
        rhsParts.addAll(Arrays.asList(rhs.split(OPERATORS_REGEX)));

        conditionBuilder.append(buildExpression(operator, lhsExpression, processRHS(rhsParts, null, prefix, true)))
                .append(closingParanthesis);
    }

    /**
     * Adds group 0 condition.
     *
     */
    private void processGroupZeroBoundryCondition() {
        GroupingInformation groupZero = groups.getOrDefault("0", null);
        if (groupZero == null) {
            return;
        }

        groupZero.addBoundryConditions(String.format("Objects.equals(%1$s, %2$s)", "this", "newRow"));
    }

    /**
     * Computes dependency between grouping variables. Assuming no cyclic dependency
     * exists, the variables are split into execution groups. All variables in a
     * execution group can be incremented simultaneously.
     */
    private void processGroupDependency() {
        Deque<String> toProcess = new LinkedList<>();

        for (Entry<String, GroupingInformation> entry : groups.entrySet()) {
            if (entry.getKey().equals("0")) {
                continue;
            }

            if (!entry.getValue().getDependentOn().isEmpty()) {
                toProcess.push(entry.getKey());
            } else {
                executionGroups.computeIfAbsent(1, k -> new HashSet<>()).add(entry.getKey());
            }
        }

        while (!toProcess.isEmpty()) {
            GroupingInformation currentNode = groups.get(toProcess.pop());
            int executionIndex = getExecutionIndex(currentNode.getDependentOn());
            if (executionIndex == -1) {
                toProcess.add(currentNode.getName());
                continue;
            }

            executionGroups.computeIfAbsent(executionIndex, k -> new HashSet<>()).add(currentNode.getName());
        }
    }

    /**
     * Checks if for a variable, all its parents are assigned a group, it returns
     * the next highest execution number. If even a single parent is not assigned a
     * group, returns -1.
     *
     * @param dependentOn
     * @return
     */
    private int getExecutionIndex(Set<String> dependentOn) {
        int executionIndex = -2;
        int parentFound = 0;

        for (String parent : dependentOn) {
            for (Entry<Integer, Set<String>> entry : executionGroups.entrySet()) {
                if (entry.getValue().contains(parent)) {
                    executionIndex = Math.max(executionIndex, entry.getKey());
                    parentFound++;
                    break;
                }
            }
        }

        if (parentFound != dependentOn.size()) {
            return -1;
        }

        return ++executionIndex;
    }

    /**
     * Processes order by conditions. If order by is specified, the MF-Table is a
     * TreeSet, else it is a HashMap. Presence of values in order by also determines
     * the implementation of compareTo method in the CompositeEntity.
     *
     * @param statement
     */
    private void processOrderByConditions(String statement) {
        String[] elements = statement.split(",");
        for (String element : elements) {
            String[] variables = getComponents(element.trim());
            String columnName = CommonUtils.toCamelCase(variables[0], false);
            String variableName = CommonUtils.append(columnName, "", variables[1], "_");
            String fullVariableName = CommonUtils.append(variableName, variables[2], variables[3], "_");

            orderByAttributes.add(CommonUtils.firstLetterToUpper(fullVariableName, "get", ""));
        }
    }

    public void addGroupingAttributes(String attribute) {
        groupingAttributes.add(attribute);
    }

    public Map<String, TableInformation> getRelations() {
        return relations;
    }

    public List<String> getProjections() {
        return projections;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getSelectConditions() {
        return selectConditions;
    }

    public String getHavingCondition() {
        return havingCondition;
    }

    public List<String> getGroupingAttributes() {
        return groupingAttributes;
    }

    public Map<String, GroupingInformation> getGroups() {
        return groups;
    }

    public Map<Integer, Set<String>> getExecutionGroups() {
        return executionGroups;
    }

    public List<String> getOrderByAttributes() {
        return orderByAttributes;
    }

    public int getOrderMultiplier() {
        return orderMultiplier;
    }

}
