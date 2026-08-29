package com.myduckstore.store.web;

import com.myduckstore.store.service.QuoteService;
import com.myduckstore.store.web.dto.QuoteRequest;
import com.myduckstore.store.web.dto.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry point for the store. Maps requests; holds no business logic. */
@Tag(name = "Store")
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
    @Operation(
            summary = "Get a price quote",
            description = """
                    Computes the full cost of an order without reserving or consuming any stock.

                    The price is resolved from the **lowest active unit price** for the requested
                    colour + size. All percentage rules (bulk discount, packaging, destination)
                    apply to the base subtotal (`quantity × unit price`), not compounding.
                    Each breakdown line is rounded to 2 dp; the `total` is their exact sum.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote computed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed — missing or invalid fields"),
            @ApiResponse(responseCode = "422", description = "No active stock for the requested colour + size")
    })
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
