/*
create or replace view C1 as
select x.cust, x.month, avg(y.quant) as avgx
from Sales x, Sales y
where  x.cust=y.cust
and x.month=y.month+1
and x.year = 1997 and y.year=1997
group by x.cust, x.month;

create or replace view C2 as
select x.cust, x.month, avg(y.quant) as avgy
from Sales x, Sales y
where  x.cust=y.cust
and x.month=y.month-1
and x.year = 1997 and y.year=1997
group by x.cust, x.month;

create or replace view C3 as
select x.cust as cust1, x.month as month1, y.cust as cust2, y.month as month2, x.avgX as avgX, y.avgY as avgY
from C1 x full outer join C2 y
on x.cust = y.cust
and x.month = y.month;

select s.cust, s.month, count(s.quant) as countq
from Sales s,C3
where s.year = 1997
and (s.cust = C3.cust1 or s.cust = C3.cust2)
and (s.month = C3.month1 or s.month = C3.month2)
and ((C3.avgX is null and s.quant<C3.avgY) or (s.quant>C3.avgX and s.quant<C3.avgY))
group by s.cust, s.month;
*/

select S.cust as CUST, S.month as MONTH, count(z.S.quant) as COUNT
from sales S
where S.year=1997
group by S.cust, S.month ; x,y,z
such that x.S.cust = S.cust and x.S.month = S.month-1,
y.S.cust = S.cust and y.S.month = S.month+1,
z.S.cust = S.cust and z.S.month = S.month and
z.S.quant>avg(x.S.quant) and z.S.quant<avg(y.S.quant)
having count(z.S.quant)>0
order by S.cust, S.month