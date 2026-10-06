package com.fixconnect.controller;

import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.FileStorageService;
import com.fixconnect.service.InvoiceService;
import com.fixconnect.service.InvoiceService.PdfFile;
import com.fixconnect.dto.CustomerDtos.InvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "13. Account & Files")
public class FileController {

    private final FileStorageService files;
    private final InvoiceService invoices;
    private final CurrentUser currentUser;

    public FileController(FileStorageService files, InvoiceService invoices, CurrentUser currentUser) {
        this.files = files;
        this.invoices = invoices;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/files/{name:.+}")
    @SecurityRequirements
    @Operation(summary = "Download an uploaded photo / document")
    public ResponseEntity<Resource> file(@PathVariable String name) {
        Resource r = files.load(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(files.contentType(name)))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(r);
    }

    @GetMapping("/api/invoices/{id}")
    @Operation(summary = "Get an invoice (customer or technician of the invoice)")
    public InvoiceResponse invoice(@PathVariable Long id) {
        return invoices.get(currentUser.id(), id);
    }

    @GetMapping(value = "/api/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download invoice PDF")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        PdfFile pdf = invoices.pdf(currentUser.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
