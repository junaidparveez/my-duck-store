package com.myduckstore.warehouse.web;

import com.myduckstore.store.domain.ShippingMode;
import com.myduckstore.store.service.NoStockException;
import com.myduckstore.store.service.QuoteService;
import com.myduckstore.store.web.QuoteController;
import com.myduckstore.warehouse.domain.Color;
import com.myduckstore.warehouse.domain.Duck;
import com.myduckstore.warehouse.domain.Size;
import com.myduckstore.warehouse.service.DuckConflictException;
import com.myduckstore.warehouse.service.DuckNotFoundException;
import com.myduckstore.warehouse.service.DuckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract: status codes, and the single error shape produced by
 * {@code GlobalExceptionHandler}.
 *
 * <p>The negative cases matter as much as the happy ones. Before the handler existed, an unknown
 * colour returned Jackson's wrapper text including the fully-qualified class name of the enum, and
 * a constraint violation returned the SQL statement and the index name. Several assertions below
 * exist specifically to stop that regressing.
 */
@WebMvcTest({DuckController.class, QuoteController.class})
class DuckApiContractTest {

    private static final String DUCKS = "/api/v1/ducks";
    private static final String QUOTE = "/api/v1/orders/quote";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DuckService duckService;

    @MockitoBean
    private QuoteService quoteService;

    private static Duck duck(long id, Color color, Size size, String price, int quantity) {
        Duck duck = new Duck(color, size, new BigDecimal(price), quantity);
        duck.setId(id);
        return duck;
    }

    @Nested
    @DisplayName("status codes")
    class StatusCodes {

        @Test
        @DisplayName("a new duck is 201 Created")
        void createReturns201() throws Exception {
            when(duckService.add(any(), any(), any(), anyInt())).thenReturn(
                    new DuckService.AddOutcome(duck(1, Color.RED, Size.LARGE, "10.00", 5), true));

            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Large","price":10.00,"quantity":5}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.color").value("Red"))
                    .andExpect(jsonPath("$.size").value("Large"))
                    .andExpect(jsonPath("$.quantity").value(5))
                    // The logical-delete flag is internal and must never be exposed.
                    .andExpect(jsonPath("$.deleted").doesNotExist());
        }

        @Test
        @DisplayName("a merge into existing stock is 200 OK, not 201")
        void mergeReturns200() throws Exception {
            when(duckService.add(any(), any(), any(), anyInt())).thenReturn(
                    new DuckService.AddOutcome(duck(1, Color.RED, Size.LARGE, "10.00", 150), false));

            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Large","price":10.00,"quantity":50}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(150));
        }

        @Test
        @DisplayName("listing is 200 with the service order preserved")
        void listReturns200() throws Exception {
            when(duckService.findAll()).thenReturn(List.of(
                    duck(2, Color.GREEN, Size.SMALL, "5.00", 7),
                    duck(1, Color.RED, Size.LARGE, "10.00", 500)));

            mockMvc.perform(get(DUCKS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].quantity").value(7))
                    .andExpect(jsonPath("$[1].quantity").value(500));
        }

        @Test
        @DisplayName("a logical delete is 204 No Content")
        void deleteReturns204() throws Exception {
            mockMvc.perform(delete(DUCKS + "/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("error responses")
    class Errors {

        @Test
        @DisplayName("validation failures name every offending field and leak no Spring internals")
        void validationFailureIsActionable() throws Exception {
            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","price":-5,"quantity":-2}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.path").value(DUCKS))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.details.length()").value(3))
                    .andExpect(jsonPath("$.details[*].field")
                            .value(containsInAnyOrder("price", "quantity", "size")))
                    // Spring's raw BindingResult dump used to be returned verbatim.
                    .andExpect(content().string(not(
                            containsString("objectName"))))
                    .andExpect(content().string(not(
                            containsString("rejectedValue"))));
        }

        @Test
        @DisplayName("an unknown colour lists the valid values without naming a Java class")
        void unknownColourIsExplained() throws Exception {
            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Purple","size":"Large","price":10.00,"quantity":5}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                    .andExpect(jsonPath("$.message")
                            .value(containsString("Red, Green, Yellow, Black")))
                    .andExpect(content().string(not(
                            containsString("com.myduckstore"))));
        }

        @Test
        @DisplayName("an unknown size lists the valid sizes")
        void unknownSizeIsExplained() throws Exception {
            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Enormous","price":10.00,"quantity":5}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(containsString("XLarge, Large, Medium, Small, XSmall")));
        }

        @Test
        @DisplayName("malformed JSON is a clean 400")
        void malformedJsonIsRejected() throws Exception {
            mockMvc.perform(post(DUCKS).contentType(MediaType.APPLICATION_JSON).content("{\"color\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("a non-numeric id is a 400, not a 500")
        void nonNumericIdIsRejected() throws Exception {
            mockMvc.perform(delete(DUCKS + "/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("a missing duck is 404")
        void missingDuckIs404() throws Exception {
            when(duckService.update(eq(9999L), any(), anyInt()))
                    .thenThrow(new DuckNotFoundException(9999L));

            mockMvc.perform(put(DUCKS + "/9999").contentType(MediaType.APPLICATION_JSON).content("""
                            {"price":10.00,"quantity":5}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Duck 9999 does not exist"));
        }

        @Test
        @DisplayName("a concurrent change is 409")
        void concurrentChangeIs409() throws Exception {
            when(duckService.update(anyLong(), any(), anyInt()))
                    .thenThrow(new DuckConflictException("Duck 1 changed concurrently. Retry the request."));

            mockMvc.perform(put(DUCKS + "/1").contentType(MediaType.APPLICATION_JSON).content("""
                            {"price":10.00,"quantity":5}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }

        @Test
        @DisplayName("a database constraint violation is 409 and never returns SQL")
        void constraintViolationIsScrubbed() throws Exception {
            when(duckService.update(anyLong(), any(), anyInt())).thenThrow(
                    new DataIntegrityViolationException(
                            "could not execute statement [ERROR: duplicate key value violates unique "
                                    + "constraint \"uq_duck_active_color_size_price\"] "
                                    + "[update duck set color=?,deleted=?,price=? where id=?]"));

            mockMvc.perform(put(DUCKS + "/1").contentType(MediaType.APPLICATION_JSON).content("""
                            {"price":200.00,"quantity":5}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"))
                    .andExpect(content().string(not(
                            containsString("uq_duck_active"))))
                    .andExpect(content().string(not(
                            containsString("update duck set"))));
        }

        @Test
        @DisplayName("an unexpected failure is 500 with nothing internal in the body")
        void unexpectedFailureIsScrubbed() throws Exception {
            doThrow(new IllegalStateException("connection pool exhausted at com.zaxxer.hikari.Pool"))
                    .when(duckService).delete(1L);

            mockMvc.perform(delete(DUCKS + "/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected internal error occurred."))
                    .andExpect(content().string(not(
                            containsString("hikari"))));
        }
    }

    @Nested
    @DisplayName("store endpoint")
    class Store {

        @Test
        @DisplayName("no stock for the requested duck is 422, not 404")
        void noStockIs422() throws Exception {
            when(quoteService.quote(any(), any(), anyInt(), any(), any()))
                    .thenThrow(new NoStockException(Color.BLACK, Size.MEDIUM));

            mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Black","size":"Medium","quantity":5,
                             "country":"USA","shippingMode":"Air"}"""))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("NO_STOCK"))
                    .andExpect(jsonPath("$.message")
                            .value(containsString("Black / Medium")));
        }

        @Test
        @DisplayName("a quote must ask for at least one duck")
        void quantityMustBePositive() throws Exception {
            mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Medium","quantity":0,
                             "country":"USA","shippingMode":"Air"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("quantity"));
        }

        @Test
        @DisplayName("a blank destination country is rejected")
        void countryMustNotBeBlank() throws Exception {
            mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Medium","quantity":5,
                             "country":"   ","shippingMode":"Air"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("country"));
        }

        @Test
        @DisplayName("an unknown shipping mode lists the valid modes")
        void unknownShippingModeIsExplained() throws Exception {
            mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Medium","quantity":5,
                             "country":"USA","shippingMode":"Rocket"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(containsString("Air, Land, Sea")));
        }

        @Test
        @DisplayName("the quote passes the request through to the service unchanged")
        void requestIsPassedThrough() throws Exception {
            when(quoteService.quote(Color.RED, Size.MEDIUM, 5, "USA", ShippingMode.AIR))
                    .thenThrow(new NoStockException(Color.RED, Size.MEDIUM));

            // Reaching NoStockException proves the controller parsed and forwarded every field;
            // any mis-binding would have produced a different stub miss.
            mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content("""
                            {"color":"Red","size":"Medium","quantity":5,
                             "country":"USA","shippingMode":"Air"}"""))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
