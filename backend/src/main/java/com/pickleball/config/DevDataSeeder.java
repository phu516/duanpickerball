package com.pickleball.config;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.booking.entity.Booking;
import com.pickleball.booking.repository.BookingRepository;
import com.pickleball.category.entity.Category;
import com.pickleball.category.repository.CategoryRepository;
import com.pickleball.court.entity.Court;
import com.pickleball.court.entity.CourtPriceRule;
import com.pickleball.court.repository.CourtPriceRuleRepository;
import com.pickleball.court.repository.CourtRepository;
import com.pickleball.product.entity.Product;
import com.pickleball.product.entity.ProductFulfillmentOption;
import com.pickleball.product.entity.ProductVariant;
import com.pickleball.product.repository.ProductFulfillmentRepository;
import com.pickleball.product.repository.ProductRepository;
import com.pickleball.product.repository.ProductVariantRepository;
import net.datafaker.Faker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "mysql-seed"})
public class DevDataSeeder implements CommandLineRunner {
    private final UserAccountRepository users;
    private final BookingRepository bookings;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductFulfillmentRepository fulfillment;
    private final CourtRepository courts;
    private final CourtPriceRuleRepository prices;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String adminUsername;
    private final String adminPassword;
    private final String staffUsername;
    private final String staffPassword;

    public DevDataSeeder(UserAccountRepository users, BookingRepository bookings,
                         CategoryRepository categories,
                         ProductRepository products, ProductVariantRepository variants,
                         ProductFulfillmentRepository fulfillment, CourtRepository courts,
                         CourtPriceRuleRepository prices, PasswordEncoder encoder,
                         @Value("${app.seed.enabled:false}") boolean enabled,
                         @Value("${app.dev-admin.username:admin}") String adminUsername,
                         @Value("${app.dev-admin.password:}") String adminPassword,
                         @Value("${app.dev-staff.username:staff}") String staffUsername,
                         @Value("${app.dev-staff.password:}") String staffPassword) {
        this.users=users; this.bookings=bookings; this.categories=categories; this.products=products;
        this.variants=variants; this.fulfillment=fulfillment; this.courts=courts;
        this.prices=prices; this.encoder=encoder; this.enabled=enabled;
        this.adminUsername=adminUsername; this.adminPassword=adminPassword;
        this.staffUsername=staffUsername; this.staffPassword=staffPassword;
    }

    @Override public void run(String... args) {
        if (!enabled) return;
        if (!adminPassword.isBlank() && users.findByUsername(adminUsername).isEmpty())
            users.save(new UserAccount(adminUsername, encoder.encode(adminPassword), "ADMIN"));
        if (!staffPassword.isBlank() && users.findByUsername(staffUsername).isEmpty())
            users.save(new UserAccount(staffUsername, encoder.encode(staffPassword), "STAFF"));
        if (categories.count() == 0) seedCategories();
        seedProductCatalog();
        if (courts.count() == 0) seedCourts();
        seedFakerBookings();
    }

    private void seedCategories() {
        categories.saveAll(List.of(
                new Category("Vợt pickleball", "vot", 1),
                new Category("Trang phục", "trang-phuc", 2),
                new Category("Bóng và phụ kiện", "phu-kien", 3),
                new Category("Nước uống", "nuoc-uong", 4)));
    }

    private void seedProductCatalog() {
        List<Category> availableCategories = categories.findByActiveTrueOrderBySortOrderAsc();
        List<SeedProduct> catalog = List.of(
                new SeedProduct("vot", "Vợt Carbon Pro 16mm", "Mặt carbon nhẹ, cân bằng lực và độ kiểm soát cho những trận đấu thường xuyên.", "PZ-001", "🏓", 1_950_000, 24, "1753901821774-22a88913130f"),
                new SeedProduct("vot", "Vợt Control Lite", "Thiết kế nhẹ, dễ điều khiển và phù hợp với người chơi ưu tiên độ chính xác.", "PZ-002", "🏓", 1_250_000, 30, "1769911112109-8ce1e4f75e19"),
                new SeedProduct("vot", "Vợt Starter Set", "Bộ vợt kèm bóng và túi đựng, phù hợp cho người mới bắt đầu chơi pickleball.", "PZ-003", "🏓", 890_000, 18, "1769911112258-47da2e8eee67"),
                new SeedProduct("trang-phuc", "Áo thi đấu DryFit", "Áo thể thao vải thoáng khí, nhanh khô khi vận động trên sân.", "PZ-004", "👕", 350_000, 40, "1757334489278-f42e7e028872"),
                new SeedProduct("trang-phuc", "Quần thể thao Flex", "Quần thể thao co giãn linh hoạt, thoải mái khi di chuyển và đổi hướng.", "PZ-005", "👕", 300_000, 36, "1762744826900-ff6cdf807d79"),
                new SeedProduct("phu-kien", "Bóng ngoài trời hộp 6", "Bóng pickleball ngoài trời có lỗ, độ nảy ổn định và dễ quan sát trên sân.", "PZ-006", "🎾", 180_000, 60, "1686721135016-49949bd23352"),
                new SeedProduct("phu-kien", "Quấn cán chống trượt", "Lớp quấn cán tăng độ bám, giúp cầm vợt chắc tay khi ra mồ hôi.", "PZ-007", "🎾", 80_000, 48, "1686721135029-3e3367daeab9"),
                new SeedProduct("nuoc-uong", "Nước suối 500ml", "Nước uống đóng chai tiện mang theo để bổ sung nước trong buổi chơi.", "PZ-008", "🥤", 15_000, 100, "1580955027067-8ef70ef0c1cf"),
                new SeedProduct("nuoc-uong", "Nước điện giải 500ml", "Thức uống điện giải giúp bù nước sau khi vận động.", "PZ-009", "🥤", 25_000, 80, "1550505095-81378a674395"),
                new SeedProduct("nuoc-uong", "Trà chanh mật ong", "Trà chanh mát lạnh với hương chanh mật ong, phù hợp dùng sau trận đấu.", "PZ-010", "🥤", 20_000, 60, "1676159435366-ed3c8cdd3567"));
        Map<String, Category> categoryBySlug = availableCategories.stream()
                .collect(java.util.stream.Collectors.toMap(Category::getSlug, category -> category));
        for (long index = products.count(); index < catalog.size(); index++) {
            SeedProduct seed = catalog.get((int) index);
            Category category = categoryBySlug.get(seed.categorySlug());
            if (category == null) continue;
            Product product = new Product(category, seed.name(), "PickleZone", seed.description(), seed.emoji());
            product.setImageUrl("https://images.unsplash.com/photo-" + seed.photoId()
                    + "?auto=format&fit=crop&w=900&q=85");
            product = products.save(product);
            variants.save(new ProductVariant(product, seed.sku(), "Mặc định",
                    BigDecimal.valueOf(seed.price()), seed.stock()));
            fulfillment.save(new ProductFulfillmentOption(product, "STORE_PICKUP"));
            fulfillment.save(new ProductFulfillmentOption(product, "COURT_DELIVERY"));
            fulfillment.save(new ProductFulfillmentOption(product, "HOME_DELIVERY"));
        }
    }

    private record SeedProduct(String categorySlug, String name, String description, String sku,
                               String emoji, int price, int stock, String photoId) {}

    private void seedCourts() {
        for (int number=1; number<=6; number++) {
            Court court = courts.save(new Court(String.format("S%02d",number),
                    "Sân " + number,"Acrylic",number<=2,"Sân pickleball tiêu chuẩn"));
            for (int day=1; day<=7; day++)
                prices.save(new CourtPriceRule(court,day,LocalTime.of(6,0),LocalTime.of(23,0),
                        BigDecimal.valueOf(day >= 6 ? 150000 : 120000),LocalDate.now().minusDays(1),null));
        }
    }

    private void seedFakerBookings() {
        Faker faker = new Faker(new Locale("vi"));
        List<Court> all = courts.findAll();
        if (all.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long existingFakerBookings = bookings.countByBookingCodeStartingWith("FAKER");
        for (long index = existingFakerBookings; index < 6; index++) {
            Court court = all.get((int) (index % all.size()));
            LocalDate playDate = LocalDate.now().plusDays(10 + index);
            int startHour = faker.number().numberBetween(7, 21);
            LocalDateTime start = playDate.atTime(startHour, 0).minusHours(7);
            BigDecimal total = BigDecimal.valueOf(faker.number().numberBetween(180_000, 501_000));
            String code = "FAKER" + faker.number().digits(10);
            bookings.save(new Booking(code, court, faker.name().fullName(),
                    "09" + faker.number().digits(8), faker.internet().emailAddress(), faker.lorem().sentence(),
                    start, start.plusHours(2), total,
                    total.multiply(BigDecimal.valueOf(0.3)),
                    "Faker-generated test price", "hold=30m;arrival=30m;minimum=120m",
                    now.plusMinutes(30), 120, 30));
        }
    }
}
