package team.project.TviyHub.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level check that two fields of a request record hold the same text, e.g. a password and its
 * repetition. The error is reported on the confirmation field.
 */
@Documented
@Constraint(validatedBy = PasswordsMatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {

	String message() default "passwords do not match";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** Name of the password field. */
	String password();

	/** Name of the field that repeats the password; the error is attached to it. */
	String confirmation();
}
