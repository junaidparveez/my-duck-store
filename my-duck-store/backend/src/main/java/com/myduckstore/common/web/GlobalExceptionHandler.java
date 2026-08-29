package com.myduckstore.common.web;

import com.myduckstore.store.service.NoStockException;
import com.myduckstore.warehouse.service.DuckConflictException;
import com.myduckstore.warehouse.service.DuckNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Translates exceptions into the single {@link ApiError} shape.
 *
 * <p>This is the <em>only</em> place that maps a failure onto an HTTP status code. Domain
 * exceptions such as {@link DuckNotFoundException} therefore stay free of any
 * {@code org.springframework.http} import, so the service layer does not depend on the web layer.
 *
 * <p>Two rules apply to every handler here:
 * <ul>
 *   <li>Anything a client can fix is reported precisely - the field, the value, the valid range.
 *   <li>Anything a client cannot fix is logged in full server-side and reduced to a generic
 *       sentence in the response. Stack traces, SQL statements and database constraint names
 *       never reach a caller.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -- 400 ---------------------------------------------------------------------

    /** Bean Validation failures: one {@code details} entry per rejected field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldViolation(
                        fieldError.getField(), describe(fieldError)))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();

        log.debug("validation failed on {}: {}", request.getRequestURI(), details);

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed. See 'details' for the offending fields.",
                request, details);
    }

    /**
     * Unreadable or unparseable body.
     *
     * <p>An unknown enum value (a colour of "Purple", a shipping mode of "Rocket") surfaces here,
     * because {@code Color.from} / {@code Size.from} / {@code ShippingMode.from} throw
     * {@link IllegalArgumentException} from inside Jackson. Those messages already list the valid
     * values, so we dig the original out of the cause chain rather than returning Jackson's own
     * wrapper text, which would otherwise leak internal class names to the client.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        String message = rootIllegalArgumentMessage(ex).orElse("Malformed JSON request body.");

        log.debug("unreadable body on {}: {}", request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request, null);
    }

    /** A path variable that is not the declared type, e.g. {@code GET /api/v1/ducks/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Parameter " + ex.getName() + " has an invalid value: " + ex.getValue(),
                request, null);
    }

    // -- 404 / 409 / 422 ---------------------------------------------------------

    @ExceptionHandler(DuckNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(DuckNotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoRoute(NoResourceFoundException ex,
                                                  HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(),
                request, null);
    }

    @ExceptionHandler(DuckConflictException.class)
    public ResponseEntity<ApiError> handleConflict(DuckConflictException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, null);
    }

    /**
     * A database constraint rejected the write.
     *
     * <p>The service layer resolves the expected collisions itself (see
     * {@code DuckService.update}), so reaching this handler means a genuine race: a concurrent
     * request created the same colour + size + price between our check and our write. Retrying
     * succeeds, hence 409. The database message is logged, never returned - it names tables
     * and constraints.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("data integrity violation on {}", request.getRequestURI(), ex);

        return build(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with the current state of the warehouse, most likely "
                        + "because a concurrent request changed the same duck. Retry the request.",
                request, null);
    }

    @ExceptionHandler(NoStockException.class)
    public ResponseEntity<ApiError> handleNoStock(NoStockException ex,
                                                  HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "NO_STOCK", ex.getMessage(), request, null);
    }

    // -- 500 ---------------------------------------------------------------------

    /** Last resort. The cause is logged with its stack trace; the client gets none of it. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected internal error occurred.", request, null);
    }

    // -- Helpers -----------------------------------------------------------------

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  HttpServletRequest request,
                                                  List<ApiError.FieldViolation> details) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), code, message, request.getRequestURI(), details));
    }

    /** Bean Validation's own message, falling back to something readable if it is absent. */
    private static String describe(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return message != null ? message : "is invalid";
    }

    /** Walks the cause chain for the {@link IllegalArgumentException} our enum factories throw. */
    private static Optional<String> rootIllegalArgumentMessage(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return Optional.of(cause.getMessage());
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return Optional.empty();
    }
}
