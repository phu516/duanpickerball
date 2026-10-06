package com.pickleball.court.controller;

import com.pickleball.court.dto.*;
import com.pickleball.court.entity.*;
import com.pickleball.court.service.CourtService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CourtController {
    private final CourtService courts;

    public CourtController(CourtService courts) { this.courts = courts; }

    @GetMapping("/courts")
    public List<CourtResponse> list() { return courts.publicCourts(); }

    @GetMapping("/courts/{id}")
    public CourtResponse detail(@PathVariable("id") Long id) { return courts.get(id); }

    @GetMapping("/court-availability")
    public List<CourtService.AvailabilitySlot> availability(@RequestParam("date") LocalDate date,
            @RequestParam(value = "courtId", required = false) Long courtId) {
        return courts.availability(date, courtId);
    }

    @PostMapping("/booking-quotes")
    public BookingQuote quote(@RequestBody QuoteRequest request) {
        return courts.quote(request.courtId(), request.startAt(), request.endAt());
    }

    @GetMapping("/admin/courts")
    public List<CourtResponse> adminList() { return courts.adminCourts(); }

    @PostMapping("/admin/courts")
    @ResponseStatus(HttpStatus.CREATED)
    public CourtResponse addCourt(@RequestBody CourtService.NewCourt request) {
        return courts.addCourt(request);
    }

    @PatchMapping("/admin/courts/{id}")
    public CourtResponse updateCourt(@PathVariable("id") Long id,
                                      @RequestBody CourtService.UpdateCourt request) {
        return courts.updateCourt(id, request);
    }

    @PostMapping("/admin/courts/{id}/lock")
    public CourtResponse lock(@PathVariable("id") Long id) { return courts.lockCourt(id); }

    @PostMapping("/admin/courts/{id}/unlock")
    public CourtResponse unlock(@PathVariable("id") Long id) { return courts.unlockCourt(id); }

    @GetMapping("/admin/courts/{id}/price-rules")
    public List<CourtService.PriceRuleResponse> rules(@PathVariable("id") Long id) { return courts.priceRules(id); }

    @PostMapping("/admin/courts/{id}/price-rules/{ruleId}/change")
    public CourtService.PriceRuleResponse changePrice(@PathVariable("id") Long id,
            @PathVariable("ruleId") Long ruleId, @RequestBody CourtService.PriceChange request) {
        return courts.changePriceRule(id, ruleId, request);
    }

    @PostMapping("/admin/courts/{id}/price-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public CourtPriceRule addRule(@PathVariable("id") Long id,
                                  @RequestBody CourtService.NewPriceRule request) {
        return courts.addPriceRule(id, request);
    }

    public record QuoteRequest(Long courtId, java.time.OffsetDateTime startAt,
                               java.time.OffsetDateTime endAt) {}
}
