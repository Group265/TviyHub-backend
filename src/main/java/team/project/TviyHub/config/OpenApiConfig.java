package team.project.TviyHub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("TviyHub API")
						.version("0.0.1")
						.description("Backend of the TviyHub coworking space.\n\n"
								+ "**Authentication.** Register with POST /api/auth/register and confirm the email with the "
								+ "emailed code (POST /api/auth/verify-email). Log in with POST /api/auth/login and send the "
								+ "accessToken in the Authorization: Bearer <token> header (the Authorize button).\n\n"
								+ "**Google sign-in.** The frontend gets an ID token from Google Sign-In and sends it to "
								+ "POST /api/auth/google; the response is the same pair of tokens as for login. A new account "
								+ "is created on the first sign-in (without phone and password); an existing account with the "
								+ "same email is linked. Such accounts set a password through forgot-password.\n\n"
								+ "**Token refresh.** The access token is short-lived (15 minutes by default). When it expires "
								+ "(401), call POST /api/auth/refresh with the refreshToken to get a new pair. Every refresh "
								+ "token can be used once; using an old one again ends the whole session. "
								+ "POST /api/auth/logout ends the session.\n\n"
								+ "**Roles.** USER and ADMIN. Endpoints under /api/admin require ADMIN (403 otherwise).\n\n"
								+ "**Errors.** Errors are returned as application/problem+json with status and detail."))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}
}
