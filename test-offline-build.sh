#!/bin/bash

# Offline Build Testing Script
# Tests build capabilities without requiring network connectivity

echo "🔧 Language-TranslaterRT Offline Build Test"
echo "============================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Set up test environment
echo "📝 Setting up test environment..."
export STRIPE_PUBLISHABLE_KEY="pk_test_demo_key_for_testing"
export PRO_MONTHLY_PRICE_ID="price_demo_monthly_testing"
export PRO_ANNUAL_PRICE_ID="price_demo_annual_testing"
export BACKEND_BASE_URL="https://api.demo.com/v1"

print_status() {
    if [ "$1" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
    elif [ "$1" = "FAIL" ]; then
        echo -e "${RED}❌ FAIL${NC}: $2"
    elif [ "$1" = "WARN" ]; then
        echo -e "${YELLOW}⚠️  WARN${NC}: $2"
    elif [ "$1" = "INFO" ]; then
        echo -e "${BLUE}ℹ️  INFO${NC}: $2"
    fi
}

# Test 1: Basic file structure
echo "📁 Testing project structure..."
if [ -f "build.gradle" ] && [ -f "app/build.gradle" ] && [ -f "gradlew" ]; then
    print_status "PASS" "Essential build files present"
else
    print_status "FAIL" "Missing essential build files"
fi

# Test 2: Gradle wrapper permissions
echo "🔧 Testing Gradle wrapper..."
if [ -x "gradlew" ]; then
    print_status "PASS" "Gradle wrapper is executable"
else
    print_status "WARN" "Gradle wrapper not executable, fixing..."
    chmod +x gradlew
fi

# Test 3: Test Gradle version (offline)
echo "📋 Testing Gradle configuration..."
if ./gradlew --version --offline 2>/dev/null | grep -q "Gradle"; then
    print_status "PASS" "Gradle wrapper works offline"
else
    print_status "INFO" "Gradle offline mode not available (first run needed)"
fi

# Test 4: Build file validation
echo "🔍 Validating build configuration..."

# Check for required build configurations
if grep -q "applicationId" app/build.gradle; then
    print_status "PASS" "Application ID configured"
else
    print_status "FAIL" "Application ID missing"
fi

if grep -q "versionCode" app/build.gradle; then
    print_status "PASS" "Version code configured"
else
    print_status "FAIL" "Version code missing"
fi

if grep -q "buildConfigField.*STRIPE" app/build.gradle; then
    print_status "PASS" "Stripe configuration present"
else
    print_status "FAIL" "Stripe configuration missing"
fi

if grep -q "buildConfigField.*DEMO_MODE" app/build.gradle; then
    print_status "PASS" "Demo mode configuration present"
else
    print_status "FAIL" "Demo mode configuration missing"
fi

# Test 5: Source code structure
echo "📝 Testing source code structure..."
MAIN_FILES=$(find app/src/main -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)
TEST_FILES=$(find app/src/test -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)

if [ "$MAIN_FILES" -gt 0 ]; then
    print_status "PASS" "Main source files found ($MAIN_FILES files)"
else
    print_status "FAIL" "No main source files found"
fi

if [ "$TEST_FILES" -gt 0 ]; then
    print_status "PASS" "Test files found ($TEST_FILES files)"
else
    print_status "WARN" "No test files found"
fi

# Test 6: Key source files
echo "🔑 Testing key source files..."
KEY_FILES=(
    "app/src/main/java/com/mllinman/languagetranslator/MainActivity.kt"
    "app/src/main/java/com/mllinman/languagetranslator/TranslationService.kt"
    "app/src/main/java/com/mllinman/languagetranslator/SubscriptionManager.kt"
)

for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        print_status "PASS" "$(basename "$file") exists"
    else
        print_status "WARN" "$(basename "$file") missing"
    fi
done

# Test 7: Android manifest
echo "📱 Testing Android configuration..."
MANIFEST_PATH="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST_PATH" ]; then
    print_status "PASS" "AndroidManifest.xml exists"
    
    # Check for required permissions
    if grep -q "RECORD_AUDIO" "$MANIFEST_PATH"; then
        print_status "PASS" "Audio recording permission declared"
    else
        print_status "WARN" "Audio recording permission missing"
    fi
    
    if grep -q "INTERNET" "$MANIFEST_PATH"; then
        print_status "PASS" "Internet permission declared"
    else
        print_status "WARN" "Internet permission missing"
    fi
else
    print_status "FAIL" "AndroidManifest.xml missing"
fi

# Test 8: Resources
echo "🎨 Testing app resources..."
if [ -d "app/src/main/res" ]; then
    print_status "PASS" "Resources directory exists"
    
    LAYOUT_COUNT=$(find app/src/main/res/layout -name "*.xml" 2>/dev/null | wc -l)
    if [ "$LAYOUT_COUNT" -gt 0 ]; then
        print_status "PASS" "Layout files found ($LAYOUT_COUNT layouts)"
    else
        print_status "WARN" "No layout files found"
    fi
    
    if [ -f "app/src/main/res/values/strings.xml" ]; then
        print_status "PASS" "String resources exist"
    else
        print_status "WARN" "String resources missing"
    fi
else
    print_status "FAIL" "Resources directory missing"
fi

# Test 9: ProGuard configuration
echo "🛡️  Testing security configuration..."
if [ -f "app/proguard-rules.pro" ]; then
    print_status "PASS" "ProGuard rules file exists"
    
    if grep -q "stripe" app/proguard-rules.pro 2>/dev/null; then
        print_status "PASS" "Stripe ProGuard rules present"
    else
        print_status "INFO" "Consider adding Stripe-specific ProGuard rules"
    fi
else
    print_status "WARN" "ProGuard rules file missing"
fi

# Test 10: Deployment scripts
echo "🚀 Testing deployment readiness..."
SCRIPTS=("validate-production.sh" "setup-backend.sh" "test-deployment.sh")

for script in "${SCRIPTS[@]}"; do
    if [ -f "$script" ]; then
        if [ -x "$script" ]; then
            print_status "PASS" "$script is executable"
        else
            print_status "WARN" "$script exists but not executable"
            chmod +x "$script"
        fi
    else
        print_status "INFO" "$script not found (optional)"
    fi
done

# Test 11: CI/CD workflows
echo "⚙️  Testing CI/CD configuration..."
if [ -d ".github/workflows" ]; then
    WORKFLOW_FILES=$(find .github/workflows -name "*.yml" -o -name "*.yaml" 2>/dev/null | wc -l)
    if [ "$WORKFLOW_FILES" -gt 0 ]; then
        print_status "PASS" "GitHub Actions workflows present ($WORKFLOW_FILES files)"
    else
        print_status "WARN" "No workflow files found in .github/workflows"
    fi
else
    print_status "INFO" "No .github/workflows directory (optional for local deployment)"
fi

# Test 12: Documentation
echo "📚 Testing documentation..."
DOC_FILES=("README.md" "DEPLOYMENT_GUIDE.md" "MONETIZATION_GUIDE.md")

for doc in "${DOC_FILES[@]}"; do
    if [ -f "$doc" ] && [ -s "$doc" ]; then
        print_status "PASS" "$doc exists and has content"
    else
        print_status "WARN" "$doc missing or empty"
    fi
done

# Test 13: Git configuration
echo "📦 Testing version control..."
if [ -d ".git" ]; then
    print_status "PASS" "Git repository initialized"
    
    if [ -f ".gitignore" ]; then
        print_status "PASS" ".gitignore file exists"
        
        # Check for important ignore patterns
        if grep -q "build/" .gitignore 2>/dev/null; then
            print_status "PASS" "Build directory ignored"
        else
            print_status "WARN" "Build directory not ignored"
        fi
        
        if grep -q "*.jks" .gitignore 2>/dev/null || grep -q "keystore" .gitignore 2>/dev/null; then
            print_status "PASS" "Keystore files ignored"
        else
            print_status "WARN" "Keystore files not ignored"
        fi
    else
        print_status "WARN" ".gitignore file missing"
    fi
else
    print_status "INFO" "Not a Git repository (or .git missing)"
fi

echo ""
echo "🎯 Offline Build Test Complete"
echo "==============================="
echo ""
print_status "INFO" "All tests completed without requiring network connectivity"
print_status "INFO" "For full build testing, run: ./test-deployment.sh"
print_status "INFO" "For production deployment: ./validate-production.sh"
echo ""