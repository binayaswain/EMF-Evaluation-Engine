/*
create or replace view a1 as
select prod, month, sum(quant) as monthsale
from Sales
where year = 1997
group by prod, month
order by prod, month

create or replace view a2 as
select prod, sum(quant) as yearsale
from Sales
where year = 1997
group by prod

select a1.prod, a1.month, round(100*a1.monthsale/a2.yearsale,2)
from a1, a2
where a1.prod = a2.prod
order by a1.prod,a1.month;
*/

select S.prod as PROD, S.month as MONTH, sum(x.S.quant)/sum(y.S.quant)*100 AS PERCT
from sales S
where S.year=1997
group by S.prod, S.month ; x,y
such that x.S.prod = S.prod and x.S.month = S.month, y.S.prod = S.prod
