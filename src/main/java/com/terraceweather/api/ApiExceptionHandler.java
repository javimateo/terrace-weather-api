package com.terraceweather.api;

import com.terraceweather.scoring.InvalidRequestException;
import com.terraceweather.scoring.TerraceProfile;
import com.terraceweather.weather.WeatherUnavailableException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Ordered first so our detailed messages win over Spring's generic problem+json handler. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(WeatherUnavailableException.class)
    ProblemDetail weatherUnavailable(WeatherUnavailableException e) {
        log.warn("Weather provider unavailable: {}", e.getMessage(), e.getCause());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Body or bound object failed bean validation: say which field and why. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        List<String> problems = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(f -> problems.add(describe(f)));
        e.getBindingResult().getGlobalErrors().forEach(g -> problems.add(g.getDefaultMessage()));
        return validationProblem(problems);
    }

    /** A constrained {@code @RequestParam} failed validation. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidParameter(HandlerMethodValidationException e) {
        List<String> problems = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                // Bound object such as RulesQuery: report the real field (maxRainProbability), not "rules".
                errors.getFieldErrors().forEach(f -> problems.add(describe(f)));
                errors.getGlobalErrors().forEach(g -> problems.add(g.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(err -> problems.add(name + ": " + err.getDefaultMessage()));
            }
        }
        return validationProblem(problems);
    }

    /** A query parameter that cannot be converted: say what was expected instead of a Spring internal message. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> type = e.getRequiredType();
        String expected;
        if (type == LocalDateTime.class) {
            expected = "a local date-time like 2026-09-22T13:00:00 (no 'Z' and no UTC offset)";
        } else if (type == LocalDate.class) {
            expected = "a date like 2026-09-22";
        } else if (type != null && type.isEnum()) {
            expected = "one of " + Arrays.toString(type.getEnumConstants());
        } else {
            expected = "a valid " + (type != null ? type.getSimpleName() : "value");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getName() + ": expected " + expected);
    }

    /** Unparseable JSON body: list the two usual culprits (times and enums). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is not valid JSON or contains an invalid value. Times must be local date-times "
                        + "like 2026-09-22T13:00:00 (no 'Z' and no UTC offset); 'profile' must be one of "
                        + Arrays.toString(TerraceProfile.values()));
    }

    /** "field: reason". Conversion failures get a plain message instead of Spring's internal type names. */
    private static String describe(FieldError f) {
        if (f.isBindingFailure()) {
            String hint = "profile".equals(f.getField()) ? " (expected one of " + Arrays.toString(TerraceProfile.values()) + ")" : "";
            return f.getField() + ": invalid value '" + f.getRejectedValue() + "'" + hint;
        }
        return f.getField() + ": " + f.getDefaultMessage();
    }

    private static ProblemDetail validationProblem(List<String> problems) {
        String detail = problems.isEmpty() ? "Validation failure" : String.join("; ", problems.stream().sorted().toList());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
