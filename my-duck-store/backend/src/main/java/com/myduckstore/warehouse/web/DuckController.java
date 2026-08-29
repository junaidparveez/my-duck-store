package com.myduckstore.warehouse.web;

import com.myduckstore.warehouse.service.DuckService;
import com.myduckstore.warehouse.web.dto.CreateDuckRequest;
import com.myduckstore.warehouse.web.dto.DuckResponse;
import com.myduckstore.warehouse.web.dto.UpdateDuckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP entry point for the warehouse. Maps requests and status codes; holds no business logic. */
@Tag(name = "Warehouse")
@RestController
@RequestMapping("/api/ducks")
public class DuckController {

    private final DuckService service;

    public DuckController(DuckService service) {
        this.service = service;
    }

    @Operation(summary = "List all active ducks",
               description = "Returns every duck that has not been logically deleted, sorted by quantity ascending (lowest stock first).")
    @ApiResponse(responseCode = "200", description = "Duck list returned")
    @GetMapping
    public List<DuckResponse> list() {
        return service.findAll().stream()
                .map(DuckResponse::from)
                .toList();
    }

    @Operation(summary = "Add or merge duck stock",
               description = "Creates a new duck record. If a duck with the same colour, size and price already exists, its quantity is incremented instead (merge). Returns 201 on create, 200 on merge.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New duck created"),
            @ApiResponse(responseCode = "200", description = "Existing duck quantities merged"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DuckResponse add(@Valid @RequestBody CreateDuckRequest request) {
        return DuckResponse.from(
                service.add(request.color(), request.size(), request.price(), request.quantity()));
    }

    @Operation(summary = "Edit price and quantity",
               description = "Updates only the price and quantity of an existing duck. Colour and size are immutable after creation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Duck updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Duck not found or deleted")
    })
    @PutMapping("/{id}")
    public DuckResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDuckRequest request) {
        return DuckResponse.from(service.update(id, request.price(), request.quantity()));
    }

    @Operation(summary = "Logically delete a duck",
               description = "Marks the duck as deleted. The row stays in the database (deleted=true) and disappears from all listings. Cannot be undone via the API.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Duck not found or already deleted")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
