package com.pickleball.setting.controller;

import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class HeroBannerController {
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public HeroBannerController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/hero/banner")
    public ResponseEntity<Banner> publicBanner() {
        Banner banner = savedBanner();
        LocalDateTime now = LocalDateTime.now(LOCAL_ZONE);
        if (banner == null || !banner.enabled() || now.isBefore(parseTime(banner.visibleFrom()))
                || now.isAfter(parseTime(banner.visibleUntil())))
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(banner);
    }

    @GetMapping("/admin/hero/banner")
    public ResponseEntity<Banner> adminBanner() {
        Banner banner = savedBanner();
        return banner == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(banner);
    }

    @GetMapping("/hero/banner/image")
    public ResponseEntity<byte[]> image() {
        Banner banner = savedBanner();
        LocalDateTime now = LocalDateTime.now(LOCAL_ZONE);
        if (banner == null || !banner.enabled() || now.isBefore(parseTime(banner.visibleFrom()))
                || now.isAfter(parseTime(banner.visibleUntil()))) return ResponseEntity.notFound().build();
        return savedImage();
    }

    @GetMapping("/admin/hero/banner/image")
    public ResponseEntity<byte[]> adminImage() { return savedImage(); }

    private ResponseEntity<byte[]> savedImage() {
        List<Image> images = jdbc.query("select image_content_type, image_data from hero_banner where id=1",
                (rs, row) -> new Image(rs.getString(1), rs.getBytes(2)));
        if (images.isEmpty()) return ResponseEntity.notFound().build();
        Image image = images.getFirst();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    @PostMapping(value = "/admin/hero/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Banner save(@RequestPart("details") String json,
                       @RequestPart(value = "image", required = false) MultipartFile file) throws IOException {
        Details details;
        try { details = mapper.readValue(json, Details.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Thông tin banner không hợp lệ"); }
        validate(details);

        byte[] bytes = null;
        String contentType = null;
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Ảnh phải nhỏ hơn 5 MB");
            bytes = file.getBytes();
            contentType = detectImageType(bytes);
        } else if (savedBanner() == null) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh banner");
        }

        LocalDateTime updatedAt = LocalDateTime.now(LOCAL_ZONE);
        LocalDateTime eventStartAt = parseTime(details.eventStartAt());
        LocalDateTime visibleFrom = parseTime(details.visibleFrom());
        LocalDateTime visibleUntil = parseTime(details.visibleUntil());
        if (savedBanner() == null) {
            jdbc.update("insert into hero_banner (id, title, summary, event_start_at, visible_from, visible_until, link_url, image_content_type, image_data, enabled, updated_at) values (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    details.title().trim(), details.summary().trim(), eventStartAt,
                    visibleFrom, visibleUntil, cleanLink(details.linkUrl()),
                    contentType, bytes, details.enabled(), updatedAt);
        } else if (bytes != null) {
            jdbc.update("update hero_banner set title=?, summary=?, event_start_at=?, visible_from=?, visible_until=?, link_url=?, image_content_type=?, image_data=?, enabled=?, updated_at=? where id=1",
                    details.title().trim(), details.summary().trim(), eventStartAt,
                    visibleFrom, visibleUntil, cleanLink(details.linkUrl()),
                    contentType, bytes, details.enabled(), updatedAt);
        } else {
            jdbc.update("update hero_banner set title=?, summary=?, event_start_at=?, visible_from=?, visible_until=?, link_url=?, enabled=?, updated_at=? where id=1",
                    details.title().trim(), details.summary().trim(), eventStartAt,
                    visibleFrom, visibleUntil, cleanLink(details.linkUrl()),
                    details.enabled(), updatedAt);
        }
        return savedBanner();
    }

    private Banner savedBanner() {
        return jdbc.query("select title, summary, event_start_at, visible_from, visible_until, link_url, enabled, updated_at from hero_banner where id=1",
                rs -> rs.next() ? new Banner(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime().toString(),
                        rs.getTimestamp(4).toLocalDateTime().toString(), rs.getTimestamp(5).toLocalDateTime().toString(),
                        rs.getString(6), rs.getBoolean(7), rs.getTimestamp(8).toLocalDateTime().toString()) : null);
    }

    private void validate(Details details) {
        if (details == null || details.title() == null || details.title().isBlank() || details.title().length() > 120)
            throw new IllegalArgumentException("Tên giải đấu phải có từ 1 đến 120 ký tự");
        if (details.summary() == null || details.summary().isBlank() || details.summary().length() > 300)
            throw new IllegalArgumentException("Mô tả phải có từ 1 đến 300 ký tự");
        if (details.eventStartAt() == null || details.visibleFrom() == null || details.visibleUntil() == null
                || !parseTime(details.visibleUntil()).isAfter(parseTime(details.visibleFrom())))
            throw new IllegalArgumentException("Thời gian hiển thị không hợp lệ");
        parseTime(details.eventStartAt());
        cleanLink(details.linkUrl());
    }

    private LocalDateTime parseTime(String value) {
        try { return LocalDateTime.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Thời gian không hợp lệ"); }
    }

    private String cleanLink(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 500) throw new IllegalArgumentException("Liên kết quá dài");
        URI uri;
        try { uri = URI.create(trimmed); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Liên kết không hợp lệ"); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) throw new IllegalArgumentException("Liên kết phải bắt đầu bằng https:// hoặc http://");
        return trimmed;
    }

    private String detectImageType(byte[] bytes) throws IOException {
        if (bytes.length < 8 || bytes.length > MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("Ảnh phải nhỏ hơn 5 MB");
        boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
        try {
            if ((!png && !jpeg) || ImageIO.read(new ByteArrayInputStream(bytes)) == null)
                throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG hoặc JPEG hợp lệ");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Ảnh PNG hoặc JPEG không hợp lệ");
        }
        return png ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
    }

    public record Details(String title, String summary, String eventStartAt,
                          String visibleFrom, String visibleUntil,
                          String linkUrl, boolean enabled) {}
    public record Banner(String title, String summary, String eventStartAt,
                         String visibleFrom, String visibleUntil,
                         String linkUrl, boolean enabled, String updatedAt) {}
    private record Image(String contentType, byte[] bytes) {}
}
