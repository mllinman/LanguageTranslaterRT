# Production Deployment Guide

This guide covers the complete process of deploying the Language Translator app to production.

## 🚀 Quick Start Production Deployment

### Prerequisites

1. **Stripe Account Setup**
   - Create a Stripe account at https://stripe.com
   - Complete business verification for live payments
   - Get your live API keys from https://dashboard.stripe.com/apikeys

2. **Backend Deployment**
   - Use the provided `setup-backend.sh` script for one-click deployment
   - Alternatively deploy to Heroku, Railway, or DigitalOcean

3. **Android Signing Certificate**
   - Generate a release keystore for signing your APK
   - Store securely for consistent releases

## 📋 Step-by-Step Production Setup

### Step 1: Configure Environment Variables

Set these environment variables for production builds:

```bash
# Required for production
export STRIPE_PUBLISHABLE_KEY="pk_live_your_actual_live_key"
export PRO_MONTHLY_PRICE_ID="price_your_monthly_price_id"
export PRO_ANNUAL_PRICE_ID="price_your_annual_price_id"
export BACKEND_BASE_URL="https://your-production-api.com/v1"

# For automated builds (GitHub Actions)
# Set these as repository secrets:
# STRIPE_PUBLISHABLE_KEY_PROD
# PRO_MONTHLY_PRICE_ID_PROD
# PRO_ANNUAL_PRICE_ID_PROD
# BACKEND_BASE_URL_PROD
```

### Step 2: Generate Signing Certificate

```bash
# Generate keystore for app signing
keytool -genkey -v -keystore release-keystore.jks \
    -alias release-key \
    -keyalg RSA \
    -keysize 2048 \
    -validity 25000

# Store keystore and passwords securely
# For GitHub Actions, encode keystore as base64:
base64 -i release-keystore.jks | pbcopy
```

### Step 3: Build Production APK

#### Local Build
```bash
# Clean build
./gradlew clean

# Build signed release APK
./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file=release-keystore.jks \
    -Pandroid.injected.signing.store.password=YOUR_KEYSTORE_PASSWORD \
    -Pandroid.injected.signing.key.alias=release-key \
    -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

#### Automated Build (GitHub Actions)
- Push to main branch triggers automatic builds
- Use workflow dispatch for manual releases
- Artifacts are automatically uploaded

### Step 4: Deploy Backend

#### Option A: Quick Deployment with setup-backend.sh
```bash
# Make script executable
chmod +x setup-backend.sh

# Run setup (creates and deploys backend)
./setup-backend.sh

# Follow prompts to configure Stripe keys
```

#### Option B: Manual Deployment

**Heroku:**
```bash
heroku create your-translator-backend
heroku config:set STRIPE_SECRET_KEY=sk_live_your_key
heroku config:set STRIPE_WEBHOOK_SECRET=whsec_your_secret
git push heroku main
```

**Railway:**
```bash
railway login
railway init
railway add
railway deploy
```

**DigitalOcean App Platform:**
1. Connect GitHub repository
2. Set environment variables in dashboard
3. Deploy automatically

### Step 5: Configure App Store

#### Google Play Store
1. Create developer account
2. Generate app bundle: `./gradlew bundleRelease`
3. Upload AAB file
4. Configure store listing
5. Set up in-app products (alternative to Stripe)

#### Alternative Distribution
- Direct APK distribution
- F-Droid (for open source version)
- Samsung Galaxy Store
- Amazon Appstore

## 🔧 Configuration Management

### Build Variants

The app supports multiple build variants:

- **Debug**: Demo mode enabled, test keys, debug logging
- **Release**: Production mode, live keys, optimized

### Environment Configuration

Production configuration is managed through:

1. **BuildConfig fields** (recommended)
2. **Environment variables**
3. **Secure SharedPreferences** (runtime configuration)

### Security Considerations

✅ **Implemented:**
- Encrypted SharedPreferences for sensitive data
- BuildConfig-based configuration
- ProGuard obfuscation for release builds
- Secure HTTP client with certificate pinning ready

🔄 **Additional Options:**
- Certificate pinning for API calls
- Root detection
- Debug detection
- Tamper detection

## 📊 Monitoring and Analytics

### Built-in Monitoring

The app includes:
- Usage analytics tracking
- Error logging and reporting
- Subscription conversion metrics
- Performance monitoring

### External Monitoring Setup

#### Firebase (Recommended)
```gradle
// Add to app/build.gradle
implementation 'com.google.firebase:firebase-analytics-ktx:21.5.0'
implementation 'com.google.firebase:firebase-crashlytics-ktx:18.6.1'
```

#### Sentry (Alternative)
```gradle
// Add to app/build.gradle
implementation 'io.sentry:sentry-android:7.0.0'
```

## 🚨 Pre-Production Checklist

### Configuration Validation
- [ ] DEMO_MODE set to false
- [ ] Live Stripe keys configured
- [ ] Backend URL points to production
- [ ] Signing certificate configured
- [ ] ProGuard rules tested

### Security Audit
- [ ] No hardcoded secrets in code
- [ ] Encrypted storage for sensitive data
- [ ] HTTPS enforced for all API calls
- [ ] Input validation implemented
- [ ] Error messages don't leak sensitive info

### Performance Testing
- [ ] APK size optimized (< 10MB recommended)
- [ ] App startup time < 3 seconds
- [ ] Memory usage profiled
- [ ] Network requests optimized
- [ ] Battery usage tested

### User Experience
- [ ] All subscription flows tested
- [ ] Payment processing validated
- [ ] Error handling graceful
- [ ] Offline functionality works
- [ ] Accessibility features enabled

### Legal Compliance
- [ ] Privacy policy updated
- [ ] Terms of service current
- [ ] GDPR compliance implemented
- [ ] App store guidelines followed
- [ ] Payment regulations complied with

## 🔄 Continuous Deployment

### GitHub Actions Workflow

The repository includes automated workflows for:

1. **Continuous Integration**
   - Unit tests
   - Lint checks
   - Security scanning
   - Build validation

2. **Production Release**
   - Automated APK building and signing
   - Backend deployment
   - Release creation
   - Artifact distribution

### Release Process

1. **Development**
   - Create feature branch
   - Implement changes
   - Create pull request

2. **Testing**
   - Automated tests run
   - Manual QA testing
   - Performance validation

3. **Release**
   - Merge to main branch
   - Trigger production workflow
   - Deploy to app stores
   - Monitor metrics

## 💰 Monetization Tracking

### Key Metrics to Monitor

1. **Conversion Metrics**
   - Free to Pro conversion rate
   - Time to conversion
   - Subscription retention

2. **Usage Metrics**
   - Daily active users
   - Translation volume
   - Feature usage

3. **Revenue Metrics**
   - Monthly recurring revenue (MRR)
   - Customer lifetime value (CLV)
   - Average revenue per user (ARPU)

### Revenue Optimization

Based on the implementation guides:

- **Free tier**: 15 translations/day, 3/hour
- **Pro Monthly**: $9.99/month unlimited
- **Pro Annual**: $99.99/year (17% savings)

Expected revenue (conservative estimates):
- 1,000 users → 30 conversions → $299/month
- 10,000 users → 300 conversions → $2,997/month
- 100,000 users → 3,000 conversions → $29,970/month

## 🆘 Troubleshooting

### Common Issues

**Build Failures:**
- Check environment variables are set
- Verify keystore file exists and passwords correct
- Ensure all dependencies are available

**Payment Issues:**
- Verify Stripe keys match environment
- Check webhook endpoints are reachable
- Validate price IDs in Stripe dashboard

**Backend Issues:**
- Check server logs for errors
- Verify environment variables on server
- Test API endpoints manually

### Support Resources

- [Stripe Documentation](https://stripe.com/docs)
- [Android Deployment Guide](https://developer.android.com/studio/publish)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## 📞 Production Support

For production deployments, consider:

1. **24/7 Monitoring** - Set up alerts for downtime
2. **Customer Support** - Implement in-app support system
3. **Regular Updates** - Plan monthly feature releases
4. **Backup Strategy** - Ensure data redundancy
5. **Scaling Plan** - Prepare for user growth

---

**Ready to deploy?** Follow the steps above and your Language Translator app will be production-ready with a complete monetization system!