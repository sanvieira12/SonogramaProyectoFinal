package com.sonograma.exception;

import com.sonograma.dto.ManualDiscogsDuplicateConflictDTO;
import com.sonograma.dto.ManualDiscogsPendingFinalizationConflictDTO;
import com.sonograma.dto.ManualDiscogsFinalizationConflictDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(LocalDateTime.now(), 401, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        LocalDateTime.now(),
                        403,
                        "No tenés permisos para realizar esta acción",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(LocalDateTime.now(), 404, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorResponse> handleNegocio(
            NegocioException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(LocalDateTime.now(), 422, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ConflictoNegocioException.class)
    public ResponseEntity<ErrorResponse> handleConflicto(
            ConflictoNegocioException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(LocalDateTime.now(), 409, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ManualDiscogsDuplicateException.class)
    public ResponseEntity<ManualDiscogsDuplicateConflictDTO> handleManualDiscogsDuplicate(
            ManualDiscogsDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ManualDiscogsDuplicateConflictDTO(
                "MANUAL_DISCOGS_DUPLICATE",
                ex.getMessage(),
                ex.getSourceCustomerCode(),
                ex.getDiscogsReleaseId(),
                ex.getExistingCopies()));
    }

    @ExceptionHandler(ManualDiscogsPendingFinalizationException.class)
    public ResponseEntity<ManualDiscogsPendingFinalizationConflictDTO> handleManualDiscogsPendingFinalization(
            ManualDiscogsPendingFinalizationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ManualDiscogsPendingFinalizationConflictDTO(
                "MANUAL_DISCOGS_PENDING_OPERATIONS",
                ex.getMessage(),
                ex.getPendingCount()));
    }

    @ExceptionHandler(ManualDiscogsFinalizationConfirmationException.class)
    public ResponseEntity<ManualDiscogsFinalizationConflictDTO> handleManualDiscogsFinalizationConfirmation(
            ManualDiscogsFinalizationConfirmationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ManualDiscogsFinalizationConflictDTO(
                "MANUAL_DISCOGS_RECONCILIATION_CONFIRMATION_REQUIRED",
                ex.getMessage(), ex.getWarnings(), ex.getReconciliation()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(LocalDateTime.now(), 400, mensaje, request.getRequestURI()));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAsyncTimeout(
            AsyncRequestTimeoutException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        LocalDateTime.now(),
                        503,
                        "La descarga tardó demasiado y fue cancelada por timeout.",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(LocalDateTime.now(), 500, ex.getMessage(), request.getRequestURI()));
    }
}
