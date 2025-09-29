#!/bin/bash

# Comprehensive Deployment Testing Script for Language-TranslaterRT
# This script tests the entire deployment pipeline and validates configuration

echo "🧪 Language-TranslaterRT Deployment Testing Suite"
echo "================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0
TESTS_RUN=0

# Function to print test results
print_result() {
    ((TESTS_RUN++))
    if [ "$1" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
    elif [ "$1" = "FAIL" ]; then
        echo -e "${RED}❌ FAIL${NC}: $2"
        ((ERRORS++))
    elif [ "$1" = "WARN" ]; then
        echo -e "${YELLOW}⚠️  WARN${NC}: $2"
        ((WARNINGS++))
    elif [ "$1" = "INFO" ]; then
        echo -e "${BLUE}ℹ️  INFO${NC}: $2"
    fi
}

# Test 1: Repository Structure
echo "📁 Testing Repository Structure"
echo "--------------------------------"

if [ -f "build.gradle" ]; then
    print_result "PASS" "Root build.gradle exists"
else
    print_result "FAIL" "Root build.gradle missing"
fi

if [ -f "app/build.gradle" ]; then
    print_result "PASS" "App build.gradle exists"
else
    print_result "FAIL" "App build.gradle missing"
fi

if [ -f "gradlew" ] && [ -x "gradlew" ]; then
    print_result "PASS" "Gradle wrapper is executable"
else
    print_result "FAIL" "Gradle wrapper missing or not executable"
    chmod +x gradlew 2>/dev/null
fi

if [ -d "app/src/main/java" ]; then
    print_result "PASS" "Main source directory exists"
else
    print_result "FAIL" "Main source directory missing"
fi

if [ -d "app/src/test/java" ]; then
    print_result "PASS" "Test source directory exists"
else
    print_result "WARN" "Test source directory missing"
fi

echo ""

# Test 2: Configuration Files
echo "⚙️  Testing Configuration Files"
echo "--------------------------------"

if [ -f ".github/workflows/build-and-test.yml" ]; then
    print_result "PASS" "CI/CD workflow file exists"
else
    print_result "WARN" "CI/CD workflow file missing"
fi

if [ -f ".github/workflows/production-release.yml" ]; then
    print_result "PASS" "Production release workflow exists"
else
    print_result "WARN" "Production release workflow missing"
fi

if [ -f "validate-production.sh" ] && [ -x "validate-production.sh" ]; then
    print_result "PASS" "Production validation script exists"
else
    print_result "WARN" "Production validation script missing or not executable"
fi

if [ -f "setup-backend.sh" ] && [ -x "setup-backend.sh" ]; then
    print_result "PASS" "Backend setup script exists"
else
    print_result "WARN" "Backend setup script missing or not executable"
fi

echo ""

# Test 3: Environment Configuration
echo "🌍 Testing Environment Configuration"
echo "------------------------------------"

# Check if environment variables are set for production testing
if [ -n "$STRIPE_PUBLISHABLE_KEY" ]; then
    if [[ $STRIPE_PUBLISHABLE_KEY == pk_live_* ]]; then
        print_result "PASS" "Live Stripe key configured"
    elif [[ $STRIPE_PUBLISHABLE_KEY == pk_test_* ]]; then
        print_result "WARN" "Test Stripe key configured (use live key for production)"
    else
        print_result "FAIL" "Invalid Stripe key format"
    fi
else
    print_result "INFO" "STRIPE_PUBLISHABLE_KEY not set (expected for testing)"
fi

if [ -n "$PRO_MONTHLY_PRICE_ID" ]; then
    print_result "PASS" "Monthly price ID configured"
else
    print_result "INFO" "PRO_MONTHLY_PRICE_ID not set (expected for testing)"
fi

if [ -n "$BACKEND_BASE_URL" ]; then
    print_result "PASS" "Backend URL configured"
else
    print_result "INFO" "BACKEND_BASE_URL not set (expected for testing)"
fi

echo ""

# Test 4: Build Configuration Validation
echo "🔧 Testing Build Configuration"
echo "-------------------------------"

# Test Gradle version compatibility
if ./gradlew --version >/dev/null 2>&1; then
    print_result "PASS" "Gradle wrapper works"
    GRADLE_VERSION=$(./gradlew --version | grep "Gradle" | head -1 | awk '{print $2}')
    print_result "INFO" "Using Gradle version: $GRADLE_VERSION"
else
    print_result "FAIL" "Gradle wrapper not working"
fi

# Check for proper Android SDK setup (simulate)
if [ -n "$ANDROID_HOME" ] || [ -n "$ANDROID_SDK_ROOT" ]; then
    print_result "PASS" "Android SDK environment configured"
else
    print_result "WARN" "Android SDK environment not configured"
fi

# Test BuildConfig validation
if grep -q "buildConfigField.*STRIPE_PUBLISHABLE_KEY" app/build.gradle; then
    print_result "PASS" "Stripe configuration in build.gradle"
else
    print_result "FAIL" "Stripe configuration missing in build.gradle"
fi

if grep -q "buildConfigField.*DEMO_MODE" app/build.gradle; then
    print_result "PASS" "Demo mode configuration found"
else
    print_result "FAIL" "Demo mode configuration missing"
fi

echo ""

# Test 5: Security Configuration
echo "🔒 Testing Security Configuration"
echo "----------------------------------"

if grep -q "minifyEnabled true" app/build.gradle; then
    print_result "PASS" "ProGuard/R8 obfuscation enabled"
else
    print_result "WARN" "Code obfuscation not enabled"
fi

if grep -q "shrinkResources true" app/build.gradle; then
    print_result "PASS" "Resource shrinking enabled"
else
    print_result "WARN" "Resource shrinking not enabled"
fi

if [ -f "app/proguard-rules.pro" ]; then
    print_result "PASS" "ProGuard rules file exists"
else
    print_result "WARN" "ProGuard rules file missing"
fi

# Check for hardcoded secrets (basic scan)
if grep -r "pk_live_" app/src/ 2>/dev/null | grep -v "BuildConfig" | grep -q .; then
    print_result "FAIL" "Hardcoded live keys found in source code"
else
    print_result "PASS" "No hardcoded live keys in source code"
fi

echo ""

# Test 6: Simulate Build Process
echo "🏗️  Testing Build Process (Simulation)"
echo "---------------------------------------"

# Create mock environment for testing
export STRIPE_PUBLISHABLE_KEY="${STRIPE_PUBLISHABLE_KEY:-pk_test_demo_key_for_testing}"
export PRO_MONTHLY_PRICE_ID="${PRO_MONTHLY_PRICE_ID:-price_demo_monthly_testing}"
export PRO_ANNUAL_PRICE_ID="${PRO_ANNUAL_PRICE_ID:-price_demo_annual_testing}"
export BACKEND_BASE_URL="${BACKEND_BASE_URL:-https://api.demo.com/v1}"

print_result "INFO" "Using test environment variables for build simulation"

# Test gradle tasks
if ./gradlew tasks --all >/dev/null 2>&1; then
    print_result "PASS" "Gradle tasks enumeration works"
else
    print_result "WARN" "Gradle tasks enumeration failed (network/dependency issue)"
fi

# Test clean task
if ./gradlew clean -q 2>/dev/null; then
    print_result "PASS" "Gradle clean task works"
else
    print_result "WARN" "Gradle clean task failed"
fi

echo ""

# Test 7: Deployment Artifacts
echo "📦 Testing Deployment Artifacts"
echo "--------------------------------"

# Check for necessary deployment files
DEPLOYMENT_FILES=("DEPLOYMENT_GUIDE.md" "MONETIZATION_GUIDE.md" "PRODUCTION_CONFIG.md" "README.md")

for file in "${DEPLOYMENT_FILES[@]}"; do
    if [ -f "$file" ]; then
        print_result "PASS" "$file exists"
    else
        print_result "WARN" "$file missing"
    fi
done

# Check workflow files
if [ -d ".github/workflows" ]; then
    WORKFLOW_COUNT=$(find .github/workflows -name "*.yml" | wc -l)
    if [ "$WORKFLOW_COUNT" -gt 0 ]; then
        print_result "PASS" "GitHub Actions workflows present ($WORKFLOW_COUNT files)"
    else
        print_result "WARN" "No GitHub Actions workflows found"
    fi
else
    print_result "WARN" ".github/workflows directory missing"
fi

echo ""

# Test 8: Backend Deployment Testing
echo "🚀 Testing Backend Deployment Scripts"
echo "--------------------------------------"

if [ -f "setup-backend.sh" ]; then
    # Test if script is syntactically correct
    if bash -n setup-backend.sh 2>/dev/null; then
        print_result "PASS" "Backend setup script syntax is valid"
    else
        print_result "FAIL" "Backend setup script has syntax errors"
    fi
    
    # Check for required environment variables in script
    if grep -q "STRIPE_SECRET_KEY" setup-backend.sh; then
        print_result "PASS" "Backend script includes Stripe configuration"
    else
        print_result "WARN" "Backend script missing Stripe configuration"
    fi
fi

echo ""

# Test 9: Code Quality Checks
echo "🧹 Testing Code Quality"
echo "------------------------"

# Count Kotlin/Java files
KOTLIN_FILES=$(find app/src -name "*.kt" | wc -l)
JAVA_FILES=$(find app/src -name "*.java" | wc -l)

print_result "INFO" "Found $KOTLIN_FILES Kotlin files and $JAVA_FILES Java files"

if [ "$KOTLIN_FILES" -gt 0 ] || [ "$JAVA_FILES" -gt 0 ]; then
    print_result "PASS" "Source code files present"
else
    print_result "FAIL" "No source code files found"
fi

# Check for tests
TEST_FILES=$(find app/src -path "*/test/*" -name "*.kt" -o -path "*/test/*" -name "*.java" | wc -l)
if [ "$TEST_FILES" -gt 0 ]; then
    print_result "PASS" "Test files present ($TEST_FILES tests)"
else
    print_result "WARN" "No test files found"
fi

echo ""

# Test 10: Documentation Completeness
echo "📚 Testing Documentation"
echo "-------------------------"

if [ -f "README.md" ] && [ -s "README.md" ]; then
    README_LINES=$(wc -l < README.md)
    if [ "$README_LINES" -gt 50 ]; then
        print_result "PASS" "Comprehensive README.md ($README_LINES lines)"
    else
        print_result "WARN" "README.md is quite short"
    fi
else
    print_result "FAIL" "README.md missing or empty"
fi

# Check for monetization documentation
if grep -q "monetization\|subscription\|stripe" README.md 2>/dev/null; then
    print_result "PASS" "Monetization documentation present in README"
else
    print_result "WARN" "Monetization documentation missing in README"
fi

echo ""

# Final Summary
echo "📊 Test Results Summary"
echo "======================="
echo ""
echo -e "Tests Run: ${BLUE}$TESTS_RUN${NC}"
echo -e "Passed: ${GREEN}$((TESTS_RUN - ERRORS - WARNINGS))${NC}"
echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"
echo -e "Errors: ${RED}$ERRORS${NC}"
echo ""

if [ "$ERRORS" -eq 0 ] && [ "$WARNINGS" -lt 5 ]; then
    echo -e "${GREEN}🎉 DEPLOYMENT READY${NC}: The project is ready for deployment!"
    exit 0
elif [ "$ERRORS" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  DEPLOYMENT POSSIBLE${NC}: Minor warnings present, but deployable."
    exit 0
else
    echo -e "${RED}❌ DEPLOYMENT BLOCKED${NC}: Please fix errors before deploying."
    exit 1
fi