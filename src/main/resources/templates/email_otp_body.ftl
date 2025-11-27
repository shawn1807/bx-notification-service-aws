<#-- One-time code verification email body -->
<html>
  <body style="font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6;">
    <p>${i18n("otp.email.greeting", userName!i18n("customer"))}</p>
    <p>${i18n("otp.email.intro", appName)}</p>
    <p style="font-size: 20px; font-weight: bold; letter-spacing: 3px; margin: 16px 0;">
      ${code}
    </p>
    <p>${i18n("otp.email.expiry", expiryMinutes?c)}</p>
    <p>${i18n("otp.email.notice")}</p>
    <p style="margin-top: 24px;">${i18n("otp.email.signature", appName)}</p>
  </body>
</html>
