# Concept: Two-Factor Authentication

Status: in progress (steps 1–4 of 10 done — schema, TOTP enrollment, backup codes, 2FA login verification, settings UI, step-up middleware, WebAuthn enrollment / login / step-up)
Scope: TOTP + FIDO2/WebAuthn second factor for the existing password-based login, with role-based mandate, per-`StationUserType` admin escalation, soft-grace enrollment, freshness-window step-up for sensitive actions, opt-in trusted-device remember, backup codes + admin reset as recovery.
Not in scope: passkeys / passwordless login, SMS or email OTP, push-based authenticators, single-sign-on / external IdP integration, 2FA on the `/remote/` federation surface (those are RSA-signed server-to-server already).

## 1. Decisions

The strategic questions are settled. Everything below builds on these.

| Decision | Value |
|---|---|
| Default policy | **Required** for the elevated role set; opt-in for everyone else. |
| Elevated role set | `InstancePermission.ADMINISTRATOR`, `StationPermission.STATION_ADMINISTRATOR`, `StationPermission.STATION_MANAGER`, `StationPermission.MANAGER`. |
| Per-`StationUserType` escalation | Instance and station admins can mark "every member with this `StationUserType` must enrol", scoped instance-wide or to one station. |
| Factors offered | TOTP (RFC 6238) + WebAuthn/FIDO2 (CTAP2). Strictly second-factor — no passwordless. |
| Recovery | Backup codes (10, one-shot, hashed) **mandatory at enrollment**; admin-issued reset for the lost-everything case. UI nudges users to add a second factor (e.g. TOTP + hardware key). |
| Step-up | Freshness-window (default 5 min). Categories: account security, federation, critical instance admin config, role / user-permission changes. |
| Trusted device | Opt-in checkbox on the 2FA screen; default off; admin-configurable max window capped at 30 days. |
| Enrollment grace | Soft — banner from day 0, hard block on day `N+1`. Configurable max 7 days. |

## 2. Frameworks

Both libraries are widely deployed, actively maintained, and ship clean Java APIs that fit Guice. The recommendation is **Yubico's `webauthn-server-core`** for FIDO2 and a small in-house TOTP module backed by `dev.samstevens.totp` for the QR/URI plumbing; the only external binary requirement is none (everything runs in-JVM).

| Concern | Library | License | Notes |
|---|---|---|---|
| FIDO2 / WebAuthn | `com.yubico:webauthn-server-core` | BSD-2 | Yubico-maintained, ships strict spec compliance, supports all common attestation formats, no transitive Spring deps. Pin a recent 2.x. |
| TOTP / HOTP | `dev.samstevens.totp:totp` | Apache 2 | ~200 LOC wrapper over `org.apache.commons.codec`, also generates `otpauth://` URIs. Replaceable with hand-rolled code if we want zero deps; the wrapper saves time. |
| QR rendering | `com.google.zxing:core` + `:javase` | Apache 2 | We render the QR on the backend as a PNG byte stream embedded in the enrollment response. The same dep covers any future QR needs. |

`bcrypt` (already in deps) is reused for backup-code hashing.

Configuration env-var: `TWO_FACTOR_SECRET_KEY` — 32-byte base64. Used as the AES-GCM key for TOTP-secret encryption at rest; rotates via a kid in the row. Hard-fail at boot if absent **and** 2FA is enabled in the config.

## 3. Threat model

In scope:
- A stolen browser session: step-up blocks the attacker from changing the victim's password, disabling their 2FA, granting themselves a role, or pairing a new federation partner.
- A leaked password: 2FA blocks the attacker from logging in as a mandated user at all.
- A lost or stolen device: backup codes get the user back in; the trusted-device cookie expires within 30 days max.
- A compromised admin: step-up on role changes makes "I quietly grant myself ADMIN" louder than today.

Out of scope (acknowledged risks):
- An attacker who controls the user's email inbox can still trigger password reset, which clears 2FA enrollment **only if the user has lost all factors and the reset is admin-issued**. Email recovery for 2FA was explicitly rejected.
- A malicious instance admin can disable 2FA enforcement, reset any user's 2FA, or escalate roles. Audit log is the only mitigation; no technical control.
- Phishing of TOTP codes is possible (not WebAuthn). WebAuthn is the recommended primary factor.

## 4. Data model

### 4.1 New tables

```sql
CREATE TYPE ember_schema.two_factor_kind AS ENUM ('TOTP', 'WEBAUTHN', 'BACKUP_CODES');

CREATE TABLE ember_schema.account_2fa_factor (
    id           SERIAL PRIMARY KEY,
    account_id   INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    kind         ember_schema.two_factor_kind NOT NULL,
    label        TEXT NOT NULL,                 -- user-supplied ("Phone", "Yubikey 5C")
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NULL,
    disabled_at  TIMESTAMPTZ NULL
);
CREATE INDEX ON ember_schema.account_2fa_factor(account_id) WHERE disabled_at IS NULL;
CREATE UNIQUE INDEX ON ember_schema.account_2fa_factor(account_id, kind)
    WHERE kind IN ('TOTP', 'BACKUP_CODES') AND disabled_at IS NULL;
```

`TOTP` and `BACKUP_CODES` are singletons per account (the unique index enforces it); `WEBAUTHN` can have many rows so users can register multiple keys.

```sql
CREATE TABLE ember_schema.account_2fa_totp (
    factor_id        INTEGER PRIMARY KEY REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    secret_encrypted BYTEA NOT NULL,        -- AES-GCM(secret_b32, TWO_FACTOR_SECRET_KEY)
    secret_kid       SMALLINT NOT NULL DEFAULT 1,
    digits           SMALLINT NOT NULL DEFAULT 6,
    period_seconds   SMALLINT NOT NULL DEFAULT 30,
    algorithm        TEXT NOT NULL DEFAULT 'SHA1'   -- compat with Google Authenticator
);

CREATE TABLE ember_schema.account_2fa_webauthn (
    factor_id           INTEGER PRIMARY KEY REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    credential_id       BYTEA NOT NULL,
    public_key_cose     BYTEA NOT NULL,
    signature_counter   BIGINT NOT NULL DEFAULT 0,
    aaguid              UUID NULL,
    transports          TEXT[] NOT NULL DEFAULT '{}',
    attestation_format  TEXT NULL,
    user_handle         BYTEA NOT NULL          -- 64 random bytes per account, stable
);
CREATE UNIQUE INDEX ON ember_schema.account_2fa_webauthn(credential_id);

CREATE TABLE ember_schema.account_2fa_backup_code (
    id          SERIAL PRIMARY KEY,
    factor_id   INTEGER NOT NULL REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL,        -- bcrypt
    used_at     TIMESTAMPTZ NULL,
    used_via_ip CIDR NULL
);
CREATE INDEX ON ember_schema.account_2fa_backup_code(factor_id) WHERE used_at IS NULL;
```

### 4.2 Session / step-up

Extend the existing `account_session` table:

```sql
ALTER TABLE ember_schema.account_session
    ADD COLUMN two_factor_verified_at TIMESTAMPTZ NULL,
    ADD COLUMN device_trust_id        INTEGER NULL
        REFERENCES ember_schema.account_2fa_trusted_device(id) ON DELETE SET NULL;
```

`two_factor_verified_at` is the source of truth for step-up freshness; it ticks every time the user completes a step-up ceremony (or the initial login 2FA challenge).

```sql
CREATE TABLE ember_schema.account_2fa_trusted_device (
    id            SERIAL PRIMARY KEY,
    account_id    INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL,        -- bcrypt of the cookie value
    user_agent    TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    trusted_until TIMESTAMPTZ NOT NULL,
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ NULL
);
CREATE INDEX ON ember_schema.account_2fa_trusted_device(account_id) WHERE revoked_at IS NULL;
```

The cookie value is a 32-byte SecureRandom, URL-safe base64; only its bcrypt lives in the table. Revoke-all on the security settings page nukes every row.

### 4.3 Policy

```sql
CREATE TABLE ember_schema.two_factor_policy (
    id             SERIAL PRIMARY KEY,
    scope          TEXT NOT NULL,                       -- 'INSTANCE' | 'STATION'
    station_id     INTEGER NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    user_type_id   INTEGER NULL REFERENCES ember_schema.station_user_type(id) ON DELETE CASCADE,
    required       BOOLEAN NOT NULL,
    grace_days     SMALLINT NOT NULL DEFAULT 7,
    created_by     INTEGER NULL REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((scope = 'INSTANCE' AND station_id IS NULL)
        OR (scope = 'STATION' AND station_id IS NOT NULL))
);
CREATE UNIQUE INDEX ON ember_schema.two_factor_policy(COALESCE(station_id, 0), user_type_id);
```

Note: role-based mandate (manager and up) is **not** stored — it's derived from the existing role/permission tables at request time. The `two_factor_policy` table only holds the per-`StationUserType` escalations.

### 4.4 Audit

```sql
CREATE TYPE ember_schema.two_factor_event AS ENUM (
    'ENROLLED', 'REMOVED', 'LOGIN_VERIFIED', 'STEPUP_VERIFIED',
    'BACKUP_CODE_USED', 'BACKUP_CODE_REGENERATED',
    'ADMIN_RESET', 'TRUSTED_DEVICE_ADDED', 'TRUSTED_DEVICE_REVOKED',
    'POLICY_CHANGED'
);

CREATE TABLE ember_schema.account_2fa_audit (
    id          SERIAL PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    actor_id    INTEGER NULL REFERENCES ember_schema.account(id) ON DELETE SET NULL,   -- NULL when account_id = self
    event       ember_schema.two_factor_event NOT NULL,
    factor_kind ember_schema.two_factor_kind NULL,
    user_agent  TEXT NULL,
    country     CHAR(2) NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON ember_schema.account_2fa_audit(account_id, created_at DESC);
CREATE INDEX ON ember_schema.account_2fa_audit(actor_id, created_at DESC) WHERE actor_id IS NOT NULL;
```

Retention: same pruner pattern as `api_request_log`. Default keep 365 days. Never aggregate or anonymize — admin actions in particular are forensically valuable.

## 5. Login flow

```
POST /api/v1/auth/login
{ email, password, trustedDeviceToken? }
```

Server steps:
1. Verify password via existing `PasswordHasher`.
2. Resolve "is 2FA required for this account?" — true if (a) any role/permission match in the mandate set, or (b) the account is a member of a station + user-type combo with an active force policy.
3. Resolve "is 2FA enrolled?" — at least one non-disabled `account_2fa_factor` row exists (the `BACKUP_CODES` row alone does not count; backup codes are recovery, not enrollment).
4. Branch:

   | Required | Enrolled | Trusted device cookie valid | Result |
   |:-:|:-:|:-:|---|
   | no  | no  | -        | Full session, done. |
   | no  | yes | -        | Pre-auth token, client must complete 2FA. |
   | yes | no  | -        | Full session **with `enrollmentDeadline`**, banner shown. If past deadline → forced enrollment, pre-auth token, client must enrol. |
   | yes | yes | yes      | Full session with `two_factor_verified_at = now`. No prompt. |
   | yes | yes | no       | Pre-auth token, client must complete 2FA. |

5. Pre-auth token: an `AccountToken` of type `TWO_FACTOR_PENDING`, valid 5 minutes, scoped to the account and IP. Cannot be used as a bearer for any other route. Issued in the response body, not as a cookie.

```
POST /api/v1/auth/2fa
{ preAuthToken, factor: 'TOTP' | 'WEBAUTHN' | 'BACKUP_CODE',
  proof: <factor-specific>, rememberDeviceDays?: 1..30 }
```

Server:
1. Validate pre-auth token; consume it (single-use).
2. Verify the proof against the indicated factor. WebAuthn assertions verify the signature counter monotonicity.
3. Mint a normal `account_session` with `two_factor_verified_at = now`.
4. If `rememberDeviceDays` is set:
   - 32-byte SecureRandom cookie value.
   - Insert `account_2fa_trusted_device` with bcrypt hash, `trusted_until = now + min(rememberDeviceDays, max)`.
   - Set `Set-Cookie: ember_2fa_trust=<token>; HttpOnly; Secure; SameSite=Strict; Max-Age=<seconds>`.
5. Audit log: `LOGIN_VERIFIED` with the factor kind.

## 6. Step-up flow

Routes opt in via a new `RouteRole` family — `StepUp` — added alongside `StationPermission` / `InstancePermission`. Marking a route `@StepUpRequired(Category.ACCOUNT_SECURITY)` means: before this route runs, the session's `two_factor_verified_at` must be within `auth.twoFactor.stepUpFreshnessSeconds`.

If stale:
- Response: `401 Unauthorized` with body `{ "error": "step_up_required", "category": "ACCOUNT_SECURITY" }` and header `X-StepUp-Required: ACCOUNT_SECURITY`.
- Client opens a modal `/auth/2fa/stepup`, user proves 2FA, server updates `two_factor_verified_at`, client retries.

Categories (covers the four lines decided):
- `ACCOUNT_SECURITY` — password change, email change, 2FA factor add/remove, backup-code regenerate, session revoke-all, trusted-device revoke.
- `FEDERATION` — pair partner, unpair, key rotation, sharing config edit, federation toggle.
- `INSTANCE_CONFIG` — instance settings edit, module toggle, storage quota change, mail server config, legal-doc update, federation discovery toggle. (Station-admin settings are deliberately not in this set.)
- `ROLE_CHANGE` — every endpoint that grants or revokes a `StationPermission` / `InstancePermission` / member-type change / batch-roles endpoint.

The category enum is rendered to the user in the step-up prompt so they see what they're authorising ("Confirming role changes — please verify with your second factor").

## 7. Enrollment

### 7.1 First time, voluntary

`/account/security/2fa` page lists the user's factors. "Add TOTP" walks through:
1. POST `/api/v1/account/2fa/totp/begin` — server generates a secret, stores it encrypted in a `TWO_FACTOR_PENDING_ENROLL` token row (not yet active), returns `{ otpauthUri, qrPng, recoveryCodes }`. The recovery codes are returned **once** alongside the new TOTP secret and presented to the user with a copy / download button; the user must check "I've stored these" to advance.
2. POST `/api/v1/account/2fa/totp/confirm { code }` — server verifies the code against the pending secret. On success, write `account_2fa_factor` + `account_2fa_totp` + 10 `account_2fa_backup_code` rows. Discard the pending token.
3. Audit: `ENROLLED` (TOTP), `BACKUP_CODE_REGENERATED`.

WebAuthn enrollment follows the same shape: `/begin` returns the Yubico `PublicKeyCredentialCreationOptions` JSON; `/confirm` verifies the attestation and registers the credential. Backup codes are *already* created from the first factor enrollment; subsequent factor adds do not re-issue them.

### 7.2 Forced (mandated user, past grace)

Login returns `{ requiresEnrollment: true, preAuthToken }`. Frontend redirects to `/auth/enroll-2fa`, which is the same wizard but without an "I'll do this later" exit. The pre-auth token is the only credential they can present until they enrol; it is bound to the account and won't authorise any other route.

### 7.3 Grace banner

While a user is mandated but not yet enrolled and within the grace window:
- Every API response includes a header `X-Two-Factor-Deadline: <ISO instant>`.
- Frontend renders a persistent banner with the countdown and a CTA into the wizard.

`two_factor_required_since` is computed on demand from `(role assignment timestamp, policy creation timestamp, account creation timestamp)` — whichever is most recent that caused the mandate. The grace deadline is `that + min(policy.grace_days, 7)`.

## 8. Recovery

### 8.1 Backup codes

Generated as 10 codes per account. Format: `xxxx-xxxx-xxxx` where each `x` is `[A-Z0-9]` (60-bit entropy). Hashed at rest via bcrypt with the same cost factor as passwords. Single-use — `used_at` set on consumption, never re-set. The user can `POST /api/v1/account/2fa/backup-codes/regenerate` (step-up required, category `ACCOUNT_SECURITY`); regeneration discards all existing codes and issues 10 new ones.

When a code is used to log in, the response body warns "You have N codes left" if N ≤ 3. At N = 0 the next login still works (any other factor) but the security page flags it red.

### 8.2 Admin reset

- Instance admin: any account.
- Station admin: members of stations they administer; can never reset an instance admin via this surface.

`POST /api/v1/admin/accounts/{id}/2fa/reset` (step-up: `ACCOUNT_SECURITY`):
1. Soft-disable every factor row (`disabled_at = now`).
2. Mark all backup codes used.
3. Revoke every active session and trusted device for the target.
4. Send an email to the affected user: "Your 2FA was reset by `<admin email>` at `<time>`. If this wasn't expected, contact support."
5. Audit: `ADMIN_RESET` with the admin as actor.

The next login by the reset user follows the "required + not enrolled" branch — banner if within grace, forced enrollment otherwise. Grace is reset to a fresh window on admin reset to give them time to re-enrol.

## 9. Policy engine

Single resolver service `TwoFactorPolicyService` with one method:

```java
public RequirementState resolve(int accountId);

public sealed interface RequirementState {
    record NotRequired() implements RequirementState {}
    record RequiredWithGrace(Instant deadline) implements RequirementState {}
    record RequiredImmediate() implements RequirementState {}
}
```

Computation:
1. Pull every `StationPermission` and `InstancePermission` held by the account across every station membership. If any falls in the elevated set → required.
2. Pull every `StationUserType` the account is assigned across all memberships. For each, check `two_factor_policy` for a matching INSTANCE or STATION scope. If any policy `required = true` matches → required.
3. If required:
   - Look at the timestamp that *caused* the requirement (role grant, policy creation, or membership creation, whichever is most recent).
   - Compute `deadline = that + min(grace_days, 7)`.
   - If `now < deadline` → `RequiredWithGrace(deadline)`; else `RequiredImmediate()`.

Cached via Caffeine, 60-second TTL, invalidated explicitly on role / policy / membership changes via the existing `DomainEventBus`.

## 10. Admin & member UI

### 10.1 Member: `/account/security`

Sections:
- **2FA status** (green badge "Active" or red "Required by your role").
- **Authenticator app** — add / remove / rename TOTP factor.
- **Security keys** — list registered WebAuthn credentials, add, rename, revoke.
- **Backup codes** — show remaining count, "Regenerate" (step-up).
- **Trusted devices** — list with user-agent + last-seen + "Revoke", plus "Revoke all".
- **Recommendation card** when only one factor is enrolled: "Add a second factor so you don't get locked out".

### 10.2 Station admin: `/station/manage/security`

- **Per-user-type enforcement** — a table of station user types with a toggle "Require 2FA for this user type". Toggling on triggers step-up.
- **Member 2FA status** — per-member list with badge (Enrolled / Not enrolled / Mandated within grace / Past grace). Action menu: "Reset 2FA" (with confirmation and step-up).

### 10.3 Instance admin: `/admin/security`

- **Instance-wide policy** — same shape as the station view but scoped to every station.
- **Audit log viewer** — paginated table of `account_2fa_audit`, filter by account, actor, event, date range.
- **Configuration** — knobs from §11. All edits are step-up.

## 11. Configuration

```yaml
auth:
  twoFactor:
    enabled: true
    secretKeyEnv: TWO_FACTOR_SECRET_KEY   # must resolve at boot or fail
    stepUpFreshnessSeconds: 300
    trustedDeviceMaxDays: 30
    enrollmentGraceDays: 7
    totp:
      digits: 6
      periodSeconds: 30
      algorithm: SHA1
      driftWindow: 1
      issuer: "Ember"                     # shown in authenticator app
    backupCodes:
      count: 10
      format: "BLOCK"                     # xxxx-xxxx-xxxx, [A-Z0-9]
    webauthn:
      rpId: ""                            # default: host of api.baseUrl
      rpName: ""                          # default: instance name
      attestation: "none"                 # "none" | "indirect" | "direct"
      timeoutSeconds: 60
      requireResidentKey: false
```

All overridable via env (`AUTH_TWOFACTOR_*`) using the existing `@Overwrite(env = @Env)` pattern. Hard upper bounds on `trustedDeviceMaxDays` (30) and `enrollmentGraceDays` (7) are enforced in the getter — operators cannot raise them past the cap.

## 12. Federation interaction

- 2FA enrollment never travels across instances. Account creation via federated invites still happens through email + password; the new instance computes its own 2FA mandate per its policies.
- Step-up category `FEDERATION` covers every user-facing federation action. The server-to-server `/remote/` surface is RSA-signed and out of the 2FA model entirely.
- Member-portal endpoints that aggregate cross-station data (federated KB browse, federated event list) do **not** trigger step-up — they're reads.

## 13. Rollout

### Done

1. **Schema + config + framework wiring.**
   - `patch_17.sql`: all tables (`account_2fa_factor`, `account_2fa_totp`, `account_2fa_webauthn`, `account_2fa_backup_code`, `account_2fa_trusted_device`, `two_factor_policy`, `account_2fa_audit`), enums (`two_factor_kind`, `two_factor_event`), `account_session` extended with `two_factor_verified_at` + `device_trust_id`.
   - Dependencies: `dev.samstevens.totp:totp:1.7.1`, `com.google.zxing:core:3.5.3` + `:javase`, `com.yubico:webauthn-server-core:2.7.0`.
   - `TwoFactorSettings` config class with TOTP, backup codes, and WebAuthn sub-configs. Hard caps enforced on `trustedDeviceMaxDays` (30) and `enrollmentGraceDays` (7).
   - Entities: `TwoFactorKind`, `TwoFactorEvent`, `TwoFactorFactor`, `TotpFactor`, `BackupCode`, `TrustedDevice`, `TwoFactorAuditEntry`.
   - `TwoFactorRepository`: CRUD for all 2FA tables.
   - `TotpService`: secret generation, AES-GCM encryption/decryption at rest, QR PNG rendering, code verification.
   - `BackupCodeService`: code generation (`xxxx-xxxx-xxxx` format), bcrypt hashing, verification.
   - `TwoFactorAuditService`: structured audit logging.
   - `TokenType.TWO_FACTOR_PENDING` added, `AccountSession.twoFactorVerifiedAt` field added.
   - Data tracking updated for all new tables.

2. **TOTP enroll/verify, backup codes, recovery codes.**
   - `TwoFactorService`: orchestrates enrollment (begin/confirm), TOTP verification, backup code verification, factor removal, backup code regeneration.
   - `TwoFactorRoutes`: `GET /account/2fa/status`, `POST /account/2fa/totp/begin`, `POST /account/2fa/totp/confirm`, `POST /account/2fa/totp/remove`, `POST /account/2fa/backup-codes/regenerate`, `POST /auth/2fa` (pre-auth token verification → session creation).
   - `AuthService.login()` modified: if user has an active TOTP factor, returns `TWO_FACTOR_PENDING` pre-auth token (5 min TTL) instead of a session. `createSessionForAccount()` added for post-2FA session creation.
   - `LoginResult` and `LoginResponse` extended with `twoFactorRequired`, `preAuthToken`, `preAuthTokenExpiresAt`.
   - Frontend: `TwoFactorVerifyView` (post-login 2FA challenge page with TOTP + backup code toggle), `TwoFactorSection` (settings component with QR enrollment wizard, backup code display, factor removal), `SecurityView` + page + route under `/station/profile/settings/security`.
   - `LoginView` handles `twoFactorRequired` response → redirects to `/2fa-verify`.
   - Sidebar link under profile settings.
   - Full de-DE localization.

3. **Step-up middleware.**
   - `StepUpCategory` enum (`ACCOUNT_SECURITY`, `FEDERATION`, `INSTANCE_CONFIG`, `ROLE_CHANGE`) implementing `RouteRole`.
   - `UserSession.twoFactorVerifiedAt` + `UserSession.sessionId` populated from `AccountSession`.
   - `ApiServer.handleAccess()` enforces step-up freshness against `auth.twoFactor.stepUpFreshnessSeconds`; users with no 2FA enrolled are exempt so the gate doesn't block them from setting up 2FA.
   - `StepUpRequiredException` → `401 {error:"step_up_required", category}` with `X-StepUp-Required` header.
   - `POST /auth/2fa/stepup` (LOGIN): verifies TOTP or backup code, updates `account_session.two_factor_verified_at`, records `STEPUP_VERIFIED`.
   - `ACCOUNT_SECURITY` applied to: `/auth/change-password`, `/account/2fa/totp/remove`, `/account/2fa/backup-codes/regenerate`, `/session/invalidate-all`.
   - Frontend: dedicated `/2fa-stepup` route + `StepUpVerifyView` (not a modal). Axios interceptor detects `step_up_required` 401 and redirects with `?redirect=<orig>&category=...`; view navigates back on success.
   - Help center: `help-profile-security` article + sidebar link.

4. **WebAuthn enroll/verify.**
   - `WebAuthnCredential` entity + repository CRUD (`createWebAuthn`, `findWebAuthnByCredentialId`, `findActiveWebAuthnForAccount`, `findUserHandleForAccount`, `findAccountByUserHandle`, `updateWebAuthnSignatureCounter`, generic `renameFactor`). Postgres `TEXT[]` and `UUID` columns read/written via SADU's first-class `row.getList` + `bind(..., PostgreSqlTypes.X)` and `StandardValueConverter.UUID_STRING`.
   - `WebAuthnCredentialStore` implements Yubico's `CredentialRepository` against the DB (accounts identified by stringified id; user handles stable per account).
   - `WebAuthnRelyingPartyFactory` builds the singleton `RelyingParty` from `auth.twoFactor.webauthn` + `api.baseUrl` (rpId defaults to baseUrl host); `allowOriginPort(true)` for local dev parity.
   - `WebAuthnService.startRegistration / finishRegistration / startAssertion / finishAssertion` — challenge state parked in `account_token` (`TWO_FACTOR_WEBAUTHN_REG`, `TWO_FACTOR_WEBAUTHN_ASSERT`) so the verifier stays stateless. First WebAuthn enrollment auto-seeds backup codes via `TwoFactorService.issueInitialBackupCodesIfMissing`. Generic `removeFactor` disables a factor and auto-disables the backup-code factor when no primary factors remain.
   - Routes (account-side, step-up gated by `ACCOUNT_SECURITY`): `POST /account/2fa/webauthn/register/begin|finish`, `POST /account/2fa/factors/{id}/remove`, `POST /account/2fa/factors/{id}/rename`. Login: public `POST /auth/2fa/webauthn/begin|finish` consume the existing `TWO_FACTOR_PENDING` pre-auth token. Step-up: `POST /auth/2fa/stepup/webauthn/begin|finish` updates `account_session.two_factor_verified_at`. All paths record audit events with `factor_kind = WEBAUTHN`.
   - Frontend: `util/webauthn.ts` bridges Yubico server JSON (URL-safe base64) to `navigator.credentials` and back. `WebAuthnSection.vue` lists, renames, removes, and enrolls security keys (label prompt + auto-issued backup codes modal). `TwoFactorVerifyView` and `StepUpVerifyView` expose a "Use security key" option alongside TOTP / backup code. German strings under `twoFactor.webauthn.*`.
   - Demo mode: status endpoint returns `webauthnAvailable: false`, `handleDemoGuard` rejects `/account/2fa/webauthn/register/*` defensively, and the UI hides the option.

### Remaining

5. **Policy engine.** `TwoFactorPolicyService` with `resolve(accountId)` → `RequirementState` sealed interface. Caffeine cache (60s TTL). Role-based mandate from elevated permission set. `X-Two-Factor-Deadline` header. `TwoFactorBanner` frontend component. Forced enrollment flow.
6. **Per-type force UI.** `TwoFactorAdminRoutes` for station + instance admin policy management. `SecurityView` for station manage + admin panel. Per-user-type toggles, member 2FA status list.
7. **Trusted device cookie.** `createTrustedDevice()` + `validateTrustedDevice()`. Login flow checks `ember_2fa_trust` cookie. `Set-Cookie` on `POST /auth/2fa` with `rememberDeviceDays`. Trusted devices list + revoke UI.
8. **Admin reset + audit log viewer.** `POST /admin/accounts/{id}/2fa/reset` (step-up required). Disables all factors, marks backup codes used, revokes sessions/devices, sends email. Paginated audit log viewer in admin panel.
9. **Step-up on remaining categories.** `FEDERATION` on pair/unpair/key-rotate/sharing-config. `INSTANCE_CONFIG` on instance settings/module toggle/storage/mail/legal. `ROLE_CHANGE` on permission grant/revoke/member-type change.
10. **Help center articles + default-on.** Articles for 2FA setup, backup codes, security keys, step-up, admin reset. Flip `twoFactor.enabled` default to `true`.

Each step is independently shippable. Steps 1–4 deliver a usable opt-in flow; steps 5–8 deliver the mandate; step 9 closes the post-login attack surface; step 10 is polish + default-on.

## 14. Deferred / future work

- **Passwordless / passkey login.** Same WebAuthn credentials with `requireResidentKey = true` and a "Sign in with passkey" button on the login page. Not in this scope.
- **Push-based authenticators (Duo-style).** Vendor lock-in, infra cost; revisit only if customers ask.
- **SSO / external IdP.** Different shape — the 2FA story merges with the IdP's MFA, this concept assumes Ember owns the credential.
- **Per-station max grace override.** Today only the instance admin sets the cap; could let station admins choose anything ≤ the cap for their own population.
- **Cryptographic device-binding for the trusted-device token.** Currently a bcrypt'd random cookie; could move to attested device IDs (TPM, Secure Enclave) if the threat model demands it.
- **Adaptive risk** ("require 2FA for this login because the IP looks weird"). Useful but well-known to be a long tail of false positives; out of scope for v1.
