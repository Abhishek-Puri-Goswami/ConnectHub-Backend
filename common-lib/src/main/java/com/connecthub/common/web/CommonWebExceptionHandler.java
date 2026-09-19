package com.connecthub.common.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base for every service's {@code @RestControllerAdvice}: turns the exceptions Spring MVC raises for
 * bad *client* input into the right 4xx instead of letting them fall into a catch-all 500.
 *
 * <pre>
 *   missing header / parameter / part, malformed JSON, wrong type, validation  -> 400
 *   missing X-User-Id (request did not come through the gateway's auth)        -> 401
 *   unknown path                                                               -> 404
 *   wrong HTTP method                                                          -> 405
 *   unsupported / unacceptable media type                                      -> 415 / 406
 *   body larger than the multipart ceiling                                     -> 413
 * </pre>
 *
 * All responses share one body shape that satisfies every existing frontend reader:
 * {@code {"success":false,"status":<code>,"error":"<msg>","message":"<msg>"}}.
 * Service advices extend this class and add their own domain exceptions; they must not re-declare a
 * handler for an exception type this class already maps (Spring would report an ambiguous mapping).
 */
public abstract class CommonWebExceptionHandler extends ResponseEntityExceptionHandler {

    /** Body used by every handler in the platform. */
    public static Map<String, Object> body(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("status", status);
        m.put("error", message);
        m.put("message", message);
        return m;
    }

    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(body(status.value(), message));
    }

    /** The response for anything unexpected: never echoes internal details to the client. */
    public static ResponseEntity<Map<String, Object>> internalError() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The gateway injects X-User-Id after validating the JWT; without it the caller is not authenticated.
        if (ex instanceof MissingRequestHeaderException h && "X-User-Id".equalsIgnoreCase(h.getHeaderName())) {
            return handleExceptionInternal(ex, null, headers, HttpStatus.UNAUTHORIZED, request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return new ResponseEntity<>(body(statusCode.value(), messageFor(ex, body, statusCode)), headers, statusCode);
    }

    private static String messageFor(Exception ex, Object body, HttpStatusCode status) {
        if (ex instanceof MissingRequestHeaderException h) {
            return "X-User-Id".equalsIgnoreCase(h.getHeaderName())
                    ? "Authentication required"
                    : "Missing required header: " + h.getHeaderName();
        }
        if (ex instanceof MethodArgumentNotValidException v) {
            String fields = v.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            return fields.isEmpty() ? "Validation failed" : fields;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) return "Resource not found";
        if (status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) return "Request is too large";
        if (body instanceof ProblemDetail pd && pd.getDetail() != null) return pd.getDetail();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Request failed";
    }
}
