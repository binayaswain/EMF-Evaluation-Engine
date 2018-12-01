/*
create view QView as
select cust, prod, max(quant) as maxq
from sales
group by cust, prod

select v.cust,v.prod,year, Quant
from sales s, QView v
where Quant = maxq and s.cust = v.cust
and s.prod = v.prod
*/


select S.cust as CUST, S.prod as PROD, x.S.year as YEAR, x.S.quant AS MAX
from sales S
group by S.cust, S.prod; x
such that x.S.quant= max(S.quant)