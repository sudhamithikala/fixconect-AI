package com.fixconnect.service;

import com.fixconnect.domain.ServiceCategory;
import com.fixconnect.domain.Severity;
import com.fixconnect.dto.AiDtos.DiagnosisResponse;
import com.fixconnect.dto.AiDtos.RepairGuideResponse;
import com.fixconnect.repository.ServiceCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "FixConnect AI" problem analysis. A transparent keyword / rule engine that predicts the root cause,
 * category, severity, specialist and cost range. It can later be swapped for an LLM call without
 * changing the API contract.
 */
@Service
public class AiService {

    private record Rule(String category, List<String> keywords, String issue, Severity severity, String specialist,
                        int costMin, int costMax, List<String> tools, List<String> steps, int minutes) {
    }

    private static final List<String> CRITICAL_WORDS = List.of("spark", "sparking", "smoke", "burning smell", "fire",
            "shock", "gas leak", "gas smell", "burst", "flood", "flooding", "short circuit", "electrocut");

    private static final List<Rule> RULES = List.of(
            new Rule("AC_REPAIR", List.of("leak", "water", "drip", "vent", "lukewarm", "tray"),
                    "Condensate drain line blockage overflowing internal tray.", Severity.MODERATE,
                    "Certified AC Technician", 650, 1200,
                    List.of("Wet/dry vacuum", "Drain line brush", "Nitrogen flush kit", "Spirit level"),
                    List.of("Switch off the unit and inspect the drain tray", "Flush the condensate drain line",
                            "Check indoor unit level / slope", "Clean filters and evaporator fins", "Run test for 15 minutes"), 60),
            new Rule("AC_REPAIR", List.of("not cooling", "warm air", "gas", "refill", "compressor", "e4", "error", "turns off", "trip"),
                    "Low refrigerant charge or compressor thermal overload.", Severity.HIGH,
                    "Certified AC Technician", 1200, 3500,
                    List.of("Digital manifold gauge", "Clamp meter", "Capacitor 45uF (spare)", "Leak detector", "Refrigerant cylinder"),
                    List.of("Read error code and check outdoor fan operation", "Measure compressor amps with clamp meter",
                            "Test run capacitor (45uF)", "Check gas pressure with manifold gauge",
                            "Clean outdoor condenser coil", "Leak test before recharging gas"), 90),
            new Rule("AC_REPAIR", List.of("ac", "air conditioner", "split", "noise", "smell", "service"),
                    "AC requires deep servicing (dirty filters / coils).", Severity.LOW,
                    "AC Technician", 499, 900,
                    List.of("Jet pump", "Coil cleaner", "Fin comb", "Screwdriver set"),
                    List.of("Remove and wash filters", "Jet-clean indoor coil", "Clean blower", "Clean outdoor unit", "Check cooling delta-T"), 60),
            new Rule("PLUMBING", List.of("leak", "pipe", "sink", "tap", "drip", "joint", "cabinet"),
                    "Loose or corroded pipe joint causing leakage.", Severity.MODERATE,
                    "Plumbing Specialist", 350, 900,
                    List.of("Pipe wrench", "Teflon tape", "Replacement coupling", "Sealant"),
                    List.of("Shut the water supply", "Locate leaking joint", "Replace washer / coupling", "Re-seal and pressure test"), 45),
            new Rule("PLUMBING", List.of("block", "clog", "drain", "overflow", "slow", "toilet", "flush"),
                    "Drain blockage due to grease / debris build-up.", Severity.MODERATE,
                    "Plumbing Specialist", 300, 800,
                    List.of("Drain snake / auger", "Plunger", "Drain cleaner", "Gloves"),
                    List.of("Identify blocked section", "Use plunger / auger", "Flush with hot water", "Check trap and vent"), 45),
            new Rule("PLUMBING", List.of("burst", "flood", "main pipe", "motor", "tank", "no water"),
                    "Pipe burst / supply line failure.", Severity.CRITICAL,
                    "Emergency Plumber", 800, 2500,
                    List.of("Pipe cutter", "Repair clamp", "PVC/CPVC fittings", "Solvent cement"),
                    List.of("Isolate main valve immediately", "Cut out damaged section", "Install repair coupling", "Restore supply and check"), 90),
            new Rule("ELECTRICAL", List.of("switch", "socket", "board", "plug", "light", "fan", "regulator"),
                    "Faulty switchboard component or loose wiring connection.", Severity.MODERATE,
                    "Licensed Electrician", 250, 700,
                    List.of("Multimeter", "Line tester", "Insulated screwdrivers", "Spare switches / sockets"),
                    List.of("Isolate the circuit at MCB", "Test with line tester", "Tighten / replace terminal", "Restore power and verify"), 40),
            new Rule("ELECTRICAL", List.of("trip", "mcb", "short", "spark", "power cut", "fuse", "wiring", "shock"),
                    "Short circuit or overloaded circuit tripping the MCB.", Severity.HIGH,
                    "Master Electrician", 500, 1500,
                    List.of("Insulation tester (megger)", "Multimeter", "Clamp meter", "Spare MCB / RCCB"),
                    List.of("Isolate main supply", "Identify tripping circuit", "Insulation-resistance test", "Replace damaged wire / MCB", "Load test"), 75),
            new Rule("APPLIANCE_REPAIR", List.of("fridge", "refrigerator", "freez", "cooling", "ice", "buzz"),
                    "Refrigerator compressor relay or thermostat fault.", Severity.HIGH,
                    "Appliance Technician", 800, 2500,
                    List.of("Multimeter", "Start relay (spare)", "Thermostat (spare)", "Gas charging kit"),
                    List.of("Check thermostat setting", "Test start relay and overload", "Check condenser fan", "Check gas pressure"), 75),
            new Rule("APPLIANCE_REPAIR", List.of("washing", "washer", "spin", "drum", "drain", "noise", "vibrat"),
                    "Washing machine drum bearing / belt wear or drain pump blockage.", Severity.MODERATE,
                    "Appliance Technician", 500, 1800,
                    List.of("Socket set", "Bearing puller", "Spare belt", "Multimeter"),
                    List.of("Run diagnostic cycle", "Inspect belt and pulley", "Clean drain pump filter", "Check drum bearings"), 60),
            new Rule("APPLIANCE_REPAIR", List.of("microwave", "oven", "geyser", "heater", "mixer", "chimney", "ro", "purifier"),
                    "Heating element / control board fault in appliance.", Severity.MODERATE,
                    "Appliance Technician", 400, 1500,
                    List.of("Multimeter", "Insulated tools", "Spare fuse / element"),
                    List.of("Check power supply and fuse", "Test heating element continuity", "Inspect control board"), 45),
            new Rule("CARPENTRY", List.of("door", "lock", "hinge", "drawer", "cupboard", "wardrobe", "furniture", "jam", "key"),
                    "Misaligned hinge / damaged lock mechanism.", Severity.LOW,
                    "Carpenter", 300, 900,
                    List.of("Drill machine", "Chisel set", "Spare hinges / lock", "Screws & wall plugs"),
                    List.of("Inspect alignment", "Tighten / replace hinges", "Repair or replace lock", "Lubricate and test"), 45),
            new Rule("PAINTING", List.of("paint", "wall", "damp", "peel", "crack", "seepage", "colour", "color"),
                    "Wall dampness / paint peeling - needs putty, primer and repaint.", Severity.LOW,
                    "Painter", 1500, 6000,
                    List.of("Scraper", "Putty knife", "Waterproof primer", "Roller & brushes"),
                    List.of("Identify seepage source", "Scrape loose paint", "Apply waterproof putty & primer", "Two coats of paint"), 240),
            new Rule("CLEANING", List.of("clean", "dust", "kitchen", "bathroom", "sofa", "deep", "pest", "stain"),
                    "Deep cleaning required for the selected area.", Severity.LOW,
                    "Cleaning Professional", 999, 3500,
                    List.of("Scrubbing machine", "Vacuum cleaner", "Eco-friendly chemicals"),
                    List.of("Survey area", "Dry dusting / vacuum", "Wet scrubbing & sanitising", "Final inspection"), 180)
    );

    private final ServiceCategoryRepository categories;

    public AiService(ServiceCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse diagnose(String description, String categoryHint) {
        String text = description.toLowerCase(Locale.ROOT);
        Match m = bestMatch(text, categoryHint);
        boolean critical = CRITICAL_WORDS.stream().anyMatch(text::contains);

        if (m.rule == null) {
            String code = categoryHint != null && !categoryHint.isBlank() ? categoryHint.toUpperCase() : "GENERAL";
            ServiceCategory c = categories.findByCodeIgnoreCase(code)
                    .orElseGet(() -> categories.findByCodeIgnoreCase("GENERAL").orElse(null));
            BigDecimal min = c != null ? c.getTypicalCostMin() : BigDecimal.valueOf(300);
            BigDecimal max = c != null ? c.getTypicalCostMax() : BigDecimal.valueOf(1500);
            return new DiagnosisResponse("Unable to pinpoint the exact fault from the description.",
                    "A technician inspection is recommended. Add more detail (appliance, symptoms, error codes) for a better prediction.",
                    c != null ? c.getCode() : code, c != null ? c.getName() : "General Repair",
                    critical ? Severity.CRITICAL : Severity.MODERATE,
                    c != null ? c.getSpecialistTitle() : "Multi-skill Technician", min, max, costLabel(min, max), 55.0,
                    critical, tips(critical));
        }

        Rule r = m.rule;
        Severity severity = critical ? Severity.CRITICAL : r.severity();
        ServiceCategory c = categories.findByCodeIgnoreCase(r.category()).orElse(null);
        double confidence = Math.min(97.5, 62 + m.score * 11.3);
        BigDecimal min = BigDecimal.valueOf(r.costMin());
        BigDecimal max = BigDecimal.valueOf(r.costMax());
        String shortText = description.length() > 45 ? description.substring(0, 45) + "..." : description;
        return new DiagnosisResponse(r.issue(),
                "Analyzed \"" + shortText + "\". Most likely cause: " + r.issue(),
                r.category(), c != null ? c.getName() : r.category(), severity, r.specialist(), min, max,
                costLabel(min, max), Math.round(confidence * 10) / 10.0,
                severity == Severity.CRITICAL, tips(severity == Severity.CRITICAL));
    }

    @Transactional(readOnly = true)
    public RepairGuideResponse repairGuide(String description, String categoryHint) {
        String text = description.toLowerCase(Locale.ROOT);
        Match m = bestMatch(text, categoryHint);
        List<String> safety = new ArrayList<>(List.of("Isolate power / water supply before starting work",
                "Wear insulated gloves and safety glasses", "Take BEFORE photos and upload them to the job"));
        if (CRITICAL_WORDS.stream().anyMatch(text::contains)) {
            safety.add(0, "Hazard keywords detected - secure the area and keep occupants away");
        }
        if (m.rule == null) {
            return new RepairGuideResponse("Insufficient detail - perform a general inspection.",
                    categoryHint, List.of("Multimeter", "Basic tool kit", "Torch"),
                    List.of("Talk to the customer and reproduce the issue", "Visually inspect components",
                            "Measure supply voltage / pressure", "Quote the customer before replacing parts"),
                    safety, 60, 50.0);
        }
        Rule r = m.rule;
        return new RepairGuideResponse(r.issue(), r.category(), r.tools(), r.steps(), safety, r.minutes(),
                Math.round(Math.min(97.5, 62 + m.score * 11.3) * 10) / 10.0);
    }

    // ---------------------------------------------------------------------------------------------

    private record Match(Rule rule, int score) {
    }

    private static Match bestMatch(String text, String categoryHint) {
        String hint = categoryHint == null ? null : categoryHint.trim().toUpperCase();
        return RULES.stream()
                .map(r -> {
                    int score = (int) r.keywords().stream().filter(text::contains).count();
                    if (score > 0 && hint != null && hint.equals(r.category())) {
                        score += 1; // the customer's chosen category is a strong signal
                    }
                    return new Match(r, score);
                })
                .filter(x -> x.score() > 0)
                .max(Comparator.comparingInt(Match::score))
                .orElse(new Match(null, 0));
    }

    private static List<String> tips(boolean critical) {
        if (critical) {
            return List.of("Switch off the main power / water valve if safe to do so",
                    "Keep children and pets away from the affected area",
                    "Use Emergency SOS for priority dispatch (target under 30 minutes)");
        }
        return List.of("Avoid using the appliance / fixture until inspected",
                "Upload a photo with your request for faster diagnosis",
                "Book a verified technician - all work carries a 30-day warranty");
    }

    private static String costLabel(BigDecimal min, BigDecimal max) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"));
        return "₹" + nf.format(min) + " - ₹" + nf.format(max);
    }
}
