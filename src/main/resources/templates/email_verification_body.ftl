<!DOCTYPE html>
<html>
<body style="margin:0; padding:0; background-color:#f6f8fa; font-family:Arial, Helvetica, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f6f8fa; padding:40px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; padding:40px;">

          <tr>
            <td align="center" style="font-size:24px; font-weight:700; padding-bottom:10px;">
              ${appName}
            </td>
          </tr>

          <tr>
            <td align="center" style="font-size:20px; font-weight:600; padding-bottom:20px;">
              ${i18n("email.verify.title")}
            </td>
          </tr>

          <tr>
            <td style="font-size:16px; padding-bottom:10px;">
              ${i18n("email.verify.greeting", userName)}
            </td>
          </tr>

          <tr>
            <td style="font-size:15px; line-height:1.6; color:#333333; padding-bottom:20px;">
              ${i18n("email.verify.message", appName)}
            </td>
          </tr>

          <tr>
            <td align="center" style="padding:20px 0;">
              <a href="${verifyUrl}"
                 style="background:#0d6efd; color:white; text-decoration:none;
                        padding:14px 28px; border-radius:6px; font-size:16px;">
                ${i18n("email.verify.button")}
              </a>
            </td>
          </tr>

          <tr>
            <td style="font-size:14px; line-height:1.6; color:#666666; padding-top:20px;">
              ${i18n("email.verify.alt")}<br><br>
              <a href="${verifyUrl}" style="color:#0d6efd; word-break:break-all;">
                ${verifyUrl}
              </a>
            </td>
          </tr>

          <tr>
            <td style="font-size:13px; line-height:1.6; color:#999999; padding-top:30px;">
              ${i18n("email.verify.notice", appName)}
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>
