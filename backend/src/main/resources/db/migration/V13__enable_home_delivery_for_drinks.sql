-- Allow beverage products to be delivered to a customer's home.
insert into product_fulfillment_options (product_id, method)
select p.id, 'HOME_DELIVERY'
from products p
join categories c on c.id = p.category_id
where c.slug = 'nuoc-uong'
  and not exists (
    select 1
    from product_fulfillment_options existing
    where existing.product_id = p.id
      and existing.method = 'HOME_DELIVERY'
  );
