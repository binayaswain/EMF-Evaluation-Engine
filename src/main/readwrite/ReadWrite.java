package main.readwrite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.parser.ParsedQuery;
import main.utils.CommonUtils;

/**
 * Provides functions read and write data.
 *
 * @author R&B
 *
 */
public class ReadWrite {

    private static final Logger LOG = Logger.getLogger(CommonUtils.class.getCanonicalName());

    private static final String LINE_FEED = System.lineSeparator();

    private static final String LINE_SEPARATOR = "-----------------------------------------";

    private static final String MESSAGE = new StringBuilder(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED)
            .append("\tSelect the input method").append(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED)
            .append("1.\tSQL from command line").append(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED)
            .append("2.\tParameters from command line").append(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED)
            .append("3.\tSQL from file").append(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED)
            .append("4.\tExit").append(LINE_FEED).append(LINE_SEPARATOR).append(LINE_FEED).append(LINE_FEED)
            .append("Enter the choice : ").toString();

    private static final String INVALID_INPUT = "Invalid input.";

    private ReadWrite() {
        // Private constructor to prevent object creation of this class.
    }

    /**
     * Provides a set of choices to the user in order to enter the query for
     * evaluation.
     *
     * @param sc
     * @return ParsedQuery
     */
    public static ParsedQuery acceptUserInput(Scanner sc) {
        int choice = -1;

        while (choice == -1) {
            System.out.print(MESSAGE);

            String input = sc.nextLine();

            if (input == null || input.isEmpty() || !input.matches("\\d")) {
                System.out.println(INVALID_INPUT);
                continue;
            }

            choice = Integer.parseInt(input);
        }

        switch (choice) {
            case 1:
                return ReadWrite.readSQLFromCommandLine(sc);
            case 2:
                return ReadWrite.readParameterFromCommandLine(sc);
            case 3:
                return ReadWrite.readSQLFromFile(sc);
            default:
                CommonUtils.exit(0);
                return new ParsedQuery();
        }
    }

    /**
     * Accepts a SQL query from the command line.
     *
     * @param sc
     * @return ParsedQuery
     */
    public static ParsedQuery readSQLFromCommandLine(Scanner sc) {
        StringBuilder sqlBuilder = new StringBuilder();
        System.out.println();
        System.out.println("Enter the sql to be processed (type / in a new line to finish)");
        System.out.println();
        String input = sc.nextLine();

        while (!input.equals("/")) {
            sqlBuilder.append(input).append(" ");
            input = sc.nextLine();
        }

        ParsedQuery parsedQuery = new ParsedQuery();
        parsedQuery.processSQL(sqlBuilder.toString());

        return parsedQuery;
    }

    /**
     * Accepts a file name from user and reads the query.
     *
     * @param sc
     * @return ParsedQuery
     */
    public static ParsedQuery readSQLFromFile(Scanner sc) {
        File input = null;
        String line = "";
        while (input == null) {
            System.out.println();
            System.out.print("Enter the file name : ");
            line = sc.nextLine();

            if (line == null || line.isEmpty()) {
                System.out.println(INVALID_INPUT);
                continue;
            }

            input = new File(line);

            if (!input.exists()) {
                System.out.println(INVALID_INPUT);
                input = null;
            }
        }

        ParsedQuery parsedQuery = new ParsedQuery();
        parsedQuery.processSQL(readFile(input));

        return parsedQuery;
    }

    /**
     * Reads the query from the specified file.
     *
     * @param fileName
     * @return ParsedQuery
     */
    public static ParsedQuery readSQLFromFile(String fileName) {
        ParsedQuery parsedQuery = new ParsedQuery();
        parsedQuery.processSQL(readFile(new File(fileName)));

        return parsedQuery;
    }

    private static String readFile(File input) {
        StringBuilder sqlBuilder = new StringBuilder();
        String line = "";
        try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
            line = "";
            while ((line = reader.readLine()) != null) {
                // Ignore single line comments in the SQL file
                if (line.startsWith("--") || line.isEmpty()) {
                    continue;
                }
                // Ignores multiple line comments in the SQL file
                if (line.startsWith("/*")) {
                    while (!line.endsWith("*/")) {
                        line = reader.readLine();
                    }
                } else {
                    sqlBuilder.append(line).append(" ");
                }
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error while reading file.", e);
        }

        return sqlBuilder.toString();
    }

    /**
     * Outputs a CSV file with the data in result.
     *
     * @param result
     * @param csvFileName
     */
    public static void generateCsv(String result, String csvFileName) {
        try (BufferedWriter writer = Files.newBufferedWriter(new File(csvFileName).toPath())) {
            writer.write(result);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error in writing file", e);
            CommonUtils.exit(1);
        }
    }

    /**
     * Reads and processes properties file.
     *
     * @param propertiesFile
     * @return Properties
     */
    public static Properties readProperties(String propertiesFile) {
        Properties properties = new Properties();
        Path myPath = Paths.get(propertiesFile);
        try (BufferedReader bf = Files.newBufferedReader(myPath, StandardCharsets.UTF_8)) {
            properties.load(bf);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not read properties File.", e);
            CommonUtils.exit(1);
        }
        return properties;
    }

    /**
     * Accepts file name from user. Returns null if to be displayed on the console.
     *
     * @return cavFileName
     */
    public static String getCsvFileName() {
        String line = "";
        try (Scanner sc = new Scanner(System.in)) {
            while (line == null || line.isEmpty()) {
                System.out.println();
                System.out.print("Do you want to export result to a csv file ? [Y/N] : ");
                line = sc.nextLine();
                if ("Y".equalsIgnoreCase(line)) {
                    return getFileName(sc);
                }
                if ("N".equalsIgnoreCase(line)) {
                    return null;
                }
                System.out.println(INVALID_INPUT);
                line = null;
            }
        }
        return line;
    }

    private static String getFileName(Scanner sc) {
        String line = "";
        while (line == null || line.isEmpty()) {
            System.out.println();
            System.out.print("Enter the file name : ");
            line = sc.nextLine();

            if (line == null || line.isEmpty()) {
                System.out.println(INVALID_INPUT);
                continue;
            }
        }
        return line;
    }

    /**
     * Asks user whether to execute the query.
     *
     * @param sc
     * @return boolean
     */
    public static boolean isExecute(Scanner sc) {
        String line = "";
        while (line == null || line.isEmpty()) {
            System.out.println();
            System.out.print("Do you want to execute the evaluation engine ? [Y/N] : ");
            line = sc.nextLine();

            if ("Y".equalsIgnoreCase(line)) {
                return true;
            }

            if ("N".equalsIgnoreCase(line)) {
                return false;
            }

            System.out.println(INVALID_INPUT);
            line = null;
        }

        return false;
    }

    /**
     * Accepts individual query components from the user.
     *
     * @param sc
     * @return ParsedQuery
     */
    public static ParsedQuery readParameterFromCommandLine(Scanner sc) {
        List<String> cmponents = new ArrayList<>();
        String input = null;
        System.out.println("Enter the components (with table aliases) of the query");
        System.out.println();

        System.out.print("Projection: ");
        input = sc.nextLine();
        while (input == null) {
            System.out.println(INVALID_INPUT);
            System.out.print("Projection: ");
            input = sc.nextLine();
        }
        cmponents.add("select");
        cmponents.add(input);

        System.out.print("Relations: ");
        input = sc.nextLine();
        while (input == null) {
            System.out.println(INVALID_INPUT);
            System.out.print("Relations: ");
            input = sc.nextLine();
        }
        cmponents.add("from");
        cmponents.add(input);

        System.out.print("Where Condition: ");
        input = sc.nextLine();

        if (input != null) {
            cmponents.add("where");
            cmponents.add(input);
        }

        System.out.print("Grouping Attributes: ");
        input = sc.nextLine();
        while (input == null) {
            System.out.println(INVALID_INPUT);
            System.out.print("Grouping Attributes: ");
            input = sc.nextLine();
        }

        System.out.print("Grouping Variables: ");
        String variables = sc.nextLine();
        while (input == null) {
            System.out.println(INVALID_INPUT);
            System.out.print("Grouping Variables: ");
            variables = sc.nextLine();
        }
        cmponents.add("group by");
        cmponents.add(input + ";" + variables);

        System.out.print("Such That Condition: ");
        input = sc.nextLine();
        while (input == null) {
            System.out.println(INVALID_INPUT);
            System.out.print("Such That Condition: ");
            input = sc.nextLine();
        }
        cmponents.add("such that");
        cmponents.add(input);

        System.out.print("Having Condition: ");
        input = sc.nextLine();

        if (input != null) {
            cmponents.add("having");
            cmponents.add(input);
        }

        System.out.print("Order By: ");
        input = sc.nextLine();

        if (input != null) {
            cmponents.add("order by");
            cmponents.add(input);
        }

        ParsedQuery parsedQuery = new ParsedQuery();
        parsedQuery.processSqlKeywords(cmponents.toArray(new String[0]));

        return parsedQuery;
    }
}
