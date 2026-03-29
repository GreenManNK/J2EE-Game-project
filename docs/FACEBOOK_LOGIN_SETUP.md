# Facebook Login Setup Guide

## Overview
This document provides step-by-step instructions to enable Facebook OAuth2 login functionality for the J2EE Game application.

## Current Status
✅ **Backend Configuration:** Complete - All code and YAML configuration is in place
✅ **Frontend UI:** Complete - Login and registration pages have Facebook buttons
⏳ **Setup Required:** You need to configure Facebook App credentials

## Step 1: Create Facebook App

### 1.1 Go to Facebook Developers Console
1. Visit [Facebook Developers](https://developers.facebook.com/)
2. Log in with your Facebook account (create one if needed)

### 1.2 Create a New App
1. Click "My Apps" → "Create App"
2. Choose "Consumer" as the app type
3. Fill in app details:
   - **App Name:** Game Hub (or your preferred name)
   - **App Contact Email:** Your email
   - **App Purpose:** Choose "Manage Business Integrations" or similar

### 1.3 Set Up Facebook Login
1. In the app dashboard, click "Add Product"
2. Find "Facebook Login" and click "Set Up"
3. Choose the platform appropriate for your setup (typically "Web")

## Step 2: Configure App Settings

### 2.1 Get Credentials
1. Go to **Settings → Basic** in your app dashboard
2. Copy your:
   - **App ID** (Facebook Client ID)
   - **App Secret** (Facebook Client Secret)
   - ⚠️ **Keep the App Secret secure** - never commit to version control

### 2.2 Set Callback/Redirect URIs
1. Go to **Facebook Login → Settings**
2. Under "Valid OAuth Redirect URIs", add your callback URL:
   ```
   https://<your-domain>/Game/login/oauth2/code/facebook
   ```
   
   **Examples:**
   - Local development: `http://localhost:8080/Game/login/oauth2/code/facebook`
   - Production: `https://yourgame.com/Game/login/oauth2/code/facebook`
   - Tunnel: `https://your-tunnel-url.trycloudflare.com/Game/login/oauth2/code/facebook`

3. Add your main domain to **App Domains**:
   ```
   localhost
   yourgame.com
   your-tunnel-url.trycloudflare.com
   etc.
   ```

## Step 3: Configure Application Environment Variables

### 3.1 Using Environment File (.env.local)
Copy the `.env.public.example` to `.env.public.local` and add:

```env
# Facebook OAuth2 Configuration
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID=YOUR_FACEBOOK_APP_ID
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET=YOUR_FACEBOOK_APP_SECRET
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_SCOPE=public_profile,email
```

### 3.2 Using System Environment Variables
Set in your system/container environment:
```powershell
# PowerShell
$env:SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID = "YOUR_APP_ID"
$env:SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET = "YOUR_APP_SECRET"
```

### 3.3 Using Docker Environment
If using Docker, add to your `docker-compose.yml` or Docker environment:
```yaml
environment:
  SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID: YOUR_APP_ID
  SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET: YOUR_APP_SECRET
```

## Step 4: Verify Configuration

### 4.1 Check Backend Configuration
The application automatically detects Facebook configuration when:
- Both `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID` and `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET` are set
- The values are non-empty strings

### 4.2 Test the Feature
1. Start the application
2. Navigate to the login page: `https://<your-domain>/Game/account/login-page`
3. The Facebook button should appear (enabled, not grayed out)
4. Click the Facebook button to test the login flow

### 4.3 Troubleshooting
If the Facebook button appears disabled:
- ❌ Check that environment variables are set correctly
- ❌ Verify variables are loaded before the app starts
- ❌ Check application logs for any configuration errors
- ❌ Ensure no typos in environment variable names

## Step 5: Additional Configuration (Optional)

### 5.1 Customize Scopes
By default, the app requests:
- `public_profile` (basic Facebook profile info)
- `email` (user's email address)

To request additional permissions:
```env
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_SCOPE=public_profile,email,user_birthday
```

[Facebook Login Permissions Reference](https://developers.facebook.com/docs/facebook-login/permissions)

### 5.2 Override Endpoint URLs (Advanced)
If you need to use a different Facebook API version:
```env
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_FACEBOOK_AUTHORIZATION_URI=https://www.facebook.com/v18.0/dialog/oauth
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_FACEBOOK_TOKEN_URI=https://graph.facebook.com/v18.0/oauth/access_token
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_FACEBOOK_USER_INFO_URI=https://graph.facebook.com/me?fields=id,name,email
```

## Technical Details

### How It Works
1. **User clicks Facebook button** on login page
2. **Redirects to Facebook** for authentication (via `/oauth2/authorization/facebook`)
3. **Facebook verifies credentials** and redirects back to callback URL
4. **Application exchanges code for access token** with Facebook's servers
5. **Application retrieves user info** (name, email, ID)
6. **Account is created or linked** - auto-registration or linking to existing account
7. **User is logged in** and redirected to game

### Callback Flow
```
User Login Page
    ↓
Click "Dang nhap voi Facebook"
    ↓
POST /oauth2/authorization/facebook
    ↓
Redirect to Facebook (authorization-uri)
    ↓
User grants permissions on Facebook
    ↓
Facebook redirects to: /Game/login/oauth2/code/facebook?code=...&state=...
    ↓
Exchange code for token (token-uri)
    ↓
Get user info (user-info-uri)
    ↓
Create or link account
    ↓
Set user session
    ↓
Redirect to /account/oauth2-success or home page
```

### Security
- Client secret is never exposed to the frontend
- CSRF protection is enabled via SessionCreationPolicy
- PKCE flow is supported by Spring Security OAuth2
- Tokens are stored securely in the session

## Related Files

### Frontend Templates
- [Login Page](../src/main/frontend/templates/account/login.html) - Facebook button in social section
- [Register Page](../src/main/frontend/templates/account/register.html) - Quick registration option
- [OAuth2 Success Page](../src/main/frontend/templates/account/oauth2-success.html) - Landing after login

### Backend Configuration
- [Application Configuration](../src/main/backend/resources/application.yml) - OAuth2 provider and client registration
- [Security Config](../src/main/backend/java/com/game/hub/config/SecurityConfig.java) - Spring Security OAuth2 setup
- [Social Login Config](../src/main/backend/java/com/game/hub/config/SocialLoginConfiguration.java) - Provider status checking
- [OAuth2 Success Handler](../src/main/backend/java/com/game/hub/config/OAuth2LoginSuccessHandler.java) - Post-auth processing
- [Account Service](../src/main/backend/java/com/game/hub/service/AccountService.java) - Account creation/linking logic

## Troubleshooting

### Facebook Button Not Showing
**Problem:** Facebook login button appears disabled/grayed out on login page

**Solution:**
1. Verify environment variables are set: `echo $SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID`
2. Restart the application
3. Check Docker logs if using containers: `docker logs <container-id>`
4. Check app logs for "SocialLoginConfiguration" messages

### "Invalid OAuth Redirect URI"
**Problem:** Error message: "The provided redirect URI is not registered with the app"

**Solution:**
1. Verify your exact domain/URL from browser address bar
2. Add the **full** redirect URI to Facebook app settings including path: `/Game/login/oauth2/code/facebook`
3. For local development with tunnel, use the tunnel URL, not localhost

### "User not found" or "Login required"
**Problem:** After successful Facebook login, see error messages

**Solution:**
1. Check `OAuth2LoginSuccessHandler` logs for account creation errors
2. Verify database connection is working
3. Check user permissions if account already exists
4. See account linking settings in `/account/settings`

### Email Not Provided by Facebook
**Problem:** Email field is empty after login

**Solution:**
1. In Facebook App settings → Facebook Login → Permissions, check that `email` is requested
2. Verify `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_SCOPE` includes `email`
3. User must have a confirmed email in their Facebook account

## FAQ

**Q: What data does the app get from Facebook?**
A: By default: ID, name, and email. The app uses this to auto-create an account or link to existing account.

**Q: Can users link Facebook to existing account?**
A: Yes, from `/account/settings` users can link/unlink social accounts to their existing email account.

**Q: Is the app secret secure?**
A: Yes, the client secret never leaves the backend server. It's only used for server-to-server communication with Facebook.

**Q: Can I use Facebook login without email?**
A: The current implementation requires email for account creation. You can customize the `OAuth2LoginSuccessHandler` if needed.

**Q: Does Facebook login work offline?**
A: No, Facebook login requires internet connection to verify with Facebook's servers.

## Support & References

- [Spring Security OAuth2 Documentation](https://spring.io/projects/spring-security-oauth2-client)
- [Facebook Login Documentation](https://developers.facebook.com/docs/facebook-login/)
- [Facebook App Settings](https://developers.facebook.com/apps/)
- Project Issue Tracker: [GitHub Issues](https://github.com/your-repo/issues)

---

**Last Updated:** 2026-03-28  
**Maintained By:** Development Team
