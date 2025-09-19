#!/bin/bash

# Production Configuration Validation Script
# Run this before deploying to production

echo "🔍 Language Translator Production Configuration Validator"
echo "========================================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# Function to print status
print_status() {
    if [ "$1" = "ERROR" ]; then
        echo -e "${RED}❌ $2${NC}"
        ((ERRORS++))
    elif [ "$1" = "WARNING" ]; then
        echo -e "${YELLOW}⚠️  $2${NC}"
        ((WARNINGS++))
    elif [ "$1" = "SUCCESS" ]; then
        echo -e "${GREEN}✅ $2${NC}"
    else
        echo "ℹ️  $2"
    fi
}

# Check environment variables
echo "🔧 Environment Variables Check"
echo "------------------------------"

if [ -z "$STRIPE_PUBLISHABLE_KEY" ]; then
    print_status "ERROR" "STRIPE_PUBLISHABLE_KEY environment variable not set"
else
    if [[ $STRIPE_PUBLISHABLE_KEY == pk_live_* ]]; then
        print_status "SUCCESS" "Live Stripe publishable key configured"
    elif [[ $STRIPE_PUBLISHABLE_KEY == pk_test_* ]]; then
        print_status "WARNING" "Test Stripe key detected - ensure this is intentional"
    else
        print_status "ERROR" "Invalid Stripe publishable key format"
    fi
fi

if [ -z "$PRO_MONTHLY_PRICE_ID" ]; then
    print_status "ERROR" "PRO_MONTHLY_PRICE_ID environment variable not set"
else
    if [[ $PRO_MONTHLY_PRICE_ID == price_* ]]; then
        print_status "SUCCESS" "Monthly price ID configured"
    else
        print_status "ERROR" "Invalid monthly price ID format"
    fi
fi

if [ -z "$PRO_ANNUAL_PRICE_ID" ]; then
    print_status "ERROR" "PRO_ANNUAL_PRICE_ID environment variable not set"
else
    if [[ $PRO_ANNUAL_PRICE_ID == price_* ]]; then
        print_status "SUCCESS" "Annual price ID configured"
    else
        print_status "ERROR" "Invalid annual price ID format"
    fi
fi

if [ -z "$BACKEND_BASE_URL" ]; then
    print_status "ERROR" "BACKEND_BASE_URL environment variable not set"
else
    if [[ $BACKEND_BASE_URL == https://* ]]; then
        print_status "SUCCESS" "Backend URL uses HTTPS"
    else
        print_status "ERROR" "Backend URL must use HTTPS for production"
    fi
fi

echo ""

# Check build configuration
echo "🏗️  Build Configuration Check"
echo "-----------------------------"

if [ -f "app/build.gradle" ]; then
    print_status "SUCCESS" "Build configuration file found"
    
    # Check if buildConfig is enabled
    if grep -q "buildConfig true" app/build.gradle; then
        print_status "SUCCESS" "BuildConfig feature enabled"
    else
        print_status "ERROR" "BuildConfig feature not enabled"
    fi
    
    # Check if release build type exists
    if grep -q "release {" app/build.gradle; then
        print_status "SUCCESS" "Release build type configured"
        
        # Check if minification is enabled
        if grep -A 10 "release {" app/build.gradle | grep -q "minifyEnabled true"; then
            print_status "SUCCESS" "Code minification enabled for release"
        else
            print_status "WARNING" "Code minification not enabled - recommended for production"
        fi
    else
        print_status "ERROR" "Release build type not configured"
    fi
else
    print_status "ERROR" "Build configuration file not found"
fi

echo ""

# Check security configuration
echo "🔒 Security Configuration Check"
echo "-------------------------------"

if [ -f "app/proguard-rules.pro" ]; then
    print_status "SUCCESS" "ProGuard rules file found"
    
    if grep -q "stripe" app/proguard-rules.pro; then
        print_status "SUCCESS" "Stripe-specific ProGuard rules configured"
    else
        print_status "WARNING" "Stripe ProGuard rules not found"
    fi
else
    print_status "WARNING" "ProGuard rules file not found"
fi

if [ -f ".gitignore" ]; then
    if grep -q "*.jks" .gitignore && grep -q "*.keystore" .gitignore; then
        print_status "SUCCESS" "Keystore files excluded from version control"
    else
        print_status "ERROR" "Keystore files not excluded from version control"
    fi
    
    if grep -q "production.properties" .gitignore; then
        print_status "SUCCESS" "Production configuration files excluded"
    else
        print_status "WARNING" "Production configuration files not excluded"
    fi
else
    print_status "WARNING" ".gitignore file not found"
fi

echo ""

# Check dependencies
echo "📦 Dependencies Check"
echo "--------------------"

if [ -f "app/build.gradle" ]; then
    if grep -q "stripe-android" app/build.gradle; then
        print_status "SUCCESS" "Stripe Android SDK included"
    else
        print_status "ERROR" "Stripe Android SDK not found in dependencies"
    fi
    
    if grep -q "okhttp3" app/build.gradle; then
        print_status "SUCCESS" "OkHttp HTTP client included"
    else
        print_status "WARNING" "OkHttp HTTP client not found - needed for backend communication"
    fi
    
    if grep -q "kotlinx-serialization" app/build.gradle; then
        print_status "SUCCESS" "Kotlin serialization included"
    else
        print_status "WARNING" "Kotlin serialization not found"
    fi
    
    if grep -q "security-crypto" app/build.gradle; then
        print_status "SUCCESS" "Android Security library included"
    else
        print_status "WARNING" "Android Security library not found"
    fi
fi

echo ""

# Check source code configuration
echo "💻 Source Code Check"
echo "-------------------"

if [ -f "app/src/main/java/com/mllinman/languagetranslator/SubscriptionManager.kt" ]; then
    print_status "SUCCESS" "SubscriptionManager found"
    
    if grep -q "BuildConfig.DEMO_MODE" app/src/main/java/com/mllinman/languagetranslator/SubscriptionManager.kt; then
        print_status "SUCCESS" "Using BuildConfig for demo mode"
    else
        print_status "WARNING" "Demo mode configuration not using BuildConfig"
    fi
    
    if grep -q "BuildConfig.STRIPE_PUBLISHABLE_KEY" app/src/main/java/com/mllinman/languagetranslator/SubscriptionManager.kt; then
        print_status "SUCCESS" "Using BuildConfig for Stripe key"
    else
        print_status "ERROR" "Stripe key not configured via BuildConfig"
    fi
else
    print_status "ERROR" "SubscriptionManager not found"
fi

if [ -f "app/src/main/java/com/mllinman/languagetranslator/api/ApiClient.kt" ]; then
    print_status "SUCCESS" "ApiClient found"
else
    print_status "WARNING" "ApiClient not found - needed for backend communication"
fi

echo ""

# Final summary
echo "📊 Validation Summary"
echo "===================="

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    print_status "SUCCESS" "All checks passed! Ready for production deployment."
    echo ""
    echo "🚀 Next steps:"
    echo "  1. Build release APK: ./gradlew assembleRelease"
    echo "  2. Test payment flow thoroughly"
    echo "  3. Deploy backend using setup-backend.sh"
    echo "  4. Monitor analytics and error rates"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    print_status "WARNING" "$WARNINGS warning(s) found. Review before deployment."
    echo ""
    echo "⚠️  Consider addressing warnings for optimal production setup."
    exit 0
else
    print_status "ERROR" "$ERRORS error(s) and $WARNINGS warning(s) found."
    echo ""
    echo "❌ Please fix all errors before deploying to production."
    echo ""
    echo "🔧 Common fixes:"
    echo "  - Set environment variables: export STRIPE_PUBLISHABLE_KEY=pk_live_..."
    echo "  - Update build.gradle configuration"
    echo "  - Configure security settings"
    echo "  - Add missing dependencies"
    exit 1
fi