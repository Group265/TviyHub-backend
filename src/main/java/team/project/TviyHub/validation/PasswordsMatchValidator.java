package team.project.TviyHub.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Method;
import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

	private String passwordField;
	private String confirmationField;

	@Override
	public void initialize(PasswordsMatch annotation) {
		this.passwordField = annotation.password();
		this.confirmationField = annotation.confirmation();
	}

	@Override
	public boolean isValid(Object request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		Object password = read(request, passwordField);
		Object confirmation = read(request, confirmationField);
		// A missing value is reported by @NotBlank on the field itself
		if (password == null || confirmation == null || Objects.equals(password, confirmation)) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode(confirmationField).addConstraintViolation();
		return false;
	}

	private static Object read(Object request, String field) {
		try {
			Method accessor = request.getClass().getMethod(field);
			return accessor.invoke(request);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("No accessor '" + field + "' on " + request.getClass().getSimpleName(), e);
		}
	}
}
