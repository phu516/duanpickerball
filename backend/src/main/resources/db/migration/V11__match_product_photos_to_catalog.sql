-- Product and apparel photos use free Unsplash images whose subjects match the catalog.
-- Keep image URLs in products.image_url so the catalog and cart APIs can serve them.
update products p
join product_variants v on v.product_id = p.id
set p.image_url = case
  when v.sku like 'PZ-001%' then 'https://images.unsplash.com/photo-1753901821774-22a88913130f?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-002%' then 'https://images.unsplash.com/photo-1769911112109-8ce1e4f75e19?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-003%' then 'https://images.unsplash.com/photo-1769911112258-47da2e8eee67?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-004%' then 'https://images.unsplash.com/photo-1757334489278-f42e7e028872?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-005%' then 'https://images.unsplash.com/photo-1762744826900-ff6cdf807d79?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-006%' then 'https://images.unsplash.com/photo-1686721135016-49949bd23352?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-007%' then 'https://images.unsplash.com/photo-1686721135029-3e3367daeab9?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-008%' then 'https://images.unsplash.com/photo-1580955027067-8ef70ef0c1cf?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-009%' then 'https://images.unsplash.com/photo-1550505095-81378a674395?auto=format&fit=crop&w=900&q=85'
  when v.sku like 'PZ-010%' then 'https://images.unsplash.com/photo-1676159435366-ed3c8cdd3567?auto=format&fit=crop&w=900&q=85'
  else p.image_url
end
where v.sku like 'PZ-00%';

-- Replace obviously unrelated Faker commerce names and descriptions with coherent demo products.
-- This lets every displayed demo image agree with its product details.
update products p
join categories c on c.id = p.category_id
join product_variants v on v.product_id = p.id
set p.brand = 'PickleZone',
    p.name = case c.slug
      when 'vot' then concat(case mod(p.id, 3)
        when 0 then 'Vợt pickleball Carbon Pro 16mm'
        when 1 then 'Vợt pickleball Control Lite'
        else 'Bộ vợt pickleball Starter Set' end, ' · mẫu ', right(v.sku, 4))
      when 'trang-phuc' then concat(case mod(p.id, 2)
        when 0 then 'Áo thi đấu DryFit'
        else 'Quần thể thao Flex' end, ' · mẫu ', right(v.sku, 4))
      when 'phu-kien' then concat(case mod(p.id, 3)
        when 0 then 'Bóng pickleball ngoài trời hộp 6'
        when 1 then 'Bộ quấn cán vợt chống trượt'
        else 'Túi đựng vợt pickleball' end, ' · mẫu ', right(v.sku, 4))
      when 'nuoc-uong' then concat(case mod(p.id, 3)
        when 0 then 'Nước suối 500ml'
        when 1 then 'Nước điện giải 500ml'
        else 'Trà chanh mật ong' end, ' · mẫu ', right(v.sku, 4))
      else p.name
    end,
    p.description = case c.slug
      when 'vot' then case mod(p.id, 3)
        when 0 then 'Mặt carbon nhẹ, cân bằng lực và độ kiểm soát cho những trận đấu thường xuyên.'
        when 1 then 'Thiết kế nhẹ, dễ điều khiển và phù hợp với người chơi ưu tiên độ chính xác.'
        else 'Bộ vợt kèm bóng và túi đựng, phù hợp cho người mới bắt đầu chơi pickleball.' end
      when 'trang-phuc' then case mod(p.id, 2)
        when 0 then 'Áo thể thao vải thoáng khí, nhanh khô khi vận động trên sân.'
        else 'Quần thể thao co giãn linh hoạt, thoải mái khi di chuyển và đổi hướng.' end
      when 'phu-kien' then case mod(p.id, 3)
        when 0 then 'Bóng pickleball ngoài trời có lỗ, độ nảy ổn định và dễ quan sát trên sân.'
        when 1 then 'Lớp quấn cán tăng độ bám, giúp cầm vợt chắc tay khi ra mồ hôi.'
        else 'Túi gọn nhẹ để bảo quản và mang theo vợt cùng phụ kiện ra sân.' end
      when 'nuoc-uong' then case mod(p.id, 3)
        when 0 then 'Nước uống đóng chai tiện mang theo để bổ sung nước trong buổi chơi.'
        when 1 then 'Thức uống điện giải giúp bù nước sau khi vận động.'
        else 'Trà chanh mát lạnh với hương chanh mật ong, phù hợp dùng sau trận đấu.' end
      else p.description
    end,
    p.image_url = case c.slug
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
      else p.image_url
    end
where v.sku like 'FAKER-%';
