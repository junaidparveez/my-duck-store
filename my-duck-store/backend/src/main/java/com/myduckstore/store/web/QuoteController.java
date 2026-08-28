package com.myduckstore.store.web;

import com.myduckstore.store.service.QuoteService;
import com.myduckstore.store.web.dto.QuoteRequest;
import com.myduckstore.store.web.dto.QuoteResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry point for the store. Maps requests; holds no business logic. */
@RestController
@RequestMapping("/api/orders")
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    /**
     * Price a potential order without reserving or consuming any stock.
     *
     * <p>Returns the package type, protection materials, itemized cost breakdown,
     * and the total. The total is the exact sum of the breakdown lines.
     */
    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return service.quote(
                request.color(),
                request.size(),
                request.quantity(),
                request.country(),
                request.shippingMode());
    }
}
