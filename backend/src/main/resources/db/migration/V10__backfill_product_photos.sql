-- Curated photos for existing catalog rows without an image.
-- Photos are freely usable under the Unsplash License. Admins can replace
-- these illustrative URLs with exact SKU photos through the product editor.
update products p set image_url = 'https://images.unsplash.com/photo-1737229373530-4e982d21c8c3?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-001%');
update products p set image_url = 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-002%');
update products p set image_url = 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-003%');
update products p set image_url = 'https://images.unsplash.com/photo-1774459295077-9cc07d15ff1a?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-004%');
update products p set image_url = 'https://images.unsplash.com/photo-1714291067125-191d40cee861?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-005%');
update products p set image_url = 'https://images.unsplash.com/photo-1659318006095-4d44845f3a1b?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-006%');
update products p set image_url = 'https://images.unsplash.com/photo-1686721135029-3e3367daeab9?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-007%');
update products p set image_url = 'https://images.unsplash.com/photo-1576468475362-4785943dcda0?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-008%');
update products p set image_url = 'https://images.unsplash.com/photo-1525397053281-a37d8a2ff7ce?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-009%');
update products p set image_url = 'https://images.unsplash.com/photo-1681974913878-1c446bac7cf5?auto=format&fit=crop&w=900&q=85'
where p.image_url is null and exists (select 1 from product_variants v where v.product_id = p.id and v.sku like 'PZ-010%');

-- Remaining faker/demo rows have nonsensical names; show a matching category
-- image until an editor supplies a specific photo and correct product details.
update products p
set image_url = (select case c.slug
  when 'vot' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1769911112109-8ce1e4f75e19?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85' end
  when 'trang-phuc' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1774459295077-9cc07d15ff1a?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1714291067125-191d40cee861?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1768853972795-2739a9685567?auto=format&fit=crop&w=900&q=85' end
  when 'phu-kien' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1659318006095-4d44845f3a1b?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1769911111880-b9cc416dc03a?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85' end
  when 'nuoc-uong' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1682309832701-a29a1c2c6600?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1576468475362-4785943dcda0?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1525397053281-a37d8a2ff7ce?auto=format&fit=crop&w=900&q=85' end
  else 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
end from categories c where c.id = p.category_id)
where p.image_url is null or trim(p.image_url) = '';
