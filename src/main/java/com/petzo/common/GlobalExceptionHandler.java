package com.petzo.common;

import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	static ProblemDetail problem(HttpStatus status, String code, String message) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(status, message);
		p.setProperty("code", code);
		return p;
	}

	@ExceptionHandler(ApiException.class)
	ProblemDetail api(ApiException e) {
		return problem(e.status(), e.code(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail invalid(MethodArgumentNotValidException e) {
		Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
		ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Datos inválidos");
		p.setProperty("fields", fields);
		return p;
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	ProblemDetail unreadable(Exception e) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Datos inválidos");
	}

	// FK violation: the client referenced a row that does not exist (e.g. municipalityId)
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail integrity(DataIntegrityViolationException e) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REFERENCE", "Referencia inválida");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception e) throws Exception {
		if (e instanceof org.springframework.web.ErrorResponse || e instanceof org.springframework.security.access.AccessDeniedException) throw e;
		log.error("Unexpected error", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Error interno");
	}
}
