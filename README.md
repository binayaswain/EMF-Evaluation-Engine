# EMF-Evaluation-Engine
Pre-requisites:
While writing the query, please include the table alias, and subsequently associate that alias with all columns related to that query.
This includes all the grouping variables that access columns of the specific table.

For example:
select S.cust as CUST, S.month as MONTH, count(z.S.quant) as COUNT
from sales S
where S.year=1997
group by S.cust, S.month ; x,y,z
such that x.S.cust = S.cust and x.S.month = S.month-1,
y.S.cust = S.cust and y.S.month = S.month+1,
z.S.cust = S.cust and z.S.month = S.month and
z.S.quant>avg(x.S.quant) and z.S.quant<avg(y.S.quant)
having count(z.S.quant)>0
order by S.cust, S.month;

How to run:
A batch file "RunApplication.bat" has been created which will compile and run the program and create the generated objects under the path main/generated.
After running this script, an option will appear whether to run the evaluation engine or no. 
If "Y" is selected, the output will be processed and displayed as a Java output, or a .csv file (user's choice).
If "N" is selected, the generated Java class "EvaluationEngine.java" can be run manually to display the output in a similar manner. 
