package com.fixconnect.service;

import com.fixconnect.domain.ContactMessage;
import com.fixconnect.domain.EmergencyType;
import com.fixconnect.dto.CommonDtos.*;
import com.fixconnect.repository.ContactMessageRepository;
import com.fixconnect.repository.ServiceCategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class CatalogService {

    private final ServiceCategoryRepository categories;
    private final ContactMessageRepository contacts;
    private final DtoMapper mapper;
    private final Lookup lookup;
    private final String hotline;
    private final int radiusKm;

    public CatalogService(ServiceCategoryRepository categories, ContactMessageRepository contacts, DtoMapper mapper,
                          Lookup lookup, @Value("${fixconnect.emergency.hotline}") String hotline,
                          @Value("${fixconnect.emergency.radius-km:5}") int radiusKm) {
        this.categories = categories;
        this.contacts = contacts;
        this.mapper = mapper;
        this.lookup = lookup;
        this.hotline = hotline;
        this.radiusKm = radiusKm;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderByIdAsc().stream().map(mapper::category).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse category(String code) {
        return mapper.category(lookup.category(code));
    }

    public EmergencyInfoResponse emergencyInfo() {
        List<EmergencyCategoryResponse> list = Arrays.stream(EmergencyType.values())
                .map(t -> new EmergencyCategoryResponse(t, t.getLabel(), t.getCategoryCode()))
                .toList();
        return new EmergencyInfoResponse(hotline, radiusKm,
                "Priority dispatch to verified technicians within " + radiusKm + " km - target arrival under 30 minutes.",
                list);
    }

    @Transactional
    public ContactResponse submitContact(ContactRequest req) {
        ContactMessage m = new ContactMessage();
        m.setName(req.name().trim());
        m.setEmail(req.email().trim());
        m.setPhone(req.phone());
        m.setService(req.service());
        m.setSubject(req.subject());
        m.setDetails(req.details());
        contacts.save(m);
        return new ContactResponse(true,
                "Thank you! Your message has been submitted successfully. Our support team will reach out to you shortly.",
                m.getId());
    }
}
