package com.fastcode.error;

import com.fastcode.error.commons.logging.ErrorLoggingHelper;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus; // still fine to use for response statuses
import org.springframework.http.HttpStatusCode; // Spring 6 override signatures
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;

import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FastCodeRestExceptionHandler extends ResponseEntityExceptionHandler {

	@Autowired
	private ErrorLoggingHelper logHelper;

	@PostConstruct
	public void init() {
		System.out.println("FastCodeRestExceptionHandler loaded");
	}

	/**
	 * Missing required request parameter.
	 */
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		String error = ex.getParameterName() + " parameter is missing";
		return buildResponseEntity(new ApiError(BAD_REQUEST, error, ex));
	}

	/**
	 * Unsupported media type.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		StringBuilder builder = new StringBuilder();
		builder.append(ex.getContentType());
		builder.append(" media type is not supported. Supported media types are ");
		ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(", "));
		String message = builder.substring(0, Math.max(0, builder.length() - 2));
		return buildResponseEntity(new ApiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, ex));
	}

	/**
	 * @Valid validation failure.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(BAD_REQUEST);
		apiError.setMessage("Validation error");
		apiError.addValidationErrors(ex.getBindingResult().getFieldErrors());
		apiError.addValidationError(ex.getBindingResult().getGlobalErrors());
		return buildResponseEntity(apiError);
	}

	/**
	 * Malformed JSON request.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		String error = "Malformed JSON request";
		return buildResponseEntity(new ApiError(HttpStatus.BAD_REQUEST, error, ex));
	}

	/**
	 * Error writing JSON response.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotWritable(
			HttpMessageNotWritableException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		String error = "Error writing JSON output";
		return buildResponseEntity(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, error, ex));
	}

	/**
	 * No handler found for request.
	 */
	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(
			NoHandlerFoundException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(BAD_REQUEST);
		apiError.setMessage(String.format("Could not find the %s method for URL %s",
				ex.getHttpMethod(), ex.getRequestURL()));
		apiError.setDebugMessage(ex.getMessage());
		return buildResponseEntity(apiError);
	}

	/**
	 * Unsupported HTTP method.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
			HttpRequestMethodNotSupportedException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(METHOD_NOT_ALLOWED);
		apiError.setMessage("Specified HTTP Method Is Not Allowed");
		apiError.setDebugMessage(ex.getMessage());
		return buildResponseEntity(apiError);
	}

	/**
	 * Jakarta Bean Validation violation (@Validated on params/paths).
	 */
	@ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
	protected ResponseEntity<Object> handleConstraintViolation(
			jakarta.validation.ConstraintViolationException ex) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(BAD_REQUEST);
		apiError.setMessage("Validation error");
		apiError.addValidationErrors(ex.getConstraintViolations());
		return buildResponseEntity(apiError);
	}

	/**
	 * Entity not found.
	 */
	@ExceptionHandler(EntityNotFoundException.class)
	protected ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex) {
		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(NOT_FOUND);
		apiError.setMessage("The entity with the specified ID was not found in the system.");
		apiError.setDebugMessage("Additional details can go here for debugging.");
		return buildResponseEntity(apiError);
	}

	/**
	 * Entity already exists.
	 */
	@ExceptionHandler(EntityExistsException.class)
	protected ResponseEntity<Object> handleEntityExists(EntityExistsException ex) {
		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(CONFLICT);
		apiError.setMessage(ex.getMessage());
		return buildResponseEntity(apiError);
	}

	/**
	 * Data integrity violations (unique constraints, FKs, etc.).
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	protected ResponseEntity<Object> handleDataIntegrityViolation(
			DataIntegrityViolationException ex, WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);

		if (ex.getCause() instanceof ConstraintViolationException) {
			return buildResponseEntity(new ApiError(HttpStatus.CONFLICT, ex.getMessage(), ex.getCause()));
		}
		return buildResponseEntity(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, ex));
	}

	/**
	 * Argument type mismatch (e.g., converting path/query params).
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(
			MethodArgumentTypeMismatchException ex, WebRequest request) {

		logHelper.getLogger().error("An Exception Occurred:", ex);
		ApiError apiError = new ApiError(BAD_REQUEST);
		apiError.setMessage(String.format(
				"The parameter '%s' of value '%s' could not be converted to type '%s'",
				ex.getName(), ex.getValue(),
				ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"));
		apiError.setDebugMessage(ex.getMessage());
		return buildResponseEntity(apiError);
	}

	/**
	 * Catch-all.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleAnyException(Exception ex, WebRequest request) {
		logHelper.getLogger().error("An Exception Occurred:", ex);
		String error = "Internal error occured";
		return buildResponseEntity(new ApiError(HttpStatus.BAD_REQUEST, error, ex));
	}

	private ResponseEntity<Object> buildResponseEntity(ApiError apiError) {
		return new ResponseEntity<>(apiError, apiError.getStatus());
	}
}
