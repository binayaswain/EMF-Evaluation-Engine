/*
create or replace view v1 as
select cust, round(avg(quant),2) as NYsale
from Sales
where state = 'NY' and year = 1997
group by cust;

create or replace view v2 as
select cust, round(avg(quant),2) as NJsale
from Sales
where state = 'NJ' and year = 1997
group by cust;

create or replace view v3 as
select cust, round(avg(quant),2) as CTsale
from Sales
where state = 'CT' and year = 1997
group by cust;

select s.cust, v1.NYsale, v2.NJsale, v3.CTsale
from sales s, v1, v2, v3
where s.cust = v1.cust 
and v1.cust = v2.cust and v2.cust = v3.cust
and v1.NYsale>v2.NJsale and v1.NYsale>v3.CTsale
group by s.cust, v1.NYsale, v2.NJsale, v3.CTsale;
*/

select S.cust as CUST, avg(x.S.quant) as AVG_NY, avg(y.S.quant) as AVG_CT, avg(z.S.quant) as AVG_NJ
from sales S
where S.year=1997
group by S.cust; x,y,z
such that x.S.cust=S.cust and x.S.state="NY",
y.S.cust=S.cust and y.S.state="CT",
z.S.cust=S.cust and z.S.state="NJ"
having avg(x.S.quant)>avg(y.S.quant) and avg(x.S.quant)>avg(z.S.quant);
