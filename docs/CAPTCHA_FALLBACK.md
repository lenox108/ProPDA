# Captcha fallback UX (X-03)

> What happens when 4PDA serves a Google reCAPTCHA challenge instead of
> the requested page, and how the app surfaces that to the user.

## End-to-end flow

1. **Server side.** A 4PDA endpoint returns a reCAPTCHA page (HTML body,
   status 200). The body does not match any of the parsers' root
   patterns.
2. **Client detection.** `client/ErrorInterceptor.kt` recognizes the
   response, throws `client/GoogleCaptchaException(val pageContent: String)`.
3. **Per-feature mapping.**
   - **QMS chat open** — `QmsOpenErrorReason.CaptchaPage` →
     `QmsLoadErrorKind.CAPTCHA` → `R.string.qms_error_captcha`
     toast/banner with a "Solve captcha" action that opens
     `Screen.Captcha()` (`ui/navigation/TabHelper.kt`,
     `ui/fragments/other/GoogleCaptchaFragment.kt`).
   - **News / reputation / auth** — each domain has its own
     `*HtmlValidator` (`model/data/remote/api/news/ArticleHtmlValidator.kt`,
     `model/data/remote/api/reputation/ReputationHtmlValidator.kt`) that
     maps the same condition to its domain-specific error reason and
     string.
4. **Captcha solver.** `GoogleCaptchaFragment` runs the WebView through
   the standard Google captcha UI and posts the resulting token back to
   the failing request via the in-app cookie / header plumbing.

## Known sharp edges

- If the captcha token expires before the user submits the form
  (typical: 2 minutes), the next API call will throw
  `GoogleCaptchaException` again. The retry loop must surface a
  "captcha expired" error rather than auto-retry silently.
- The captcha page is loaded from `https://www.google.com/recaptcha/`,
  which **fails** if the user is behind a firewall that blocks Google.
  This is a known limitation; see S-07 (mixed content) for the related
  security review.
- `GoogleCaptchaException.pageContent` keeps the HTML body in memory
  until it is consumed. The screen must drop the reference as soon as
  the captcha is solved, otherwise it leaks the WebView-associated
  string across configuration changes.

## QA checklist for a captcha regression

Run on a real device that triggers captcha (or stub the
`GoogleCaptchaException` in a debug menu):

- [ ] QMS chat open → "captcha" error appears within 1 second of the
      network call returning.
- [ ] Tapping the action opens `GoogleCaptchaFragment` with the
      captcha UI loaded.
- [ ] Solving the captcha and pressing "continue" returns to QMS chat
      and the next API call succeeds.
- [ ] Closing `GoogleCaptchaFragment` without solving does **not**
      retry the original request.
- [ ] `GoogleCaptchaException.pageContent` is GC-eligible after the
      captcha screen is destroyed (check via heap dump if LeakCanary
      is enabled).
