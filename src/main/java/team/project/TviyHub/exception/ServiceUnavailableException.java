package team.project.TviyHub.exception;

/** A feature is not configured or an external service is not reachable (HTTP 503). */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}
}
