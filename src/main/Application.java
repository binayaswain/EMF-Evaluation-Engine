package main;

import java.lang.reflect.Method;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.database.MetaDataAccessObject;
import main.parser.ParsedQuery;
import main.readwrite.ReadWrite;
import main.utils.CodeGenerator;
import main.utils.CommonUtils;

/**
 * Stating point for the program. Must use a JDK to run and other classes and
 * generated and compiled during execution.
 *
 * @author R&B
 *
 */
public class Application {

    private static final Logger LOG = Logger.getLogger(Application.class.getCanonicalName());

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            ParsedQuery parsedQuery = ReadWrite.acceptUserInput(sc);
            MetaDataAccessObject dao = new MetaDataAccessObject();

            CodeGenerator.cleanDirectory();
            CodeGenerator.createEntities(parsedQuery, dao);
            CodeGenerator.createCompositeEntity(parsedQuery);
            CodeGenerator.createDataAccessObject(parsedQuery);
            CodeGenerator.createEvaluationEngine(parsedQuery);

            if (ReadWrite.isExecute(sc)) {
                Class<?> clazz = Class.forName("main.generated.EvaluationEngine");
                Method method = clazz.getMethod("main", String[].class);
                method.invoke(null, (Object) null);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Could not execute evaluation engine.", e);
            CommonUtils.exit(1);
        }

        CommonUtils.exit(0);
    }

}
