package team.project.TviyHub.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the UTF-8 size of a string. bcrypt only reads the first 72 BYTES of a password, and the encoder
 * refuses longer input, so a limit counted in characters is not enough for non-ASCII passwords.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

	String message() default "must be at most {value} bytes in UTF-8 (letters outside English take 2 bytes or more)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int value();
}
