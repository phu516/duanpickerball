-- Backfill every existing product that still has no image URL.
-- Images are selected by category so they match the product catalog content.
update products p
join categories c on c.id = p.category_id
set p.image_url = case c.slug
  when 'vot' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1769911112109-8ce1e4f75e19?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85' end
  when 'trang-phuc' then case mod(p.id, 2)
    when 0 then 'https://images.unsplash.com/photo-1757334489278-f42e7e028872?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1762744826900-ff6cdf807d79?auto=format&fit=crop&w=900&q=85' end
  when 'phu-kien' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1686721135016-49949bd23352?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1686721135029-3e3367daeab9?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85' end
  when 'nuoc-uong' then case mod(p.id, 3)
    when 0 then 'https://images.unsplash.com/photo-1580955027067-8ef70ef0c1cf?auto=format&fit=crop&w=900&q=85'
    when 1 then 'https://images.unsplash.com/photo-1550505095-81378a674395?auto=format&fit=crop&w=900&q=85'
    else 'https://images.unsplash.com/photo-1676159435366-ed3c8cdd3567?auto=format&fit=crop&w=900&q=85' end
  else 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
end
where p.image_url is null or trim(p.image_url) = '';
