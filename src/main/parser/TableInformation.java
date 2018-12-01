package main.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains information pertaining to individual tables name, associated entity
 * class, columns and their data type.
 *
 * @author R&B
 *
 */
public class TableInformation implements Comparable<TableInformation> {

    private String tableName;

    private String className;

    private int numberOfRows;

    private final String alias;

    private final Map<String, Class<?>> requiredColumns;

    public TableInformation(String variableName) {
        alias = variableName;
        requiredColumns = new HashMap<>();
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setNumberOfRows(int numberOfRows) {
        this.numberOfRows = numberOfRows;
    }

    public void addColumn(String columnName) {
        requiredColumns.computeIfAbsent(columnName, k -> String.class);
    }

    public String getTableName() {
        return tableName;
    }

    public String getClassName() {
        return className;
    }

    public int getNumberOfRows() {
        return numberOfRows;
    }

    public Map<String, Class<?>> getRequiredColumns() {
        return requiredColumns;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public int compareTo(TableInformation o) {
        return numberOfRows < o.getNumberOfRows() ? 1 : -1;
    }
}
