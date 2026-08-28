package com.myduckstore.warehouse.web;

import com.myduckstore.warehouse.service.DuckService;
import com.myduckstore.warehouse.web.dto.CreateDuckRequest;
import com.myduckstore.warehouse.web.dto.DuckResponse;
import com.myduckstore.warehouse.web.dto.UpdateDuckRequest;
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
@RestController
@RequestMapping("/api/ducks")
public class DuckController {

    private final DuckService service;

    public DuckController(DuckService service) {
        this.service = service;
    }

    /** Active ducks, sorted by quantity. */
    @GetMapping
    public List<DuckResponse> list() {
        return service.findAll().stream()
                .map(DuckResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DuckResponse add(@Valid @RequestBody CreateDuckRequest request) {
        return DuckResponse.from(
                service.add(request.color(), request.size(), request.price(), request.quantity()));
    }

    @PutMapping("/{id}")
    public DuckResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDuckRequest request) {
        return DuckResponse.from(service.update(id, request.price(), request.quantity()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
