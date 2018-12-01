/*
create view B1 as
select x.cust, x.month, avg(y.quant) as avgx
from Sales x, Sales y
where  x.cust=y.cust and
x.month>y.month
and x.year = 1997
group by x.cust, x.month

create view B2 as
select x.cust, x.month, avg(y.quant) as avgy
from Sales x, Sales y
where  x.cust=y.cust and
x.month<y.month
and x.year = 1997
group by x.cust, x.month

select B1.cust, B1.month, round(avg(s.quant),2), round(avgx,2), round(avgy,2)
from Sales s,B1, B2
where  s.cust = B1.cust
and B1.cust=B2.cust and
s.month = B1.month and
B1.month=B2.month
and s.year = 1997
group by B1.cust, B1.month, avgx, avgy
*/

select S.cust as CUST, S.month as MONTH, avg(x.S.quant) as AVG_BEF, avg(S.quant) as AVG_CUR, avg(y.S.quant) as AVG_AFT
from sales S
where S.year=1997
group by S.cust, S.month ;  x, y
such that x.S.cust=S.cust and x.S.month < S.month,
y.S.cust=S.cust and y.S.month > S.month