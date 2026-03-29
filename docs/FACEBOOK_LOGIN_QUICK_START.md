# Facebook Login - Quick Setup Checklist

## ✅ What's Done (Backend & Frontend)
- [x] OAuth2 configuration in application.yml
- [x] Spring Security OAuth2 client registration
- [x] Facebook provider endpoints configured
- [x] Login page with Facebook button
- [x] Registration page with Facebook button
- [x] OAuth2 success/failure handlers
- [x] Account auto-creation and linking

## 📋 What You Need to Do (3 Steps)

### Step 1: Create Facebook App
```
1. Go to https://developers.facebook.com/
2. Create new app → type "Consumer"
3. Add "Facebook Login" product
4. Copy App ID and App Secret from Settings → Basic
```

### Step 2: Configure Redirect URI in Facebook
```
Valid OAuth Redirect URIs:
  https://<your-domain>/Game/login/oauth2/code/facebook

Examples:
  http://localhost:8080/Game/login/oauth2/code/facebook
  https://game.example.com/Game/login/oauth2/code/facebook
```

### Step 3: Set Environment Variables
```env
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_ID=YOUR_APP_ID
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_CLIENT_SECRET=YOUR_APP_SECRET
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_FACEBOOK_SCOPE=public_profile,email
```

**Where to set:**
- `.env.public.local` (file-based)
- Docker environment variables
- System environment variables
- Container secrets

## 🔍 How to Verify It Works

1. **Check variables are loaded:**
   ```powershell
   # In your app startup logs, should see configured providers
   ```

2. **Visit login page:**
   ```
   https://<your-domain>/Game/account/login-page
   ```

3. **Facebook button should:**
   - ✅ Be enabled (not grayed out)
   - ✅ Be clickable
   - ✅ Redirect to Facebook login

4. **Test login:**
   - Click Facebook button
   - Authenticate with Facebook account
   - Should be redirected back and logged in

## ❌ Troubleshooting

| Issue | Solution |
|-------|----------|
| Facebook button is disabled | Check: Env variables set? App restarted? |
| "Invalid redirect URI" | Add full path `/Game/login/oauth2/code/facebook` to Facebook app |
| Error after Facebook login | Check app logs for account creation issues |
| Email field empty | Check Facebook app permissions include "email" |

## 📚 Full Documentation
See [FACEBOOK_LOGIN_SETUP.md](FACEBOOK_LOGIN_SETUP.md) for complete guide with troubleshooting and technical details.

## 🔗 Useful Links
- [Facebook Developers Console](https://developers.facebook.com/apps/)
- [Facebook Login Docs](https://developers.facebook.com/docs/facebook-login/)
- Spring Security OAuth2: Built-in, no additional setup needed
