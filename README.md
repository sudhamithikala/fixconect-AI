# FixConnect AI – Spring Boot Backend

REST API for the **FixConnect AI** home-repair marketplace (the static site in `SE project/`).
It replaces the demo Express `server.js` and backs every form, button and dashboard section in the HTML pages.

* Java 17+, Spring Boot 3.3, Spring Data JPA, Spring Security (JWT), Bean Validation
* **Swagger UI** (springdoc-openapi): <http://localhost:8080/swagger-ui.html>
* H2 database by default (file `./data`), MySQL profile included
* Demo data seeded on first start (same people as the HTML mock-ups)

## Run (backend + website together)

The website (all HTML/CSS/JS pages) lives in **`src/main/resources/static`** and is packaged inside the app,
so one command runs everything:

* **Website:** <http://localhost:8080/> (redirects to `index.html`)
* **Swagger UI:** <http://localhost:8080/swagger-ui.html>

Edit the pages in `src/main/resources/static` and refresh (when running from the IDE / `mvn spring-boot:run`;
restart if the change does not show). To serve pages from another folder while editing, set
`fixconnect.frontend-dir` (or env `FIXCONNECT_FRONTEND_DIR`).

The pages call the API through `api.js` (JWT kept in `localStorage`). If you open the pages from another server
(e.g. VS Code Live Server on :5500) they automatically call `http://localhost:8080`.

Build a single deployable file: `mvn clean package` → `java -jar target/fixconnect-backend-1.0.0.jar`.

```bash
mvn spring-boot:run                 # or: mvn clean package && java -jar target/fixconnect-backend-1.0.0.jar
mvn test                            # end-to-end smoke tests (login → request → accept → complete → invoice → pay → review)
```

Open **http://localhost:8080/swagger-ui.html** → pick a group (`1-all`, `2-customer`, `3-provider`) from the top-right dropdown.

| Demo login | Password | Role |
|---|---|---|
| customer@gmail.com (Naga Sudha) | 12345 | `CUSTOMER` |
| provider@gmail.com (Rahul Kumar) | 12345 | `PROVIDER` |
| **adminfixconnectai@gmail.com** (FixConnect Admin) – sign in at `/admin-login.html` | **Admin@123** | `ADMIN` |
| mahesh@fixconnect.ai (new technician awaiting verification) | 12345 | `PROVIDER` |
| arjun@ / vijay@ / ravi@ / kirankumar@ / suresh@ / lakshmi@fixconnect.ai | 12345 | `PROVIDER` |

1. `POST /api/auth/login` → copy `token`
2. Click **Authorize** in Swagger UI, paste the token
3. Call `/api/customer/**` or `/api/provider/**` endpoints

H2 console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/fixconnect`, user `sa`, no password).

MySQL: `mvn spring-boot:run -Dspring-boot.run.profiles=mysql` (set `DB_USERNAME` / `DB_PASSWORD`).

## Admin console

* **Login:** <http://localhost:8080/admin-login.html> (not linked from the public site) → `admin-dashboard.html`
* **Seeded admin:** `adminfixconnectai@gmail.com` / `Admin@123` – created on startup if no admin exists (also on existing
  databases). Override with `fixconnect.admin.email`, `fixconnect.admin.password`, `fixconnect.admin.name`
  (env `FIXCONNECT_ADMIN_EMAIL`, `FIXCONNECT_ADMIN_PASSWORD`). Change the default password for any real deployment.
* **What an admin can do**
  * See all customers and service providers (search + filters), overview counts.
  * **Verify service providers** – ID proof, trade licence, background check. All three = approved.
    Unverified technicians cannot go online, do not appear in technician search and receive no jobs.
  * **Deactivate** a customer or provider (with a reason, e-mailed to the user) and **reactivate** later.
    Deactivation logs the user out immediately (existing tokens are rejected).
    Provider: goes offline, bookings sent only to them return to the pool. Customer: pending requests are cancelled.
  * Admins do not manage customers' bookings.
* **API:** `GET /api/admin/dashboard`, `GET /api/admin/customers`, `GET /api/admin/providers`,
  `GET /api/admin/providers/{id}`, `PATCH /api/admin/providers/{id}/verification`, `PATCH /api/admin/users/{id}/status`
  (Swagger group `4-admin`).

## Password reset by e-mail

`forgot-password.html` → `POST /api/auth/forgot-password` → the user receives an e-mail with a one-time link
`{APP_BASE_URL}/forgot-password.html?token=…` → the page validates it (`GET /api/auth/reset-password/validate`)
and shows a "new password" form → `POST /api/auth/reset-password` → a "password changed" confirmation e-mail.

Security: random 256-bit token, only its SHA-256 hash is stored, valid 30 min, single use, a new request
invalidates older links, 60 s resend cooldown, same response for unknown e-mails (no account enumeration).

**Gmail SMTP setup**
1. Google Account → Security → turn on **2-Step Verification**.
2. Google Account → Security → **App passwords** → create one (e.g. "FixConnect") → copy the 16-character password.
3. Set environment variables before starting the app (PowerShell):
   ```powershell
   $env:MAIL_USERNAME="yourname@gmail.com"
   $env:MAIL_PASSWORD="abcd efgh ijkl mnop"   # the app password, not your normal password
   $env:APP_BASE_URL="http://localhost:8080"  # public URL of the site when deployed
   mvn spring-boot:run
   ```
   In IntelliJ/Eclipse/VS Code add them to the Run Configuration's environment variables instead.

Without `MAIL_USERNAME` no e-mail is sent; the reset link is printed in the application log so you can still test.
Other providers (Outlook, SendGrid, Mailtrap…) work by also setting `MAIL_HOST` and `MAIL_PORT`.

## Configuration (`src/main/resources/application.yml`)

| Key | Default | Notes |
|---|---|---|
| `fixconnect.jwt.secret` | dev value | **Set `FIXCONNECT_JWT_SECRET` (32+ chars) in production** |
| `fixconnect.jwt.expiration-minutes` | 1440 | token lifetime |
| `fixconnect.firebase.project-id` | `fixconnect-ai` | used to verify Google sign-in ID tokens |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | – | SMTP login (Gmail app password) for reset e-mails |
| `APP_BASE_URL` | `http://localhost:8080` | used to build links inside e-mails |
| `fixconnect.auth.expose-reset-token` | `false` | dev only: also return the token in the API response |
| `fixconnect.upload-dir` | `./uploads` | photos & ID proofs |
| `fixconnect.emergency.radius-km` | 5 | SOS broadcast radius |
| `fixconnect.seed-demo-data` | `true` | load demo users/bookings on an empty DB |

## How the pages map to the API

| Page | APIs |
|---|---|
| `login.html` | `POST /api/auth/login`, `POST /api/auth/google` (send `await user.getIdToken()` from Firebase) |
| `signup.html`, `customer-register.html`, `provider-register.html` | `POST /api/auth/register/customer`, `POST /api/auth/register/provider`, `POST /api/provider/profile/id-proof` |
| `forgot-password.html` | `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` |
| `index.html`, `service(s).html` | `GET /api/services`, `GET /api/technicians/top`, `POST /api/ai/diagnose` |
| `contact.html` | `POST /api/contact` |
| `emergency.html` | `GET /api/emergency/categories`, `POST /api/customer/emergency/sos` |
| `customer-profile.html` | `/api/customer/profile`, `/api/customer/addresses/**`, `PUT /api/users/me/password` |
| `customer-dashboard.html` (20 sections) | `/api/customer/dashboard`, `/requests/**`, `/history`, `/photos`, `/favourites/**`, `/invoices/**`, `/warranties/**`, `/rewards/**`, `/settings`, `/api/technicians/**`, `/api/notifications/**` |
| `provider-profile.html` | `/api/provider/profile`, `/verification`, `/availability`, `/settings` |
| `provider-dashboard.html` (19 sections) | `/api/provider/dashboard`, `/requests/**`, `/jobs/**`, `/services/**`, `/customers`, `/reviews`, `/performance`, `/invoices`, `/warranties`, `/photos`, `/api/ai/repair-guide` |

## Request lifecycle

```
PENDING ──accept──▶ ACCEPTED ──▶ EN_ROUTE ──▶ ARRIVED ──▶ IN_PROGRESS ──▶ COMPLETED
   │  (#CMP-xxxx)     (#BK-xxxx)                              │ invoice      │ 30-day warranty, review
   └──cancel / decline (direct booking goes back to the pool)               └─ pay → loyalty points (10 / ₹100)
```

* A request without `technicianId` goes to the pool: every available technician in that category and within their radius is notified.
* `emergency: true` / SOS requests are CRITICAL, shown first, and broadcast within 5 km.
* Warranty claims create a free follow-up request for the same technician.
* AI analysis is a transparent keyword/rule engine in `AiService` – swap in an LLM later without changing the API.

## Project layout

```
com.fixconnect
├── config        SecurityConfig (JWT, CORS, roles), OpenApiConfig (Swagger), DataSeeder
├── controller    14 REST controllers (89 endpoints)
├── domain        JPA entities + enums
├── dto           request/response records
├── repository    Spring Data repositories
├── security      JwtService, CurrentUser, FirebaseTokenVerifier
├── service       business logic
└── common        ApiException, GlobalExceptionHandler (uniform JSON errors)
```

Errors always look like:
`{"success":false,"status":400,"error":"Bad Request","message":"Validation failed","path":"/api/...","fieldErrors":{"email":"must be a well-formed email address"}}`

## Endpoint catalogue (89)

### 00. Health

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/` | Root check (same as the original Express server) |
| GET | `/api/health` | Health status |

### 01. Auth

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/login` | Login as customer or provider |
| POST | `/api/auth/register/customer` | Create customer account (customer-register.html / signup.html - Customer) |
| POST | `/api/auth/register/provider` | Register as service provider (provider-register.html / signup.html - Provider) |
| POST | `/api/auth/google` | Continue with Google (Firebase ID token) |
| POST | `/api/auth/forgot-password` | Send password-reset instructions (forgot-password.html) |
| POST | `/api/auth/reset-password` | Reset password with the token from the reset e-mail |

### 02. Public Catalog

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/services` | List service categories (Plumbing, Electrical, AC Repair, Appliance Repair, Cleaning, Carpentry, Painting...) |
| GET | `/api/services/{code}` | Get one service category by code |
| GET | `/api/emergency/categories` | Emergency SOS categories, hotline and dispatch radius (emergency.html) |
| POST | `/api/contact` | Submit the contact form (contact.html) |

### 03. Technicians

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/technicians` | Find technicians |
| GET | `/api/technicians/top` | Top rated verified technicians (dashboard widget) |
| GET | `/api/technicians/{technicianId}` | Technician profile with services, rates and recent reviews |
| GET | `/api/technicians/{technicianId}/reviews` | All reviews of a technician |

### 04. AI

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/ai/diagnose` | AI Problem Analysis - predicts root cause, category, severity, specialist and cost (public) |
| POST | `/api/ai/repair-guide` | AI Repair Guide Assistant for technicians - tools, steps and safety notes (PROVIDER) |

### 05. Customer - Profile

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/customer/dashboard` | Dashboard overview: stats, ongoing service, upcoming services, top technicians, notifications |
| GET | `/api/customer/profile` | My profile with summary counters |
| PUT | `/api/customer/profile` | Update profile details |
| GET | `/api/customer/addresses` | Saved service locations |
| POST | `/api/customer/addresses` | Add address |
| PUT | `/api/customer/addresses/{id}` | Update address |
| PATCH | `/api/customer/addresses/{id}/primary` | Make address primary |
| DELETE | `/api/customer/addresses/{id}` | Delete address |
| GET | `/api/customer/settings` | Notification preferences (SMS emergency alerts, WhatsApp status updates, e-mail) |
| PUT | `/api/customer/settings` | Update notification preferences (null fields are left unchanged) |

### 06. Customer - Requests & Bookings

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/customer/requests` | Request a home service / raise a complaint |
| POST | `/api/customer/requests/{id}/photos` | Upload problem photo for a request |
| GET | `/api/customer/requests` | My complaints / bookings |
| GET | `/api/customer/requests/{id}` | Request / booking details including photos |
| PATCH | `/api/customer/requests/{id}/reschedule` | Reschedule visit |
| POST | `/api/customer/requests/{id}/cancel` | Cancel request / booking |
| GET | `/api/customer/requests/{id}/tracking` | Live technician tracking (location, distance, ETA) |
| GET | `/api/customer/requests/{id}/timeline` | ETA / Service status milestones |
| GET | `/api/customer/requests/{id}/photos` | Problem + before & after photos of a request |
| POST | `/api/customer/requests/{id}/review` | Rate & review the technician after completion |
| GET | `/api/customer/photos` | All before & after photos across my jobs |
| GET | `/api/customer/history` | Service history (date, service, technician, cost, rating) |

### 07. Customer - Emergency SOS

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/customer/emergency/sos` | Dispatch SOS technician |

### 08. Customer - Favourites, Invoices, Warranty, Rewards

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/customer/favourites` | Favourite technicians |
| POST | `/api/customer/favourites/{technicianId}` | Save technician to favourites |
| DELETE | `/api/customer/favourites/{technicianId}` | Remove technician from favourites |
| GET | `/api/customer/invoices` | Digital invoices (PDF at /api/invoices/{id}/pdf) |
| POST | `/api/customer/invoices/{id}/pay` | Pay invoice (optionally apply a redeemed voucher code). Earns loyalty points. |
| GET | `/api/customer/warranties` | Service warranties (30-day coverage) |
| POST | `/api/customer/warranties/{id}/claim` | Claim free repair under warranty - creates a follow-up request to the same technician |
| GET | `/api/customer/rewards` | Loyalty points, tier, vouchers and redemptions |
| POST | `/api/customer/rewards/redeem` | Redeem a voucher with points - returns a coupon code usable when paying an invoice |

### 09. Provider - Profile & Settings

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/provider/profile` | Technician profile |
| PUT | `/api/provider/profile` | Update technician information (name, specialization, skills, experience, area) |
| POST | `/api/provider/profile/id-proof` | Upload Government Photo ID (Aadhaar / PAN) |
| GET | `/api/provider/verification` | Verification & credentials status (ID, trade licence, background check, Pro badge) |
| PATCH | `/api/provider/availability` | Online / offline availability toggle |
| PUT | `/api/provider/location` | Update live location (used for tracking, distance and ETA) |
| GET | `/api/provider/settings` | Work preferences, bank payout details, notification preferences |
| PUT | `/api/provider/settings` | Update preferences (service radius, max daily jobs, payout details). Null fields unchanged. |
| GET | `/api/provider/services` | My services & rates |
| POST | `/api/provider/services` | Add a service offering with base rate |
| PUT | `/api/provider/services/{id}` | Update a service offering |
| DELETE | `/api/provider/services/{id}` | Delete a service offering |

### 10. Provider - Requests & Jobs

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/provider/requests` | Incoming service requests matched to my category & radius (emergencies first) |
| POST | `/api/provider/requests/{id}/accept` | Accept request - becomes booking #BK-xxxx, customer notified |
| POST | `/api/provider/requests/{id}/decline` | Decline request |
| GET | `/api/provider/jobs` | My jobs |
| GET | `/api/provider/jobs/{id}` | Job details with customer contact and photos |
| GET | `/api/provider/jobs/{id}/timeline` | Job status timeline |
| PATCH | `/api/provider/jobs/{id}/status` | Broadcast job status: EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED (completion issues a 30-day warranty) |
| PATCH | `/api/provider/jobs/{id}/eta` | Update customer ETA |
| POST | `/api/provider/jobs/{id}/photos` | Upload BEFORE / AFTER proof-of-work photo |
| GET | `/api/provider/jobs/{id}/photos` | Photos of a job |
| POST | `/api/provider/jobs/{id}/invoice` | Generate & send itemised invoice (labour + parts) |

### 11. Provider - Insights

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/provider/dashboard` | Provider dashboard: stats, incoming requests, today's schedule, feedback |
| GET | `/api/provider/customers` | Customer directory (contact history) |
| GET | `/api/provider/reviews` | Ratings & reviews summary |
| GET | `/api/provider/performance` | Performance analytics: acceptance/completion rates, monthly jobs & earnings |
| GET | `/api/provider/invoices` | Invoices I issued |
| GET | `/api/provider/warranties` | Warranty certificates issued |
| GET | `/api/provider/photos` | All before & after photos I uploaded |

### 12. Notifications

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/notifications` | My notifications (customer or provider) |
| PATCH | `/api/notifications/{id}/read` | Mark one notification as read |
| PATCH | `/api/notifications/read-all` | Mark all notifications as read |
| DELETE | `/api/notifications/{id}` | Delete a notification |

### 13. Account & Files

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/files/{name}` | Download an uploaded photo / document |
| GET | `/api/invoices/{id}` | Get an invoice (customer or technician of the invoice) |
| GET | `/api/invoices/{id}/pdf` | Download invoice PDF |
| GET | `/api/users/me` | Current logged-in user |
| PUT | `/api/users/me/password` | Change password (Security & Preferences) |
"# fixconnectai" 
