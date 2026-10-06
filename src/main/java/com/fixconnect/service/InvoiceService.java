package com.fixconnect.service;

import com.fixconnect.common.ApiException;
import com.fixconnect.domain.Invoice;
import com.fixconnect.domain.ServiceRequest;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import com.fixconnect.repository.InvoiceRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Shared invoice access (customer or provider of the invoice) and PDF rendering. */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final DtoMapper mapper;

    public InvoiceService(InvoiceRepository invoices, DtoMapper mapper) {
        this.invoices = invoices;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long userId, Long invoiceId) {
        return mapper.invoice(owned(userId, invoiceId));
    }

    @Transactional(readOnly = true)
    public PdfFile pdf(Long userId, Long invoiceId) {
        Invoice inv = owned(userId, invoiceId);
        return new PdfFile(inv.getInvoiceNo() + ".pdf", render(inv));
    }

    public record PdfFile(String fileName, byte[] content) {
    }

    private Invoice owned(Long userId, Long invoiceId) {
        Invoice inv = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        if (!inv.getCustomer().getId().equals(userId) && !inv.getProvider().getId().equals(userId)) {
            throw ApiException.notFound("Invoice");
        }
        return inv;
    }

    private byte[] render(Invoice inv) {
        try {
            return renderUnchecked(inv);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate invoice PDF", e);
        }
    }

    private byte[] renderUnchecked(Invoice inv) throws Exception {
        ServiceRequest r = inv.getRequest();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(29, 78, 216));
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font n = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

        doc.add(new Paragraph("FixConnect AI", brand));
        doc.add(new Paragraph("Smart Solutions. Trusted Technicians. | " + EmailService.SUPPORT_EMAIL + " | Hyderabad, India", small));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("TAX INVOICE  #" + inv.getInvoiceNo(), h));
        doc.add(new Paragraph("Issued: " + inv.getIssuedAt().format(df), n));
        doc.add(new Paragraph("Status: " + inv.getStatus()
                + (inv.getPaidAt() != null ? " (" + inv.getPaymentMethod() + ", " + inv.getPaidAt().format(df) + ")" : ""), n));
        doc.add(Chunk.NEWLINE);

        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.addCell(cell("Billed to\n" + inv.getCustomer().getFullName() + "\n" + nz(inv.getCustomer().getPhone())
                + "\n" + r.getAddress(), n));
        parties.addCell(cell("Technician\n" + inv.getProvider().getFullName() + "\n" + nz(inv.getProvider().getPhone())
                + "\nBooking #" + nz(r.getBookingNo()), n));
        doc.add(parties);
        doc.add(Chunk.NEWLINE);

        PdfPTable items = new PdfPTable(new float[]{4, 1.4f});
        items.setWidthPercentage(100);
        items.addCell(cell("Description", h));
        items.addCell(cell("Amount (Rs.)", h));
        items.addCell(cell("Labour - " + DtoMapper.titleOf(r), n));
        items.addCell(cell(money(inv.getLaborCost()), n));
        items.addCell(cell("Parts" + (inv.getPartsDescription() != null ? " - " + inv.getPartsDescription() : ""), n));
        items.addCell(cell(money(inv.getPartsCost()), n));
        if (inv.getDiscount().signum() > 0) {
            items.addCell(cell("Discount" + (inv.getAppliedCoupon() != null ? " (" + inv.getAppliedCoupon() + ")" : ""), n));
            items.addCell(cell("- " + money(inv.getDiscount()), n));
        }
        items.addCell(cell("TOTAL", h));
        items.addCell(cell(money(inv.getTotal()), h));
        doc.add(items);
        doc.add(Chunk.NEWLINE);
        if (inv.getNotes() != null) {
            doc.add(new Paragraph("Notes: " + inv.getNotes(), n));
        }
        doc.add(new Paragraph("All completed work is covered by the FixConnect 30-day service warranty.", small));
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(6);
        return c;
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
