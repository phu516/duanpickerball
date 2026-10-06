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
import java.util.UUID;
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
        seedFakerCatalog();
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

    private void seedFakerCatalog() {
        Faker faker = new Faker(new Locale("vi"));
        List<Category> availableCategories = categories.findByActiveTrueOrderBySortOrderAsc();
        long currentProducts = products.count();
        for (long index = currentProducts; index < 20; index++) {
            Category category = availableCategories.get(faker.number().numberBetween(0, availableCategories.size()));
            String slug = category.getSlug();
            String emoji = switch (slug) {
                case "vot" -> "🏓";
                case "trang-phuc" -> "👕";
                case "nuoc-uong" -> "🥤";
                default -> "🎾";
            };
            Product product = products.save(new Product(category,
                    category.getName() + " " + faker.commerce().productName(),
                    faker.company().name(), faker.lorem().sentence(), emoji));
            int minimumPrice = slug.equals("nuoc-uong") ? 10_000 : 80_000;
            int maximumPrice = slug.equals("vot") ? 2_500_000 : slug.equals("nuoc-uong") ? 80_000 : 800_000;
            int stock = faker.number().numberBetween(8, 121);
            variants.save(new ProductVariant(product, "FAKER-" + UUID.randomUUID().toString().substring(0, 12),
                    "Mặc định", BigDecimal.valueOf(faker.number().numberBetween(minimumPrice, maximumPrice + 1)), stock));
            fulfillment.save(new ProductFulfillmentOption(product, "STORE_PICKUP"));
            fulfillment.save(new ProductFulfillmentOption(product, "COURT_DELIVERY"));
            if (!slug.equals("nuoc-uong"))
                fulfillment.save(new ProductFulfillmentOption(product, "HOME_DELIVERY"));
        }
    }

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
