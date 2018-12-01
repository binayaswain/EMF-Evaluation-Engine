
import java.lang.reflect.Method;

import org.junit.Test;

import main.database.MetaDataAccessObject;
import main.parser.ParsedQuery;
import main.readwrite.ReadWrite;
import main.utils.CodeGenerator;

public class ParserTest {

    public static final String COMPARE_OTHER_SQL = "./resources/CompareOthers.sql";

    public static final String COMPARE_STATES_SQL = "./resources/CompareStates.sql";

    public static final String MONTH_AVG_SQL = "./resources/MonthAvg.sql";

    public static final String COMPARE_MONTH_SQL = "./resources/MonthCompare.sql";

    public static final String MONTH_PERCENTAGE_SQL = "./resources/MonthPctg.sql";

    public static final String NO_AGGREGATES_SQL = "./resources/NoAggs.sql";

    @Test
    public void parseSQLTest() throws Exception {
        String sql = "select S.cust,avg(S.quant),avg(x.S.quant),avg(y.S.quant),sum(z.S.quant)/count(z.S.quant)"
                + " from sales S where S.year=1997 group by S.cust ; x,y,z\r\n"
                + " such that x.S.cust=S.cust and x.S.state=\"PA\", y.S.cust=S.cust and y.S.state=\"CT\","
                + " z.S.cust=S.cust and z.S.state=\"NJ\""
                + " having avg(x.S.quant)>avg(y.S.quant) and avg(x.S.quant)>avg(z.S.quant)";

        ParsedQuery parsedQuery = new ParsedQuery();

        parsedQuery.processSQL(sql);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void parseMultiTableSQLTest() throws Exception {
        String sql = "select S.cust,avg(S.quant),avg(x.S.quant),avg(y.S.quant),sum(z.S.quant)/count(x.S.quant)*100"
                + " from sales S, sales C where S.year=C.year and (S.year=1997 or C.year<>1998)"
                + " group by S.cust, C.cust ; x,y,z"
                + " such that x.S.cust=S.cust and x.C.cust=C.cust and x.S.state=\"NY\","
                + " y.S.cust=S.cust and y.S.state=\"CT\", z.S.cust=S.cust and z.S.state=\"NJ\""
                + " having avg(x.S.quant)>avg(y.S.quant) and avg(x.S.quant)>avg(z.S.quant)";

        ParsedQuery parsedQuery = new ParsedQuery();

        parsedQuery.processSQL(sql);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void compareOtherSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(COMPARE_OTHER_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void compareStateSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(COMPARE_STATES_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void monthAvgSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(MONTH_AVG_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void compareMonthSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(COMPARE_MONTH_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void monthPercentageSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(MONTH_PERCENTAGE_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }

    @Test
    public void noAggerigateSql() throws Exception {
        ParsedQuery parsedQuery = ReadWrite.readSQLFromFile(NO_AGGREGATES_SQL);

        MetaDataAccessObject dao = new MetaDataAccessObject();

        CodeGenerator.cleanDirectory();

        CodeGenerator.createEntities(parsedQuery, dao);

        CodeGenerator.createCompositeEntity(parsedQuery);

        CodeGenerator.createDataAccessObject(parsedQuery);

        CodeGenerator.createEvaluationEngine(parsedQuery);

        Class<?> clazz = Class.forName("main.generated.EvaluationEngine");

        Method method = clazz.getMethod("main", String[].class);

        method.invoke(null, (Object) new String[] { "noCsv" });
    }
}
