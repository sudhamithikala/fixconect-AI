package com.fixconnect.config;

import com.fixconnect.domain.*;
import com.fixconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds service categories and reward vouchers, plus (optionally) the demo customers, technicians and bookings
 * that appear in the static HTML dashboards (Naga Sudha, Rahul Kumar, Arjun Reddy, Vijay Kumar ...).
 * Demo logins (same as the original Express server): customer@gmail.com / 12345 and provider@gmail.com / 12345.
 */
@Component
@Order(2) // after DatabaseMigrations
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "12345";

    private final ServiceCategoryRepository categories;
    private final RewardVoucherRepository vouchers;
    private final UserRepository users;
    private final ProviderProfileRepository profiles;
    private final AddressRepository addresses;
    private final ProviderServiceOfferingRepository offerings;
    private final ServiceRequestRepository requests;
    private final StatusEventRepository events;
    private final InvoiceRepository invoices;
    private final WarrantyRepository warranties;
    private final ReviewRepository reviews;
    private final FavouriteRepository favourites;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final boolean seedDemo;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public DataSeeder(ServiceCategoryRepository categories, RewardVoucherRepository vouchers, UserRepository users,
                      ProviderProfileRepository profiles, AddressRepository addresses,
                      ProviderServiceOfferingRepository offerings, ServiceRequestRepository requests,
                      StatusEventRepository events, InvoiceRepository invoices, WarrantyRepository warranties,
                      ReviewRepository reviews, FavouriteRepository favourites, NotificationRepository notifications,
                      PasswordEncoder encoder, @Value("${fixconnect.seed-demo-data:false}") boolean seedDemo,
                      @Value("${fixconnect.admin.email:adminfixconnectai@gmail.com}") String adminEmail,
                      @Value("${fixconnect.admin.password:Admin@123}") String adminPassword,
                      @Value("${fixconnect.admin.name:FixConnect Admin}") String adminName) {
        this.categories = categories;
        this.vouchers = vouchers;
        this.users = users;
        this.profiles = profiles;
        this.addresses = addresses;
        this.offerings = offerings;
        this.requests = requests;
        this.events = events;
        this.invoices = invoices;
        this.warranties = warranties;
        this.reviews = reviews;
        this.favourites = favourites;
        this.notifications = notifications;
        this.encoder = encoder;
        this.seedDemo = seedDemo;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() == 0) {
            seedCategories();
        }
        if (vouchers.count() == 0) {
            seedVouchers();
        }
        boolean emptyDb = users.count() == 0;
        seedAdmin();
        if (seedDemo && emptyDb) {
            seedDemo();
            log.info("Demo data loaded. Logins: customer@gmail.com / 12345 (CUSTOMER), provider@gmail.com / 12345 (PROVIDER)");
        }
    }

    /** Creates the admin account once (works on existing databases too). Change the password after first login. */
    private void seedAdmin() {
        if (!users.findByRole(Role.ADMIN).isEmpty()) {
            return;
        }
        if (users.existsByEmailIgnoreCase(adminEmail)) {
            log.warn("Cannot seed admin: e-mail {} is already used by another account", adminEmail);
            return;
        }
        User admin = new User();
        admin.setFullName(adminName);
        admin.setEmail(adminEmail.toLowerCase());
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(encoder.encode(adminPassword));
        users.save(admin);
        log.info("Admin account created: {} (login at /admin-login.html)", adminEmail);
    }

    private void seedCategories() {
        categories.saveAll(List.of(
                new ServiceCategory("AC_REPAIR", "AC Repair & Maintenance",
                        "AC servicing, cooling problems, gas charging, installation support and maintenance.",
                        "AC Technician", "fa-snowflake", 499, 650, 3500),
                new ServiceCategory("PLUMBING", "Plumbing Services",
                        "Pipe leakage, tap repair, sink blockage, bathroom plumbing and water-related repairs.",
                        "Plumber", "fa-faucet-drip", 299, 300, 2500),
                new ServiceCategory("ELECTRICAL", "Electrical Repair",
                        "Wiring issues, switchboard repairs, inspections and safe electrical services.",
                        "Electrician", "fa-bolt", 249, 250, 1500),
                new ServiceCategory("APPLIANCE_REPAIR", "Appliance Repair",
                        "Washing machine, refrigerator, microwave and household appliance repairs.",
                        "Appliance Technician", "fa-blender", 399, 400, 2500),
                new ServiceCategory("CLEANING", "Home Deep Cleaning",
                        "Home cleaning, kitchen cleaning, bathroom cleaning and regular maintenance.",
                        "Cleaning Professional", "fa-broom", 999, 999, 3500),
                new ServiceCategory("CARPENTRY", "Carpentry & Furniture",
                        "Furniture repair, door repair, shelves, fittings and carpentry work.",
                        "Carpenter", "fa-hammer", 299, 300, 1500),
                new ServiceCategory("PAINTING", "House Painting",
                        "Interior and exterior painting, damp proofing and touch-ups.",
                        "Painter", "fa-paint-roller", 999, 1500, 6000),
                new ServiceCategory("GENERAL", "General Inquiry / Other",
                        "Handyman services and anything not listed above.",
                        "Multi-skill Technician", "fa-screwdriver-wrench", 299, 300, 1500)));
    }

    private void seedVouchers() {
        vouchers.saveAll(List.of(
                voucher("₹100 OFF any service", "Flat ₹100 off on your next booking.", 100, 100, null),
                voucher("₹200 OFF electrical service", "Get ₹200 OFF on your next electrical service.", 200, 200, "ELECTRICAL"),
                voucher("₹500 OFF home deep cleaning", "₹500 off a full home deep cleaning.", 450, 500, "CLEANING")));
    }

    private static RewardVoucher voucher(String title, String desc, int points, int amount, String category) {
        RewardVoucher v = new RewardVoucher();
        v.setTitle(title);
        v.setDescription(desc);
        v.setPointsCost(points);
        v.setDiscountAmount(BigDecimal.valueOf(amount));
        v.setCategoryCode(category);
        return v;
    }

    // ---------------------------------------------------------------------------------------------

    private void seedDemo() {
        LocalDate today = LocalDate.now();

        // Customers
        User naga = user("Naga Sudha", "customer@gmail.com", "+91 98765 43210", Role.CUSTOMER);
        naga.setLoyaltyPoints(240);
        naga.setLifetimePoints(240);
        Address home = address(naga, "Home", "Plot 42, Jubilee Hills, Road No. 10", "500033", 17.4326, 78.4071, true);
        address(naga, "Office", "Flat 302, Green View Apartments, Madhapur", "500081", 17.4483, 78.3915, false);

        User priya = user("Priya S.", "priya@example.com", "+91 90000 22222", Role.CUSTOMER);
        Address priyaHome = address(priya, "Home", "Road No. 3, Banjara Hills", "500034", 17.4156, 78.4347, true);
        User kiran = user("Kiran M.", "kiran@example.com", "+91 90000 33333", Role.CUSTOMER);
        Address kiranHome = address(kiran, "Home", "Hitech City Road, Madhapur", "500081", 17.4474, 78.3762, true);

        // Technicians
        ProviderProfile rahul = provider("Rahul Kumar", "provider@gmail.com", "+91 90000 11111", "AC_REPAIR",
                "AC & Home Appliance Specialist", "Split AC, Window AC, Gas charging, Leak detection, Washing machines, Refrigerators",
                7, "Jubilee Hills, Banjara Hills & Madhapur", 4.9, 126, 17.4239, 78.4220);
        rahul.setAcceptedCount(92);
        rahul.setDeclinedCount(6);
        rahul.setBankAccountHolder("Rahul Kumar");
        rahul.setBankAccountNumber("123456789012");
        rahul.setBankIfsc("HDFC0001234");
        ProviderProfile arjun = provider("Arjun Reddy", "arjun@fixconnect.ai", "+91 90000 44444", "ELECTRICAL",
                "Licensed Electrician", "Wiring, Panel audits, MCB/RCCB, Inverter installation", 8,
                "Jubilee Hills & Film Nagar", 4.8, 94, 17.4400, 78.4120);
        ProviderProfile vijay = provider("Vijay Kumar", "vijay@fixconnect.ai", "+91 90000 55555", "PLUMBING",
                "Plumbing Specialist", "Leak repair, Drain cleaning, Bathroom fittings, Motor pumps", 10,
                "Jubilee Hills, Madhapur & Kondapur", 4.9, 112, 17.4480, 78.3900);
        ProviderProfile ravi = provider("Ravi Teja", "ravi@fixconnect.ai", "+91 90000 66666", "APPLIANCE_REPAIR",
                "Appliance Technician", "Refrigerators, Washing machines, Microwave ovens, RO purifiers", 6,
                "Banjara Hills & Ameerpet", 4.7, 88, 17.4300, 78.4400);
        ProviderProfile kiranK = provider("Kiran Kumar", "kirankumar@fixconnect.ai", "+91 90000 77777", "CARPENTRY",
                "Carpenter", "Furniture repair, Door locks, Modular kitchen fittings", 9,
                "Madhapur & Gachibowli", 4.9, 105, 17.4400, 78.3700);
        ProviderProfile suresh = provider("Suresh Kumar", "suresh@fixconnect.ai", "+91 90000 88888", "PAINTING",
                "Painter", "Interior painting, Texture walls, Waterproofing", 7,
                "Jubilee Hills & Kukatpally", 4.8, 82, 17.4600, 78.4000);
        provider("Lakshmi Devi", "lakshmi@fixconnect.ai", "+91 90000 99999", "CLEANING",
                "Cleaning Professional", "Kitchen deep cleaning, Bathroom sanitisation, Sofa shampooing", 5,
                "Banjara Hills & Jubilee Hills", 4.7, 64, 17.4200, 78.4300);

        // A newly registered technician waiting for admin verification
        ProviderProfile mahesh = provider("Mahesh Goud", "mahesh@fixconnect.ai", "+91 90000 12121", "ELECTRICAL",
                "Electrician", "House wiring, Fan & light installation", 3, "Kukatpally & Miyapur", 0, 0, 17.4900, 78.3900);
        mahesh.setIdVerified(false);
        mahesh.setIdVerifiedAt(null);
        mahesh.setLicenseVerified(false);
        mahesh.setBackgroundCheckCleared(false);
        mahesh.setAvailable(false);
        mahesh.setAcceptedCount(0);
        mahesh.setDeclinedCount(0);

        offering(rahul.getUser(), "AC Servicing & Repair", "Split AC, Window AC deep cleaning, gas charging, leak detection.", 499);
        offering(rahul.getUser(), "Home Appliance Repair", "Washing machines, fridges, microwave ovens repair.", 399);
        offering(arjun.getUser(), "Electrical Inspection & Wiring Audit", "Full home panel and wiring safety audit.", 349);
        offering(vijay.getUser(), "Leak & Pipe Repair", "Kitchen / bathroom leakage and pipe joint repair.", 299);
        offering(ravi.getUser(), "Refrigerator Repair", "Cooling issues, gas charging, relay & thermostat.", 449);
        offering(kiranK.getUser(), "Door & Lock Repair", "Hinges, locks, alignment.", 299);
        offering(suresh.getUser(), "Room Repainting", "Per room with premium emulsion.", 2499);

        // 1) Completed plumbing job (warranty active, 18 days left) - Naga / Vijay
        ServiceRequest pipe = request(naga, vijay.getUser(), "PLUMBING", "Kitchen Pipe Leak Fix",
                "Kitchen sink pipe joint leaking water onto cabinets.", home, today.minusDays(12), TimeSlot.MORNING,
                RequestStatus.COMPLETED, Severity.MODERATE, false);
        pipe.setCompletedAt(today.minusDays(12).atTime(11, 15));
        event(pipe, RequestStatus.PENDING, "Service request submitted", today.minusDays(13).atTime(18, 0));
        event(pipe, RequestStatus.ACCEPTED, "Request accepted by Vijay Kumar", today.minusDays(13).atTime(18, 20));
        event(pipe, RequestStatus.COMPLETED, "Job completed successfully", today.minusDays(12).atTime(11, 15));
        invoice(pipe, 500, 350, "Coupling & sealant", true, today.minusDays(12).atTime(11, 20));
        warranty(pipe, today.minusDays(12));
        review(pipe, 5, "Quick and clean fix. Highly recommended!");

        // 2) Completed AC gas refill months ago - Naga / Rahul
        ServiceRequest gas = request(naga, rahul.getUser(), "AC_REPAIR", "AC Gas Refill",
                "AC not cooling enough, needs gas top-up.", home, today.minusMonths(4), TimeSlot.AFTERNOON,
                RequestStatus.COMPLETED, Severity.HIGH, false);
        gas.setCompletedAt(today.minusMonths(4).atTime(14, 30));
        invoice(gas, 600, 800, "R32 refrigerant", true, today.minusMonths(4).atTime(14, 40));
        warranty(gas, today.minusMonths(4));
        review(gas, 5, "Professional and on time.");

        // 3) Ongoing AC repair, technician en route - Naga / Rahul
        ServiceRequest ac = request(naga, rahul.getUser(), "AC_REPAIR", "AC Repair & Deep Servicing",
                "Indoor split AC blowing warm air, outdoor unit fan stopped.", home, today, TimeSlot.MORNING,
                RequestStatus.EN_ROUTE, Severity.HIGH, false);
        ac.setEtaMinutes(18);
        event(ac, RequestStatus.PENDING, "Service request submitted", today.atTime(9, 50));
        event(ac, RequestStatus.ACCEPTED, "Request accepted by Rahul Kumar", today.atTime(10, 5));
        event(ac, RequestStatus.EN_ROUTE, "Technician is on the way", today.atTime(10, 12));

        // 4) Scheduled electrical inspection - Naga / Arjun
        request(naga, arjun.getUser(), "ELECTRICAL", "Electrical Inspection & Wiring Audit",
                "Full electrical panel audit before festive season.", home, today.plusDays(2), TimeSlot.EVENING,
                RequestStatus.ACCEPTED, Severity.LOW, false);

        // 5) Today's schedule for Rahul - Priya washing machine check (accepted)
        request(priya, rahul.getUser(), "APPLIANCE_REPAIR", "Washing Machine Check",
                "Front-load washing machine shows door lock error.", priyaHome, today, TimeSlot.AFTERNOON,
                RequestStatus.ACCEPTED, Severity.MODERATE, false);

        // 6) Completed AC job for Priya with the dashboard review
        ServiceRequest priyaAc = request(priya, rahul.getUser(), "AC_REPAIR", "AC Service",
                "AC making noise and dripping.", priyaHome, today.minusDays(1), TimeSlot.MORNING,
                RequestStatus.COMPLETED, Severity.MODERATE, false);
        priyaAc.setCompletedAt(today.minusDays(1).atTime(11, 0));
        invoice(priyaAc, 499, 0, null, false, today.minusDays(1).atTime(11, 5));
        warranty(priyaAc, today.minusDays(1));
        review(priyaAc, 5, "Rahul fixed our AC unit in 30 minutes. Excellent work!");

        // 7) Incoming: urgent pool request (AC) and a direct booking to Rahul
        request(priya, null, "AC_REPAIR", "AC Not Cooling & Water Leak",
                "Bedroom AC not cooling and water leaking from the indoor unit.", priyaHome, today, TimeSlot.IMMEDIATE,
                RequestStatus.PENDING, Severity.CRITICAL, true);
        request(kiran, rahul.getUser(), "APPLIANCE_REPAIR", "Washing Machine Spin Cycle Noise",
                "Loud noise during spin cycle, drum vibrating.", kiranHome, today.plusDays(1), TimeSlot.MORNING,
                RequestStatus.PENDING, Severity.MODERATE, false);

        // Favourites & notifications
        Favourite fav = new Favourite();
        fav.setCustomer(naga);
        fav.setProvider(rahul.getUser());
        fav.setNote("Saved from last AC service.");
        favourites.save(fav);

        notify(naga, "Seasonal Maintenance Offer", "Enjoy 15% off full home deep cleaning.", "OFFER", null,
                LocalDateTime.now().minusDays(1));
        notify(naga, "Technician Assigned", "Rahul Kumar accepted your AC request.", "BOOKING", ac.getId(),
                today.atTime(10, 5));
        notify(rahul.getUser(), "New Service Request", "Urgent: AC Not Cooling & Water Leak - Banjara Hills", "REQUEST", null,
                LocalDateTime.now().minusMinutes(15));
        notify(rahul.getUser(), "New direct booking request", "Kiran M. requested Washing Machine Spin Cycle Noise", "REQUEST", null,
                LocalDateTime.now().minusMinutes(40));
    }

    // ---------------------------------------------------------------- helpers

    private User user(String name, String email, String phone, Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setCreatedAt(LocalDateTime.of(2026, 1, 10, 9, 0));
        return users.save(u);
    }

    private Address address(User u, String label, String line1, String pin, double lat, double lng, boolean primary) {
        Address a = new Address();
        a.setUser(u);
        a.setLabel(label);
        a.setLine1(line1);
        a.setCity("Hyderabad");
        a.setState("Telangana");
        a.setPincode(pin);
        a.setLatitude(lat);
        a.setLongitude(lng);
        a.setPrimaryAddress(primary);
        return addresses.save(a);
    }

    private ProviderProfile provider(String name, String email, String phone, String categoryCode, String headline,
                                     String skills, int years, String area, double rating, int reviewsCount,
                                     double lat, double lng) {
        User u = user(name, email, phone, Role.PROVIDER);
        ProviderProfile p = new ProviderProfile();
        p.setUser(u);
        p.setCategory(categories.findByCodeIgnoreCase(categoryCode).orElseThrow());
        p.setHeadline(headline);
        p.setSkills(skills);
        p.setExperienceYears(years);
        p.setServiceArea(area);
        p.setServiceRadiusKm(10);
        p.setMaxDailyJobs(6);
        p.setAvailable(true);
        p.setLatitude(lat);
        p.setLongitude(lng);
        p.setLocationUpdatedAt(LocalDateTime.now());
        p.setIdProofFile(null);
        p.setIdVerified(true);
        p.setIdVerifiedAt(LocalDateTime.of(2026, 1, 10, 12, 0));
        p.setLicenseVerified(true);
        p.setBackgroundCheckCleared(true);
        p.setRating(rating);
        p.setReviewCount(reviewsCount);
        p.setAcceptedCount(reviewsCount);
        p.setDeclinedCount(reviewsCount / 15);
        return profiles.save(p);
    }

    private void offering(User provider, String title, String desc, int rate) {
        ProviderServiceOffering o = new ProviderServiceOffering();
        o.setProvider(provider);
        o.setTitle(title);
        o.setDescription(desc);
        o.setBaseRate(BigDecimal.valueOf(rate));
        offerings.save(o);
    }

    private ServiceRequest request(User customer, User provider, String categoryCode, String title, String description,
                                   Address addr, LocalDate date, TimeSlot slot, RequestStatus status,
                                   Severity severity, boolean emergency) {
        ServiceRequest r = new ServiceRequest();
        r.setCustomer(customer);
        r.setProvider(provider);
        r.setCategory(categories.findByCodeIgnoreCase(categoryCode).orElseThrow());
        r.setTitle(title);
        r.setDescription(description);
        r.setAddress(addr.fullText());
        r.setLatitude(addr.getLatitude());
        r.setLongitude(addr.getLongitude());
        r.setPreferredDate(date);
        r.setTimeSlot(slot);
        r.setStatus(status);
        r.setSeverity(severity);
        r.setEmergency(emergency);
        r.setCreatedAt(status == RequestStatus.PENDING ? LocalDateTime.now().minusMinutes(15) : date.minusDays(1).atTime(18, 0));
        requests.save(r);
        r.setTicketNo("CMP-" + (9000 + r.getId()));
        if (status != RequestStatus.PENDING && provider != null) {
            r.setBookingNo("BK-" + (7700 + r.getId()));
            r.setAcceptedAt(r.getCreatedAt().plusMinutes(20));
        }
        return r;
    }

    private void event(ServiceRequest r, RequestStatus s, String msg, LocalDateTime at) {
        StatusEvent e = new StatusEvent(r, s, msg, r.getEtaMinutes());
        e.setCreatedAt(at);
        events.save(e);
    }

    private void invoice(ServiceRequest r, int labor, int parts, String partsDesc, boolean paid, LocalDateTime at) {
        Invoice i = new Invoice();
        i.setInvoiceNo("INV-" + at.getYear() + "-" + String.format("%04d", r.getId()));
        i.setRequest(r);
        i.setCustomer(r.getCustomer());
        i.setProvider(r.getProvider());
        i.setLaborCost(BigDecimal.valueOf(labor));
        i.setPartsCost(BigDecimal.valueOf(parts));
        i.setPartsDescription(partsDesc);
        i.recalculate();
        i.setIssuedAt(at);
        if (paid) {
            i.setStatus(InvoiceStatus.PAID);
            i.setPaymentMethod(PaymentMethod.UPI);
            i.setPaidAt(at.plusMinutes(10));
        }
        invoices.save(i);
    }

    private void warranty(ServiceRequest r, LocalDate from) {
        Warranty w = new Warranty();
        w.setWarrantyNo("WR-" + (4000 + r.getId()));
        w.setRequest(r);
        w.setCustomer(r.getCustomer());
        w.setProvider(r.getProvider());
        w.setValidFrom(from);
        w.setValidUntil(from.plusDays(30));
        warranties.save(w);
    }

    private void review(ServiceRequest r, int rating, String comment) {
        Review rv = new Review();
        rv.setRequest(r);
        rv.setCustomer(r.getCustomer());
        rv.setProvider(r.getProvider());
        rv.setRating(rating);
        rv.setComment(comment);
        rv.setCreatedAt(r.getCompletedAt() != null ? r.getCompletedAt().plusHours(2) : LocalDateTime.now());
        reviews.save(rv);
    }

    private void notify(User u, String title, String msg, String type, Long ref, LocalDateTime at) {
        Notification n = new Notification();
        n.setUser(u);
        n.setTitle(title);
        n.setMessage(msg);
        n.setType(type);
        n.setReferenceId(ref);
        n.setCreatedAt(at);
        notifications.save(n);
    }
}
