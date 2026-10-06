package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.CustomerDtos.*;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.CustomerBenefitsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@Tag(name = "08. Customer - Favourites, Invoices, Warranty, Rewards")
public class CustomerBenefitsController {

    private final CustomerBenefitsService service;
    private final CurrentUser currentUser;

    public CustomerBenefitsController(CustomerBenefitsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/favourites")
    @Operation(summary = "Favourite technicians")
    public List<FavouriteResponse> favourites() {
        return service.favourites(currentUser.id());
    }

    @PostMapping("/favourites/{technicianId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save technician to favourites")
    public FavouriteResponse addFavourite(@PathVariable Long technicianId, @RequestParam(required = false) String note) {
        return service.addFavourite(currentUser.id(), technicianId, note);
    }

    @DeleteMapping("/favourites/{technicianId}")
    @Operation(summary = "Remove technician from favourites")
    public MessageResponse removeFavourite(@PathVariable Long technicianId) {
        service.removeFavourite(currentUser.id(), technicianId);
        return MessageResponse.ok("Removed from favourites");
    }

    @GetMapping("/invoices")
    @Operation(summary = "Digital invoices (PDF at /api/invoices/{id}/pdf)")
    public List<InvoiceResponse> invoices() {
        return service.invoices(currentUser.id());
    }

    @PostMapping("/invoices/{id}/pay")
    @Operation(summary = "Pay invoice (optionally apply a redeemed voucher code). Earns loyalty points.")
    public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody PayInvoiceRequest req) {
        return service.pay(currentUser.id(), id, req);
    }

    @GetMapping("/warranties")
    @Operation(summary = "Service warranties (30-day coverage)")
    public List<WarrantyResponse> warranties() {
        return service.warranties(currentUser.id());
    }

    @PostMapping("/warranties/{id}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Claim free repair under warranty - creates a follow-up request to the same technician")
    public WarrantyClaimResponse claim(@PathVariable Long id, @Valid @RequestBody WarrantyClaimRequest req) {
        return service.claim(currentUser.id(), id, req);
    }

    @GetMapping("/rewards")
    @Operation(summary = "Loyalty points, tier, vouchers and redemptions")
    public RewardsResponse rewards() {
        return service.rewards(currentUser.id());
    }

    @PostMapping("/rewards/redeem")
    @Operation(summary = "Redeem a voucher with points - returns a coupon code usable when paying an invoice")
    public RedeemResponse redeem(@Valid @RequestBody RedeemRequest req) {
        return service.redeem(currentUser.id(), req);
    }
}
