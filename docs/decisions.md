# Decisions log

Small decisions made during the build, so they can be revisited.

## Phase 1

- **Package / folder:** Java package `app.medreminder`; project lives in `medicine-reminder/`.
- **Spring Boot 3.5.x**, compiled with `--release 21` (works on any JDK 21+).
- **No Lombok**, to keep the build simple on new JDKs.
- **Rate limiting** is a small in-memory per-IP filter (fixed one-minute window). Fine for one server;
  replace with Bucket4j + Redis if we run more than one instance.
- **JWT lifetime** defaults to 7 days (`JWT_TTL`). No refresh tokens yet.
- **Email magic link** is not built yet: it needs an email-sending service (SMTP). Email + password works now.
- **Fonts:** the font stack lists Inter then all Noto Sans fonts, so the browser picks the right one per character.
  Mixed text (an English screen showing a Telugu medicine name) renders correctly.
- **Tailwind v4** (CSS-first `@theme` tokens) instead of a `tailwind.config.js`.
- **Elder screens** use the person's voice language, not the family member's app language
  (fixed to Telugu until pairing exists).
