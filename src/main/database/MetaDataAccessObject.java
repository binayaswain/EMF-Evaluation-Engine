package main.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.parser.TableInformation;
import main.readwrite.ReadWrite;
import main.utils.CommonUtils;

/**
 * The class connects to the database using properties defined in the properties
 * file to fetch metadata for tables.
 *
 * @author R&B
 *
 */
public class MetaDataAccessObject {

    private static final Logger LOG = Logger.getLogger(MetaDataAccessObject.class.getCanonicalName());

    private static final String INFO_QUERY = "select column_name, data_type from information_schema.columns where table_name=?";

    private static final String ROW_COUNT_QUERY = "select n_live_tup from pg_stat_user_tables where relname=?";

    private final Properties credentials;

    public MetaDataAccessObject() {
        credentials = ReadWrite.readProperties(CommonUtils.DB_PROPERTIES);
    }

    public void populateTableMetadata(TableInformation information) {
        String tableName = information.getTableName();
        Map<String, Class<?>> columns = information.getRequiredColumns();

        try (Connection connection = DriverManager.getConnection(credentials.getProperty("url"), credentials);
                PreparedStatement columnInfoStatement = connection.prepareStatement(INFO_QUERY);
                PreparedStatement rowCountStatement = connection.prepareStatement(ROW_COUNT_QUERY)) {

            // Fetches column data type
            columnInfoStatement.setString(1, tableName);
            ResultSet columnInfoResult = columnInfoStatement.executeQuery();
            transformResultSet(tableName, columnInfoResult, columns);

            // Fetches the row count statistic
            rowCountStatement.setString(1, tableName);
            ResultSet rowCountResult = rowCountStatement.executeQuery();
            processRowCount(rowCountResult, information);

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Could not execute query.", e);
            CommonUtils.exit(1);
        }
    }

    private void transformResultSet(String tableName, ResultSet resultSet, Map<String, Class<?>> result)
            throws SQLException {
        if (!resultSet.next()) {
            LOG.log(Level.SEVERE, "Table with name \"{0}\" does not exist", tableName);
            CommonUtils.exit(1);
        }
        // Only populates data type for columns used in the query.
        do {
            String name = CommonUtils.toCamelCase(resultSet.getString(1), false);
            if ("integer".equalsIgnoreCase(resultSet.getString(2)) && result.containsKey(name)) {
                result.put(name, Integer.class);
            }

        } while (resultSet.next());
    }

    private void processRowCount(ResultSet resultSet, TableInformation information) throws SQLException {
        if (resultSet.next()) {
            information.setNumberOfRows(resultSet.getInt(1));
        }
    }
}
