package team.project.TviyHub.exception;

/** The uploaded file is bigger than allowed (HTTP 413). */
public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException(String message) {
		super(message);
	}
}
