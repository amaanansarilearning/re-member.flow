# FORM / Club Management

Gym membership management built with Angular 21 and Spring Boot 3.4. It includes an owner dashboard, package catalog, member registration with a per-membership discount, membership search/cancellation, and a seven-day renewal watch.

## Run locally

Prerequisites: Java 17, Maven 3.8+, Node.js 20.19+, and Docker Compose (or a local MySQL 8 server).

For local source development, copy `.env.example` to `.env` and replace the JWT secret with a generated Base64 key. In PowerShell, generate one with:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Set `MYSQL_ROOT_PASSWORD=password` in `.env` for local development, which connects as `root` with that password. Then start MySQL with `docker compose up -d mysql`. PowerShell and Spring Boot do not automatically load values from `.env`, so set the matching datasource password in the API terminal. From the project root, start the API with:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_DATASOURCE_PASSWORD = "password"
mvn -f backend\pom.xml spring-boot:run
```

If you chose a different `MYSQL_ROOT_PASSWORD`, use that same value for `SPRING_DATASOURCE_PASSWORD`. The `dev` profile provides a local-only JWT key unless `GYM_JWT_SECRET` is set in the API process; do not use the development profile or its fallback key outside local development. Run `npm ci` / `npm start` from `frontend/`. Angular proxies `/api` to the local Spring API. The `dev` profile creates example packages and `owner@gym.local` / `ChangeMe123!`. Without an explicit profile, the API uses `prod` and requires `GYM_JWT_SECRET` to be set in the API process.

## Docker deployment

Copy `.env.example` to `.env`, replace the database passwords and `GYM_JWT_SECRET`, then set real SMTP credentials to enable email. Generate the JWT key with `openssl rand -base64 32` (or generate 32 random bytes and Base64-encode them). Start the complete stack from the project root:

```sh
docker compose up --build -d
```

For a local smoke test on the Docker host, open `http://localhost:4200` and choose **Set up a club** to register a new gym, or sign in with your credentials. The frontend and API share one origin, and nginx routes `/api` requests to Spring. The web port binds to `127.0.0.1` by default; for deployment, terminate TLS at a reverse proxy/ingress and forward HTTPS traffic to `http://127.0.0.1:4200`. Do not bind the web port to a public interface without TLS and appropriate access controls. MySQL data persists in the `gym-mysql-data` volume. Use `docker compose logs -f api` to inspect the backend and `docker compose down` to stop the stack without deleting data. Avoid `docker compose down -v` unless you intend to erase the database.

Compose defaults Hibernate to `ddl-auto=update` to initialize a new self-hosted deployment. For an existing production database, apply the documented receipt and operations-feature schema changes before switching to `ddl-auto=validate`; use managed secrets and terminate TLS at a reverse proxy or ingress. The Maven integration tests use an isolated H2 database; H2 is not an application runtime database.

## Roles and access

- `OWNER`: organization dashboard, package create/edit, branch/franchise management, staff assignment, member registration, membership management.
- `STAFF`: member registration, membership/renewal lists and cancellation at their assigned branch, package view.
- `MEMBER`: own membership view and package catalog.

Owners can provision staff accounts from the Team screen. Staff and owners can renew an existing member without creating another user account.

Each installation has one gym organization with multiple branches. Branches are classified as `COMPANY_OWNED` or `FRANCHISE`; memberships, staff, and optional branch-specific packages are assigned to a branch. Owners can see and manage all locations, while staff only see and operate at their assigned location. A franchise location is currently part of the same organization and shares its owner's oversight; separately owned franchisees with isolated organizations, billing, and reporting are not a separate tenant in this version. Branches are deactivated rather than deleted so their history remains intact.

Member and staff registration collects a phone number. Owners can edit their profile and change a password from **Account**; a forgotten password can be reset with a single-use email code that expires after 10 minutes. Owners can permanently remove staff accounts from **Team**.

Enrollment emails go to the member and owner, with an optional receipt attached; adding staff emails the owner. Email delivery requires SMTP configuration and notification failures are logged without undoing successful enrollment or staff creation. Staff and owners may attach a JPEG, PNG, or WebP receipt image (up to 5 MB) when enrolling a member. The image is stored in a separate `membership_receipts` table and linked to its membership, so ordinary membership lists do not load the BLOB. The receipt is attached to both enrollment emails. Keep database backups sized for uploaded receipts.

Packages can use calendar-month periods (1, 3, 6, or 12 months) or an exact day count (1-3,650 days), which is useful for short expiry demonstrations. End dates are inclusive paid-through dates: a two-day package starting today ends tomorrow.

Renewals default to the next calendar date after the current paid-through date; an already-expired membership defaults to today. Staff/owners can schedule a later start date, but cannot overlap the existing term. The previous row is marked `Renewed` and removed from the expiry watch once a renewal is scheduled; the replacement row is `Upcoming` until its selected start date. Memberships are date-only: the new term starts at the beginning of that date and time-of-day is not tracked. The business calendar is `Asia/Kolkata`. On startup, MySQL backfills renewal links for legacy terms that ended the day before their successor began.

## Membership operations, history, and reporting

Packages are archived by turning off **Available for new members**, not by deleting a package referenced by membership history. Owners can browse Available, Archived, or All packages and restore an archived package by editing it. Existing memberships remain intact. New and renewed memberships save the package name, description, and duration as a snapshot; on upgrade, older rows are backfilled from the package's current values because earlier package versions were not retained.

Owners and staff can freeze an active membership with a reason and inclusive start/end dates. A freeze must start today or later, start within the paid term, cannot overlap another freeze, and is limited to 365 days per freeze. The paid-through date is extended by the frozen calendar days when the freeze is recorded. A scheduled freeze can be cancelled before it starts; an active freeze can be ended early, with unused days removed from the extension. Both changes require a reason and are audited. Completed freezes remain in history and cannot be undone. Frozen status is exposed in the membership API and UI; **Check access** gives a manual eligibility decision, including denying entry during a freeze. There is no attendance log or physical door-control integration, so a gym must use this check operationally rather than expecting automatic gate control.

Owners can transfer a membership to another active branch in the same organization without changing its dates or recorded payments. Owners can also correct package, dates, discount, list price, and amount paid; corrections require a reason, cannot overlap another active/scheduled membership or a linked renewal, and are retained in the audit history. The owner-only **History** view records actor, time, reason, and before/after values for package, membership, branch, and staff-account changes. The view displays the most recent 250 records. Audit and reminder records begin accumulating after this version is deployed.

Owner reports accept date ranges up to 366 days and optional branch filtering. Revenue and package-performance reports are based on memberships created in the selected period (including renewals); cancellations use the recorded cancellation date. CSV exports are available for revenue, memberships, cancellations, and package performance. Since older cancellations do not have a cancellation timestamp, they are not included in date-based cancellation reports.

The renewal reminder scheduler checks every 15 minutes in the configured business timezone and sends emails 7, 3, and 1 day before expiry to the member, organization owners, and staff assigned to the membership's branch. It skips memberships with a scheduled renewal. Per-recipient attempts, status, and errors are visible under **History**. Failed delivery is retried with increasing delays, capped at 24 hours, so SMTP must be configured for reminders to arrive. Override the scheduler with `GYM_RENEWAL_REMINDERS_CRON` if needed.

## Gym onboarding and password recovery

The `dev` profile seeds `owner@gym.local` / `ChangeMe123!` and `admin@gymflow.io` / `AdminPassword123!`. In production, new gym owners onboard via the self-service registration form (`POST /api/auth/register-gym`) which provisions their gym organization, initial branch, and owner account.

In `dev` (and tests), the reset code is returned to the UI for local testing. In production, configure SMTP with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS`. Email OTP itself has no protocol fee, but actual delivery depends on an SMTP provider; many providers offer limited free tiers, while volume, custom domains, or deliverability features may cost money. Do not expose reset codes in production.

Login, gym registration (`/api/auth/register-gym`), and password-reset request/confirmation are public; all other API routes require a signed bearer token. The browser sends login passwords in the JSON request body; DevTools displays that body, while HTTPS encrypts it in transit in production. Passwords and reset codes are BCrypt-hashed at rest. Never expose login over public HTTP. Tokens use HS256 and expire after 12 hours by default. Password changes/resets and account deletion invalidate existing tokens immediately. Change token expiration with `GYM_JWT_EXPIRATION_MS`.

## Production configuration

Run with `SPRING_PROFILES_ACTIVE=prod` and set `GYM_JWT_SECRET` to a base64-encoded random key of at least 32 bytes. Never use the development fallback key or local MySQL password outside the `dev` profile. Configure MySQL with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; set `GYM_CORS_ORIGIN` to the deployed UI origin and `GYM_BUSINESS_ZONE` to the club's IANA timezone. Configure SMTP for password recovery and notifications. Demo data is disabled outside `dev`; the application currently defaults Hibernate to `ddl-auto=update`, so set `SPRING_JPA_DDL_AUTO=validate` after applying schema changes.

For production, use schema migrations instead of Hibernate `ddl-auto=update`, terminate TLS at the ingress, and store credentials in a secret manager. The included Compose deployment is a single-host starting point; configure `GYM_CORS_ORIGIN` to the public HTTPS origin and `WEB_PORT` for the host port your local TLS proxy forwards to. Keep `WEB_BIND_ADDRESS` at `127.0.0.1` when the proxy runs on the same host.

For an existing MySQL database that uses `ddl-auto=validate`, apply the receipt-table schema shown below and the one-time [operations feature migration](./backend/migrations/mysql-operations-features.sql) before deploying this version. The operations migration backfills old package snapshots from current package values; old package revisions and cancellation timestamps cannot be reconstructed. Run it once, then set `SPRING_JPA_DDL_AUTO=validate`.

```sql
CREATE TABLE membership_receipts (
	id BIGINT NOT NULL AUTO_INCREMENT,
	membership_id BIGINT NOT NULL,
	receipt_image LONGBLOB NOT NULL,
	receipt_image_content_type VARCHAR(32) NOT NULL,
	receipt_image_filename VARCHAR(80) NOT NULL,
	PRIMARY KEY (id),
	UNIQUE KEY uk_membership_receipts_membership_id (membership_id),
	CONSTRAINT fk_membership_receipts_membership
		FOREIGN KEY (membership_id) REFERENCES memberships (id)
);
```

## API overview

- `POST /api/auth/login`
- `POST /api/auth/register-gym` (self-service gym onboarding)
- `POST /api/auth/password-reset/request` and `/api/auth/password-reset/confirm`
- `POST /api/auth/change-password`, `PUT /api/auth/profile` (authenticated)
- `GET /api/dashboard` (owner)
- `GET /api/branches` (owner sees all organization branches; staff sees their assigned branch)
- `POST /api/branches`, `PUT /api/branches/{id}` (owner branch management)
- `GET|POST /api/plans`, `PUT /api/plans/{id}` (writes owner-only)
- `GET|POST /api/memberships`
- `GET /api/memberships/page?page=0&size=25&search=...&filter=all|active|expiring` (paginated by member; all terms are grouped together)
- `GET /api/memberships/expiring?withinDays=7`
- `POST /api/memberships/{id}/cancel`
- `POST /api/memberships/{id}/cancel-renewal` (cancel a future term while retaining the current term)
- `POST /api/memberships/{id}/renew`
- `POST /api/memberships/{id}/freeze` (owner/staff)
- `POST /api/memberships/{id}/freezes/{freezeId}/cancel` (owner/staff; future freezes only; requires a reason)
- `POST /api/memberships/{id}/freezes/{freezeId}/end-early` (owner/staff; active freezes only; requires a reason)
- `GET /api/memberships/{id}/access` (owner/staff/member; membership scope enforced)
- `POST /api/memberships/{id}/transfer` and `PUT /api/memberships/{id}/correction` (owner)
- `GET /api/history`, `GET /api/renewal-reminders` (owner)
- `GET /api/reports?from=YYYY-MM-DD&to=YYYY-MM-DD[&branchId=ID]` (owner)
- `GET /api/reports/export.csv?from=...&to=...&type=revenue|memberships|cancellations|packages[&branchId=ID]` (owner)
- `GET|POST /api/staff`, `DELETE /api/staff/{id}` (owner)

Membership registration accepts `fullName`, `email`, `phoneNumber`, `password`, `planId`, `discountPercent` (0-100), optional `startDate`, and optional `receiptImageData` as an image data URL. Package requests provide exactly one of `durationMonths` or `durationDays`. The final charge and end date are calculated by the API.
