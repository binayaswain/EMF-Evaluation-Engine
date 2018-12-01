/*
create or replace view d1 as
select s1.cust, s1.prod, round(avg(s2.quant),2) as avgq
from Sales s1, Sales s2
where s1.cust<>s2.cust and s1.prod = s2.prod
group by s1.cust, s1.prod

select s.cust, s.prod, round(avg(s.quant),2), avgq
from Sales s, d1
where s.cust = d1.cust and s.prod = d1.prod
group by s.cust, s.prod,avgq
order by s.cust, s.prod
*/

select S.cust AS CUST, S.prod as PROD, avg(x.S.quant) as AVG, avg(y.S.quant) as AVG_OTH
from sales S
group by S.cust, S.prod ; x, y
such that x.S.cust=S.cust and x.S.prod=S.prod,
y.S.cust<>S.cust and y.S.prod=S.prod