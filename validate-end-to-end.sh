#!/bin/bash

# End-to-End Deployment Validation Script
# Comprehensive testing of the entire deployment pipeline

echo "🚀 Language-TranslaterRT End-to-End Deployment Validation"
echo "=========================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
WARNINGS=0

# Function to print test results with counters
print_test_result() {
    ((TOTAL_TESTS++))
    if [ "$1" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
        ((PASSED_TESTS++))
    elif [ "$1" = "FAIL" ]; then
        echo -e "${RED}❌ FAIL${NC}: $2"
        ((FAILED_TESTS++))
    elif [ "$1" = "WARN" ]; then
        echo -e "${YELLOW}⚠️  WARN${NC}: $2"
        ((WARNINGS++))
    elif [ "$1" = "INFO" ]; then
        echo -e "${BLUE}ℹ️  INFO${NC}: $2"
    elif [ "$1" = "STEP" ]; then
        echo -e "${PURPLE}🔄 STEP${NC}: $2"
    fi
}

# Phase 1: Pre-deployment Validation
echo -e "${PURPLE}━━━ PHASE 1: PRE-DEPLOYMENT VALIDATION ━━━${NC}"
echo ""

print_test_result "STEP" "Validating repository structure and configuration"

# Test repository integrity
if [ -f "build.gradle" ] && [ -f "app/build.gradle" ] && [ -f "gradlew" ] && [ -x "gradlew" ]; then
    print_test_result "PASS" "Essential build files are present and configured"
else
    print_test_result "FAIL" "Missing or misconfigured essential build files"
fi

# Test Android project structure
if [ -d "app/src/main/java" ] && [ -f "app/src/main/AndroidManifest.xml" ]; then
    print_test_result "PASS" "Android project structure is valid"
else
    print_test_result "FAIL" "Invalid Android project structure"
fi

# Test key source files
KEY_FILES=(
    "app/src/main/java/com/mllinman/languagetranslator/MainActivity.kt"
    "app/src/main/java/com/mllinman/languagetranslator/TranslationService.kt"
    "app/src/main/java/com/mllinman/languagetranslator/SubscriptionManager.kt"
    "app/src/main/java/com/mllinman/languagetranslator/SubscriptionActivity.kt"
    "app/src/main/java/com/mllinman/languagetranslator/utils/SecureConfigManager.kt"
)

MISSING_FILES=0
for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        print_test_result "PASS" "$(basename "$file") exists"
    else
        print_test_result "FAIL" "$(basename "$file") missing"
        ((MISSING_FILES++))
    fi
done

if [ "$MISSING_FILES" -eq 0 ]; then
    print_test_result "PASS" "All key source files are present"
fi

# Phase 2: Security and Configuration Validation
echo ""
echo -e "${PURPLE}━━━ PHASE 2: SECURITY AND CONFIGURATION ━━━${NC}"
echo ""

print_test_result "STEP" "Validating security configuration and build settings"

# Check for hardcoded secrets
if grep -r "pk_live_" app/src/ 2>/dev/null | grep -v "BuildConfig\|REPLACE" | grep -q .; then
    print_test_result "FAIL" "Hardcoded live API keys found in source code"
else
    print_test_result "PASS" "No hardcoded live API keys in source code"
fi

# Test ProGuard configuration
if grep -q "minifyEnabled true" app/build.gradle; then
    print_test_result "PASS" "Code obfuscation enabled for release builds"
else
    print_test_result "WARN" "Code obfuscation not enabled - consider enabling for security"
fi

if [ -f "app/proguard-rules.pro" ]; then
    if grep -q "stripe" app/proguard-rules.pro 2>/dev/null; then
        print_test_result "PASS" "Stripe-specific ProGuard rules configured"
    else
        print_test_result "WARN" "Consider adding Stripe-specific ProGuard rules"
    fi
else
    print_test_result "WARN" "ProGuard rules file missing"
fi

# Test build configuration
if grep -q "buildConfigField.*DEMO_MODE" app/build.gradle; then
    print_test_result "PASS" "Demo mode configuration present"
else
    print_test_result "FAIL" "Demo mode configuration missing"
fi

if grep -q "buildConfigField.*STRIPE_PUBLISHABLE_KEY" app/build.gradle; then
    print_test_result "PASS" "Stripe configuration in build.gradle"
else
    print_test_result "FAIL" "Stripe configuration missing in build.gradle"
fi

# Phase 3: CI/CD and Automation Validation
echo ""
echo -e "${PURPLE}━━━ PHASE 3: CI/CD AND AUTOMATION ━━━${NC}"
echo ""

print_test_result "STEP" "Validating CI/CD workflows and automation scripts"

# Test GitHub Actions workflows
WORKFLOW_FILES=(
    ".github/workflows/build-and-test.yml"
    ".github/workflows/production-release.yml"
    ".github/workflows/build.yml"
)

WORKFLOW_COUNT=0
for workflow in "${WORKFLOW_FILES[@]}"; do
    if [ -f "$workflow" ]; then
        print_test_result "PASS" "$(basename "$workflow") workflow exists"
        ((WORKFLOW_COUNT++))
        
        # Basic workflow validation
        if grep -q "jobs:" "$workflow" && grep -q "runs-on:" "$workflow"; then
            print_test_result "PASS" "$(basename "$workflow") has valid job structure"
        else
            print_test_result "WARN" "$(basename "$workflow") may have structural issues"
        fi
    else
        print_test_result "WARN" "$(basename "$workflow") workflow missing"
    fi
done

if [ "$WORKFLOW_COUNT" -gt 0 ]; then
    print_test_result "PASS" "GitHub Actions workflows are configured ($WORKFLOW_COUNT workflows)"
fi

# Test deployment scripts
SCRIPTS=("validate-production.sh" "setup-backend.sh" "test-deployment.sh")
SCRIPT_COUNT=0

for script in "${SCRIPTS[@]}"; do
    if [ -f "$script" ]; then
        if [ -x "$script" ]; then
            print_test_result "PASS" "$script is present and executable"
            ((SCRIPT_COUNT++))
            
            # Basic syntax check
            if bash -n "$script" 2>/dev/null; then
                print_test_result "PASS" "$script has valid bash syntax"
            else
                print_test_result "FAIL" "$script has syntax errors"
            fi
        else
            print_test_result "WARN" "$script exists but is not executable"
            chmod +x "$script"
        fi
    else
        print_test_result "WARN" "$script missing"
    fi
done

# Phase 4: Testing Infrastructure
echo ""
echo -e "${PURPLE}━━━ PHASE 4: TESTING INFRASTRUCTURE ━━━${NC}"
echo ""

print_test_result "STEP" "Validating testing infrastructure and capabilities"

# Test source code structure for testing
TEST_FILES=$(find app/src -path "*/test/*" -name "*.kt" -o -path "*/test/*" -name "*.java" 2>/dev/null | wc -l)
if [ "$TEST_FILES" -gt 0 ]; then
    print_test_result "PASS" "Unit test files present ($TEST_FILES test files)"
else
    print_test_result "WARN" "No unit test files found - consider adding tests"
fi

# Test dependencies for testing
if grep -q "testImplementation.*junit" app/build.gradle; then
    print_test_result "PASS" "JUnit testing framework configured"
else
    print_test_result "WARN" "JUnit testing framework not configured"
fi

if grep -q "androidTestImplementation.*espresso" app/build.gradle; then
    print_test_result "PASS" "Espresso UI testing framework configured"
else
    print_test_result "WARN" "Espresso UI testing framework not configured"
fi

# Test offline build capabilities
print_test_result "STEP" "Testing offline build capabilities"

export STRIPE_PUBLISHABLE_KEY="pk_test_demo_key_for_testing"
export PRO_MONTHLY_PRICE_ID="price_demo_monthly_testing"
export PRO_ANNUAL_PRICE_ID="price_demo_annual_testing"
export BACKEND_BASE_URL="https://api.demo.com/v1"

if ./gradlew --version --offline >/dev/null 2>&1; then
    print_test_result "PASS" "Gradle works in offline mode"
else
    print_test_result "INFO" "Gradle offline mode not available (normal for first run)"
fi

# Phase 5: Documentation and Compliance
echo ""
echo -e "${PURPLE}━━━ PHASE 5: DOCUMENTATION AND COMPLIANCE ━━━${NC}"
echo ""

print_test_result "STEP" "Validating documentation and compliance requirements"

# Test documentation completeness
DOC_FILES=(
    "README.md"
    "DEPLOYMENT_GUIDE.md" 
    "MONETIZATION_GUIDE.md"
    "PRODUCTION_CONFIG.md"
    "IMPLEMENTATION_ROADMAP.md"
)

DOC_COUNT=0
for doc in "${DOC_FILES[@]}"; do
    if [ -f "$doc" ] && [ -s "$doc" ]; then
        print_test_result "PASS" "$doc exists and has content"
        ((DOC_COUNT++))
        
        # Check documentation quality
        LINES=$(wc -l < "$doc")
        if [ "$LINES" -gt 20 ]; then
            print_test_result "PASS" "$doc is comprehensive ($LINES lines)"
        else
            print_test_result "WARN" "$doc is quite short ($LINES lines)"
        fi
    else
        print_test_result "WARN" "$doc missing or empty"
    fi
done

# Test for monetization documentation
if grep -qi "monetization\|subscription\|stripe" README.md 2>/dev/null; then
    print_test_result "PASS" "Monetization information present in documentation"
else
    print_test_result "WARN" "Monetization information missing from main documentation"
fi

# Test Git configuration
if [ -f ".gitignore" ]; then
    print_test_result "PASS" ".gitignore file exists"
    
    # Check for important ignore patterns
    IGNORE_PATTERNS=("build/" "*.jks" "*.keystore" ".gradle/" "local.properties")
    IGNORED_COUNT=0
    
    for pattern in "${IGNORE_PATTERNS[@]}"; do
        if grep -q "$pattern" .gitignore 2>/dev/null; then
            ((IGNORED_COUNT++))
        fi
    done
    
    if [ "$IGNORED_COUNT" -ge 3 ]; then
        print_test_result "PASS" "Essential files/directories are ignored ($IGNORED_COUNT/5)"
    else
        print_test_result "WARN" "Some important files may not be ignored ($IGNORED_COUNT/5)"
    fi
else
    print_test_result "WARN" ".gitignore file missing"
fi

# Phase 6: Production Readiness Assessment
echo ""
echo -e "${PURPLE}━━━ PHASE 6: PRODUCTION READINESS ━━━${NC}"
echo ""

print_test_result "STEP" "Assessing production readiness"

# Check Android manifest for production requirements
MANIFEST_PATH="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST_PATH" ]; then
    # Check required permissions
    REQUIRED_PERMS=("RECORD_AUDIO" "INTERNET")
    PERM_COUNT=0
    
    for perm in "${REQUIRED_PERMS[@]}"; do
        if grep -q "$perm" "$MANIFEST_PATH"; then
            ((PERM_COUNT++))
        fi
    done
    
    if [ "$PERM_COUNT" -eq "${#REQUIRED_PERMS[@]}" ]; then
        print_test_result "PASS" "All required permissions are declared"
    else
        print_test_result "WARN" "Some required permissions may be missing ($PERM_COUNT/${#REQUIRED_PERMS[@]})"
    fi
    
    # Check for debug-specific configurations
    if grep -q "android:debuggable.*true" "$MANIFEST_PATH"; then
        print_test_result "WARN" "Debuggable flag should be removed for production"
    else
        print_test_result "PASS" "No debug-specific configurations in manifest"
    fi
else
    print_test_result "FAIL" "AndroidManifest.xml missing"
fi

# Check version configuration
VERSION_CODE=$(grep "versionCode" app/build.gradle | head -1 | grep -o '[0-9]\+')
VERSION_NAME=$(grep "versionName" app/build.gradle | head -1 | grep -o '"[^"]*"' | tr -d '"')

if [ -n "$VERSION_CODE" ] && [ "$VERSION_CODE" -gt 0 ]; then
    print_test_result "PASS" "Version code is configured ($VERSION_CODE)"
else
    print_test_result "FAIL" "Version code not properly configured"
fi

if [ -n "$VERSION_NAME" ] && [ "$VERSION_NAME" != "1.0" ]; then
    print_test_result "PASS" "Version name is configured ($VERSION_NAME)"
else
    print_test_result "WARN" "Version name should be updated from default"
fi

# Final Assessment
echo ""
echo -e "${PURPLE}━━━ DEPLOYMENT READINESS ASSESSMENT ━━━${NC}"
echo ""

# Calculate success rate
SUCCESS_RATE=0
if [ "$TOTAL_TESTS" -gt 0 ]; then
    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
fi

echo "📊 Test Results Summary:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "Total Tests: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"
echo -e "Success Rate: ${BLUE}$SUCCESS_RATE%${NC}"
echo ""

# Deployment recommendation
if [ "$FAILED_TESTS" -eq 0 ] && [ "$SUCCESS_RATE" -ge 90 ]; then
    echo -e "${GREEN}🎉 DEPLOYMENT APPROVED${NC}"
    echo "The project is ready for production deployment!"
    echo ""
    echo "Next Steps:"
    echo "1. Set production environment variables"
    echo "2. Run './validate-production.sh' with production config"
    echo "3. Generate signed APK with './gradlew assembleRelease'"
    echo "4. Deploy backend with './setup-backend.sh'"
    echo "5. Test end-to-end functionality"
    EXIT_CODE=0
elif [ "$FAILED_TESTS" -eq 0 ] && [ "$WARNINGS" -lt 10 ]; then
    echo -e "${YELLOW}⚠️  DEPLOYMENT CONDITIONAL${NC}"
    echo "The project can be deployed with minor improvements recommended."
    echo ""
    echo "Consider addressing the warnings before production deployment."
    EXIT_CODE=0
else
    echo -e "${RED}❌ DEPLOYMENT NOT RECOMMENDED${NC}"
    echo "Please fix the failed tests before proceeding with deployment."
    echo ""
    echo "Critical issues must be resolved before production release."
    EXIT_CODE=1
fi

echo ""
echo "For detailed deployment instructions, see DEPLOYMENT_GUIDE.md"
echo ""

exit $EXIT_CODE