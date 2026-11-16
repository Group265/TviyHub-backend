# Backend Deployment Guide (TviyHub Core Modules)

This guide covers the setup and execution of the workspace booking, subscription, and Stripe payment modules.

## Prerequisites
* Java 17 or higher
* Docker and Docker Compose (for the database setup)
* A Stripe developer account (to obtain a test secret key)

## Step-by-Step Setup

### 1. Configure Environment Variables
1. In the root directory of the backend project, copy the `.env.sample` template and rename it to `.env`:
   ```bash
   cp .env.sample .env
   ```
2. Open the `.env` file and populate the remaining variables:
    * `JWT_SECRET` — Generate a secure string (e.g., using `openssl rand -base64 48`).
    * `STRIPE_SECRET_KEY` — Paste your personal Stripe Test Secret Key (starts with `sk_test_`). You can obtain it from your personal Stripe Developers Dashboard.

### 2. Run the Database Container
The application uses Liquibase to automatically set up and seed the database schema. Start the MySQL container by executing:
```bash
docker-compose up -d
```
*Liquibase migrations will execute automatically upon Java application startup, seeding the system with 6 default workspaces and active tariffs.*

### 3. Start the Spring Boot Application
Run the application using the Maven wrapper or your preferred IDE:
```bash
./mvnw spring-boot:run
```

### 4. Interactive API Testing (Swagger UI)
Once the server is running, you can access the OpenAPI documentation at:
`http://localhost:8080/swagger-ui/index.html`

#### E2E Stripe Payment Testing Scenario in Swagger:
1. Navigate to **Bookings Management** and create a booking via `POST /api/bookings`. Note the `id` of the generated payment from the JSON response.
2. Go to **Client Payment Management** and execute `POST /api/payments/{id}/stripe-session`. Provide a mock `successUrl` and `cancelUrl` (e.g., `https://example.com`).
3. Copy the `sessionUrl` from the response payload, paste it into your browser, and complete the transaction using the global test card number: `4242 4242 4242 4242`.
4. After being redirected, extract the `session_id` parameter value from the browser's address bar.
5. Paste this token into the `GET /api/payments/success` endpoint to verify the transaction. The database entry status will switch to `PAID`.

## Emails with confirmation codes

Registration, password reset and profile changes send one-time codes by email. The shared SMTP settings of the team (Brevo) live in `.env.team`. The file contains the real key, so it is **not in git** (GitHub blocks pushes that contain secrets): get it from the team (password manager) and put it in the project root. `.env.team.sample` shows the format:

* `docker-compose up` and `./mvnw spring-boot:run` both read `.env.team` first and then your local `.env`.
* To use your own SMTP, set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM` in your `.env`; they override `.env.team`.
* On startup the log shows `Email: SMTP <host>, from=<address>`. If it says `MAIL_HOST is empty`, the codes are only written to the log.

**Important for testers:** Brevo only accepts connections from allowed IP addresses. Before testing emails, add your own public IP in Brevo (Security → Authorized IPs; you can find your IP at https://api.ipify.org). Without it registration fails with 503 and the log shows `525 5.7.1 Unauthorized IP address` or `535 Authentication failed`. Ask the person who owns the Brevo account to add it, or send them your IP.

If the SMTP key ever leaks, issue a new SMTP key in Brevo and update `.env.team` for everyone.

If emails do not arrive, check: the key is an SMTP key (not an API key), the port is 587 or 2525, `MAIL_FROM` is a sender verified in Brevo, the daily limit of the free plan (about 300 emails) is not used up, and the spam folder.

## Default administrator

On startup, if the database has no administrator, one is created so the admin panel can be opened right away:

* email `admin@admin.com`, password `12345678` (the log prints a warning while the default password is in use);
* change them with `ADMIN_EMAIL` and `ADMIN_PASSWORD` in your `.env`, or turn the feature off with `ADMIN_BOOTSTRAP_ENABLED=false`;
* if a user with that email already exists, they are made an administrator and keep their password;
* nothing happens once an administrator exists, so the account is never recreated or overwritten.

**These defaults are public, for development and testing only.** On any real server set your own `ADMIN_EMAIL` and `ADMIN_PASSWORD`, or change the password after the first login.

## Registration with a mandatory email code

1. `POST /api/auth/register` does **not** create the account. It stores the data, emails a 6-digit code and answers `202 {email, codeExpiresInSeconds}`.
2. `POST /api/auth/verify-email` `{email, code}` creates the account (email already verified) and answers `200` with `accessToken` and `refreshToken`, so the user is signed in at once.
3. After 5 wrong codes or 10 minutes the pending registration is dropped: register again.

Until the code is confirmed there is no user, so login returns 401. Registering again with the same email replaces the pending registration.
