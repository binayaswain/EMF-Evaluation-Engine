package main.utils;

import java.text.DecimalFormat;
import java.util.logging.Logger;

/**
 * A Utility class providing features that are required all over the project
 *
 * @author R&B
 *
 */
public class CommonUtils {

    private static final Logger LOG = Logger.getLogger(CommonUtils.class.getCanonicalName());

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.000");

    public static final String STRING_CELL_FORMAT = "%1$-15.15s|";

    public static final String NUMBER_CELL_FORMAT = "%1$15.15s|";

    public static final String ROW_NUM_FORMAT = "|%1$3.3s|";

    public static final String DB_PROPERTIES = "./resources/database.properties";

    private CommonUtils() {
        // Private constructor to prevent object creation of this class.
    }

    /**
     * Converts a string to camel case. If the boolean parameter is false the first
     * letter of the word is not capitalized (naming convention for variables) else
     * the whole word is converted to camel case.
     *
     * @param input
     * @param firstLetterToUpper
     * @param suffix
     * @return string in camel case
     */
    public static String toCamelCase(String input, boolean firstLetterToUpper) {
        return toCamelCase(input, firstLetterToUpper, false, "");
    }

    public static String toCamelCase(String input, boolean firstLetterToUpper, boolean withUnderscore, String suffix) {
        StringBuilder camelCaseBuilder = new StringBuilder();
        boolean toUpper = firstLetterToUpper;

        for (char ch : input.toCharArray()) {
            if (toUpper) {
                camelCaseBuilder.append(Character.toUpperCase(ch));
                toUpper = false;
            } else if (!Character.isLetterOrDigit(ch)) {
                toUpper = true;
            } else {
                camelCaseBuilder.append(Character.toLowerCase(ch));
            }
        }

        if (withUnderscore) {
            camelCaseBuilder.append("_");
        }

        return camelCaseBuilder.append(suffix).toString();
    }

    /**
     * Capitalizes the first letter and adds a prefix and suffix. Useful for
     * defining method names.
     *
     * @param input
     * @param prefix
     * @return string in camel case with a prefix appended
     */
    public static String firstLetterToUpper(String input, String prefix, String suffix) {
        StringBuilder outputBuilder = new StringBuilder(prefix);
        boolean toUpper = true;

        for (char ch : input.toCharArray()) {
            if (toUpper) {
                outputBuilder.append(Character.toUpperCase(ch));
                toUpper = false;
                continue;
            }
            outputBuilder.append(ch);
        }

        return outputBuilder.append(suffix).toString();
    }

    /**
     * Exits the system. By convention, any status code other than 0 indicates
     * abnormal termination.
     *
     * @param status
     */
    public static void exit(int status) {
        if (status == 0) {
            LOG.info("Completed Successfully.");
        } else {
            LOG.info("Exiting due to falure.");
        }
        System.exit(status);
    }

    /**
     * Appends a prefix and suffix split by a separator.
     *
     * @param input
     * @param prefix
     * @param suffix
     * @param spearator
     * @return
     */
    public static String append(String input, String prefix, String suffix, String spearator) {
        StringBuilder outputBuilder = new StringBuilder(prefix);

        if (!prefix.isEmpty()) {
            outputBuilder.append(spearator);
        }

        outputBuilder.append(input);

        if (!suffix.isEmpty()) {
            outputBuilder.append(spearator);
        }

        return outputBuilder.append(suffix).toString();
    }

    /**
     * Finds the maximum amongst the elements.
     *
     * @param elements
     * @return
     */
    @SafeVarargs
    public static <T extends Comparable<? super T>> T max(T... elements) {
        if (elements == null || elements.length == 0 || elements[0] == null) {
            return null;
        }

        T candidate = elements[0];

        for (int i = 1; i < elements.length; i++) {
            T next = elements[i];
            if (next.compareTo(candidate) > 0) {
                candidate = next;
            }
        }

        return candidate;
    }

    /**
     * Finds the minimum amongst the elements.
     *
     * @param elements
     * @return
     */
    @SafeVarargs
    public static <T extends Comparable<? super T>> T min(T... elements) {
        if (elements == null || elements.length == 0 || elements[0] == null) {
            return null;
        }

        T candidate = elements[0];

        for (int i = 1; i < elements.length; i++) {
            T next = elements[i];
            if (next.compareTo(candidate) < 0) {
                candidate = next;
            }
        }

        return candidate;
    }

    /**
     * Generate a row separator string.
     *
     * @param length
     * @return
     */
    public static String getRowSeparator(int length) {
        StringBuilder sb = new StringBuilder(System.lineSeparator());
        for (int i = 0; i < length - 2; i++) {
            sb.append('-');
        }
        return sb.toString();
    }

}
