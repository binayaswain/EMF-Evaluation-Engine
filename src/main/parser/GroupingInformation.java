package main.parser;

import java.util.HashSet;
import java.util.Set;

import main.utils.CommonUtils;

/**
 * Contains information pertaining to individual grouping variables like
 * aggregates to compute, dependency on other grouping variables and its such
 * that (boundary) condition.
 *
 * @author R&B
 *
 */
public class GroupingInformation {

    private final String name;

    private final StringBuilder boundryConditions;

    private final Set<String> dependentOn;

    private final Set<String> aggregates;

    public GroupingInformation(String variableName) {
        name = variableName;
        boundryConditions = new StringBuilder();
        dependentOn = new HashSet<>();
        aggregates = new HashSet<>();
    }

    public void addBoundryConditions(String conditions) {
        boundryConditions.append(conditions);
    }

    public void addAggregates(String aggregateName, String variableFullName, boolean forceAdd) {
        if (aggregateName.isEmpty() && !forceAdd) {
            return;
        }

        if (aggregateName.equals("avg")) {
            aggregates.add(CommonUtils.append(variableFullName, "sum", name, "_"));
            aggregates.add(CommonUtils.append(variableFullName, "count", name, "_"));
            aggregates.add(CommonUtils.append(variableFullName, "avg", name, "_"));
            return;
        }

        aggregates.add(CommonUtils.append(variableFullName, aggregateName, name, "_"));
    }

    public void addDependentOn(String variableName) {
        dependentOn.add(variableName);
    }

    public String getName() {
        return name;
    }

    public String getBoundryConditions() {
        return boundryConditions.toString();
    }

    public Set<String> getDependentOn() {
        return dependentOn;
    }

    public Set<String> getAggregates() {
        return aggregates;
    }
}
