#!/bin/bash

# Simple Backend Setup Script for Language Translator Monetization
# This creates a basic Node.js/Express backend with Stripe integration

echo "🚀 Setting up Language Translator Backend for Monetization..."

# Create backend directory structure
mkdir -p language-translator-backend
cd language-translator-backend

# Initialize npm project
cat > package.json << 'EOF'
{
  "name": "language-translator-backend",
  "version": "1.0.0",
  "description": "Backend service for Language Translator app monetization",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "dev": "nodemon server.js",
    "test": "jest"
  },
  "dependencies": {
    "express": "^4.18.2",
    "stripe": "^14.14.0",
    "cors": "^2.8.5",
    "helmet": "^7.1.0",
    "dotenv": "^16.3.1",
    "sqlite3": "^5.1.6",
    "uuid": "^9.0.1",
    "express-rate-limit": "^7.1.5"
  },
  "devDependencies": {
    "nodemon": "^3.0.2",
    "jest": "^29.7.0"
  },
  "keywords": ["translation", "subscription", "stripe", "monetization"],
  "author": "Your Name",
  "license": "MIT"
}
EOF

# Create main server file
cat > server.js << 'EOF'
const express = require('express');
const stripe = require('stripe')(process.env.STRIPE_SECRET_KEY);
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// Security middleware
app.use(helmet());
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS?.split(',') || ['http://localhost:3000'],
  credentials: true
}));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
});
app.use('/api/', limiter);

// Body parser middleware
app.use('/webhook', express.raw({ type: 'application/json' }));
app.use(express.json());

// Database setup (SQLite for simplicity)
const Database = require('./database');
const db = new Database();

// Routes
app.get('/api/health', (req, res) => {
  res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Create Stripe checkout session
app.post('/api/create-checkout-session', async (req, res) => {
  try {
    const { userId, priceId, successUrl, cancelUrl } = req.body;
    
    // Validate required fields
    if (!userId || !priceId) {
      return res.status(400).json({ error: 'Missing required fields' });
    }
    
    // Create or get customer
    let customer = await db.getCustomerByUserId(userId);
    if (!customer) {
      const stripeCustomer = await stripe.customers.create({
        metadata: { userId: userId }
      });
      customer = await db.createCustomer(userId, stripeCustomer.id);
    }
    
    // Create checkout session
    const session = await stripe.checkout.sessions.create({
      customer: customer.stripeCustomerId,
      payment_method_types: ['card'],
      line_items: [{
        price: priceId,
        quantity: 1,
      }],
      mode: 'subscription',
      success_url: successUrl,
      cancel_url: cancelUrl,
      metadata: {
        userId: userId
      }
    });
    
    res.json({ 
      isSuccessful: true, 
      sessionId: session.id,
      url: session.url 
    });
    
  } catch (error) {
    console.error('Error creating checkout session:', error);
    res.status(500).json({ 
      isSuccessful: false, 
      error: 'Failed to create checkout session' 
    });
  }
});

// Get subscription status
app.get('/api/subscription/status/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    
    const subscription = await db.getUserSubscription(userId);
    
    res.json({
      isSuccessful: true,
      tier: subscription ? subscription.tier : 'FREE',
      subscriptionId: subscription?.subscriptionId,
      status: subscription?.status,
      currentPeriodEnd: subscription?.currentPeriodEnd
    });
    
  } catch (error) {
    console.error('Error getting subscription status:', error);
    res.status(500).json({ 
      isSuccessful: false, 
      error: 'Failed to get subscription status' 
    });
  }
});

// Cancel subscription
app.post('/api/subscription/cancel/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    
    const subscription = await db.getUserSubscription(userId);
    if (!subscription) {
      return res.status(404).json({ error: 'Subscription not found' });
    }
    
    // Cancel at period end to let user keep access
    await stripe.subscriptions.update(subscription.subscriptionId, {
      cancel_at_period_end: true
    });
    
    await db.updateSubscriptionStatus(userId, 'CANCELLED');
    
    res.json({ isSuccessful: true });
    
  } catch (error) {
    console.error('Error cancelling subscription:', error);
    res.status(500).json({ 
      isSuccessful: false, 
      error: 'Failed to cancel subscription' 
    });
  }
});

// Record usage analytics
app.post('/api/usage/record', async (req, res) => {
  try {
    const { userId, action, metadata } = req.body;
    
    await db.recordUsage(userId, action, metadata);
    
    res.json({ isSuccessful: true });
    
  } catch (error) {
    console.error('Error recording usage:', error);
    res.status(500).json({ 
      isSuccessful: false, 
      error: 'Failed to record usage' 
    });
  }
});

// Stripe webhook handler
app.post('/webhook/stripe', async (req, res) => {
  const sig = req.headers['stripe-signature'];
  let event;
  
  try {
    event = stripe.webhooks.constructEvent(req.body, sig, process.env.STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error('Webhook signature verification failed:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }
  
  // Handle the event
  switch (event.type) {
    case 'checkout.session.completed':
      const session = event.data.object;
      await handleCheckoutCompleted(session);
      break;
      
    case 'invoice.payment_succeeded':
      const invoice = event.data.object;
      await handlePaymentSucceeded(invoice);
      break;
      
    case 'invoice.payment_failed':
      const failedInvoice = event.data.object;
      await handlePaymentFailed(failedInvoice);
      break;
      
    case 'customer.subscription.deleted':
      const deletedSub = event.data.object;
      await handleSubscriptionDeleted(deletedSub);
      break;
      
    default:
      console.log(`Unhandled event type ${event.type}`);
  }
  
  res.json({ received: true });
});

// Webhook handlers
async function handleCheckoutCompleted(session) {
  try {
    const userId = session.metadata.userId;
    const subscription = await stripe.subscriptions.retrieve(session.subscription);
    
    const tier = getTierFromPriceId(subscription.items.data[0].price.id);
    
    await db.updateSubscription(userId, {
      subscriptionId: subscription.id,
      tier: tier,
      status: 'ACTIVE',
      currentPeriodEnd: new Date(subscription.current_period_end * 1000)
    });
    
    console.log(`Subscription activated for user ${userId}: ${tier}`);
  } catch (error) {
    console.error('Error handling checkout completed:', error);
  }
}

async function handlePaymentSucceeded(invoice) {
  try {
    const subscription = await stripe.subscriptions.retrieve(invoice.subscription);
    const customer = await stripe.customers.retrieve(subscription.customer);
    const userId = customer.metadata.userId;
    
    await db.updateSubscriptionStatus(userId, 'ACTIVE');
    
    console.log(`Payment succeeded for user ${userId}`);
  } catch (error) {
    console.error('Error handling payment succeeded:', error);
  }
}

async function handlePaymentFailed(invoice) {
  try {
    const subscription = await stripe.subscriptions.retrieve(invoice.subscription);
    const customer = await stripe.customers.retrieve(subscription.customer);
    const userId = customer.metadata.userId;
    
    await db.updateSubscriptionStatus(userId, 'PAYMENT_FAILED');
    
    console.log(`Payment failed for user ${userId}`);
  } catch (error) {
    console.error('Error handling payment failed:', error);
  }
}

async function handleSubscriptionDeleted(subscription) {
  try {
    const customer = await stripe.customers.retrieve(subscription.customer);
    const userId = customer.metadata.userId;
    
    await db.updateSubscription(userId, {
      status: 'CANCELLED',
      tier: 'FREE'
    });
    
    console.log(`Subscription deleted for user ${userId}`);
  } catch (error) {
    console.error('Error handling subscription deleted:', error);
  }
}

function getTierFromPriceId(priceId) {
  // Map your actual Stripe price IDs to subscription tiers
  const priceMapping = {
    [process.env.PRO_MONTHLY_PRICE_ID]: 'PRO_MONTHLY',
    [process.env.PRO_ANNUAL_PRICE_ID]: 'PRO_ANNUAL'
  };
  
  return priceMapping[priceId] || 'FREE';
}

app.listen(PORT, () => {
  console.log(`🚀 Language Translator Backend running on port ${PORT}`);
  console.log(`📊 Health check: http://localhost:${PORT}/api/health`);
});

module.exports = app;
EOF

# Create database module
cat > database.js << 'EOF'
const sqlite3 = require('sqlite3').verbose();
const { v4: uuidv4 } = require('uuid');

class Database {
  constructor() {
    this.db = new sqlite3.Database(':memory:'); // Use file for production: './translator.db'
    this.init();
  }
  
  init() {
    const createTables = `
      CREATE TABLE IF NOT EXISTS customers (
        id TEXT PRIMARY KEY,
        user_id TEXT UNIQUE NOT NULL,
        stripe_customer_id TEXT UNIQUE NOT NULL,
        email TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );
      
      CREATE TABLE IF NOT EXISTS subscriptions (
        id TEXT PRIMARY KEY,
        user_id TEXT UNIQUE NOT NULL,
        subscription_id TEXT,
        tier TEXT DEFAULT 'FREE',
        status TEXT DEFAULT 'ACTIVE',
        current_period_end DATETIME,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES customers (user_id)
      );
      
      CREATE TABLE IF NOT EXISTS usage_logs (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        action TEXT NOT NULL,
        metadata TEXT,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES customers (user_id)
      );
      
      CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id);
      CREATE INDEX IF NOT EXISTS idx_usage_logs_user_id ON usage_logs (user_id);
      CREATE INDEX IF NOT EXISTS idx_usage_logs_timestamp ON usage_logs (timestamp);
    `;
    
    this.db.exec(createTables, (err) => {
      if (err) {
        console.error('Error creating tables:', err);
      } else {
        console.log('📦 Database tables created successfully');
      }
    });
  }
  
  async getCustomerByUserId(userId) {
    return new Promise((resolve, reject) => {
      this.db.get(
        'SELECT * FROM customers WHERE user_id = ?',
        [userId],
        (err, row) => {
          if (err) reject(err);
          else resolve(row);
        }
      );
    });
  }
  
  async createCustomer(userId, stripeCustomerId, email = null) {
    return new Promise((resolve, reject) => {
      const customerId = uuidv4();
      this.db.run(
        'INSERT INTO customers (id, user_id, stripe_customer_id, email) VALUES (?, ?, ?, ?)',
        [customerId, userId, stripeCustomerId, email],
        function(err) {
          if (err) reject(err);
          else resolve({ id: customerId, userId, stripeCustomerId, email });
        }
      );
    });
  }
  
  async getUserSubscription(userId) {
    return new Promise((resolve, reject) => {
      this.db.get(
        'SELECT * FROM subscriptions WHERE user_id = ?',
        [userId],
        (err, row) => {
          if (err) reject(err);
          else resolve(row);
        }
      );
    });
  }
  
  async updateSubscription(userId, subscriptionData) {
    return new Promise((resolve, reject) => {
      const {
        subscriptionId,
        tier,
        status,
        currentPeriodEnd
      } = subscriptionData;
      
      this.db.run(
        `INSERT OR REPLACE INTO subscriptions 
         (id, user_id, subscription_id, tier, status, current_period_end, updated_at) 
         VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`,
        [uuidv4(), userId, subscriptionId, tier, status, currentPeriodEnd],
        function(err) {
          if (err) reject(err);
          else resolve({ userId, ...subscriptionData });
        }
      );
    });
  }
  
  async updateSubscriptionStatus(userId, status) {
    return new Promise((resolve, reject) => {
      this.db.run(
        'UPDATE subscriptions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?',
        [status, userId],
        function(err) {
          if (err) reject(err);
          else resolve({ userId, status });
        }
      );
    });
  }
  
  async recordUsage(userId, action, metadata = null) {
    return new Promise((resolve, reject) => {
      const usageId = uuidv4();
      const metadataJson = metadata ? JSON.stringify(metadata) : null;
      
      this.db.run(
        'INSERT INTO usage_logs (id, user_id, action, metadata) VALUES (?, ?, ?, ?)',
        [usageId, userId, action, metadataJson],
        function(err) {
          if (err) reject(err);
          else resolve({ id: usageId, userId, action, metadata });
        }
      );
    });
  }
  
  async getUsageStats(userId, days = 30) {
    return new Promise((resolve, reject) => {
      this.db.all(
        `SELECT action, COUNT(*) as count, DATE(timestamp) as date 
         FROM usage_logs 
         WHERE user_id = ? AND timestamp > datetime('now', '-${days} days')
         GROUP BY action, DATE(timestamp)
         ORDER BY date DESC`,
        [userId],
        (err, rows) => {
          if (err) reject(err);
          else resolve(rows);
        }
      );
    });
  }
}

module.exports = Database;
EOF

# Create environment template
cat > .env.example << 'EOF'
# Server Configuration
PORT=3000
NODE_ENV=development
ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com

# Stripe Configuration
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
STRIPE_PUBLISHABLE_KEY=pk_test_your_stripe_publishable_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret

# Price IDs from Stripe Dashboard
PRO_MONTHLY_PRICE_ID=price_your_monthly_price_id
PRO_ANNUAL_PRICE_ID=price_your_annual_price_id

# Database (for production, use PostgreSQL or MySQL)
DATABASE_URL=file:./translator.db

# Analytics (optional)
ANALYTICS_API_KEY=your_analytics_api_key
EOF

# Create startup script
cat > start.sh << 'EOF'
#!/bin/bash

# Check if .env exists
if [ ! -f .env ]; then
    echo "⚠️  Creating .env file from template..."
    cp .env.example .env
    echo "📝 Please edit .env file with your actual Stripe keys"
    echo "🔑 Get your keys from: https://dashboard.stripe.com/apikeys"
    exit 1
fi

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Start the server
echo "🚀 Starting Language Translator Backend..."
npm run dev
EOF

chmod +x start.sh

# Create simple test file
cat > test.js << 'EOF'
const request = require('supertest');
const app = require('./server');

describe('API Health Check', () => {
  test('GET /api/health should return healthy status', async () => {
    const response = await request(app)
      .get('/api/health')
      .expect(200);
    
    expect(response.body.status).toBe('healthy');
  });
});
EOF

# Create README for backend
cat > README.md << 'EOF'
# Language Translator Backend

Simple Node.js/Express backend for handling Language Translator app subscriptions and monetization.

## Features

- ✅ Stripe Checkout Session creation
- ✅ Subscription status management
- ✅ Webhook handling for subscription events
- ✅ Usage analytics tracking
- ✅ SQLite database (easily upgradeable to PostgreSQL/MySQL)
- ✅ Security middleware (CORS, Helmet, Rate limiting)

## Quick Start

1. **Setup Environment**
   ```bash
   chmod +x start.sh
   ./start.sh
   ```

2. **Configure Stripe Keys**
   - Edit `.env` file with your actual Stripe keys
   - Get keys from: https://dashboard.stripe.com/apikeys

3. **Start Development Server**
   ```bash
   npm run dev
   ```

## API Endpoints

### Core Endpoints
- `GET /api/health` - Health check
- `POST /api/create-checkout-session` - Create Stripe checkout
- `GET /api/subscription/status/:userId` - Get subscription status
- `POST /api/subscription/cancel/:userId` - Cancel subscription
- `POST /api/usage/record` - Record usage analytics

### Webhooks
- `POST /webhook/stripe` - Stripe webhook handler

## Production Deployment

### Using Heroku
```bash
heroku create your-translator-backend
heroku config:set STRIPE_SECRET_KEY=sk_live_your_key
heroku config:set STRIPE_WEBHOOK_SECRET=whsec_your_secret
git push heroku main
```

### Using Railway
```bash
railway login
railway init
railway add PostgreSQL
railway deploy
```

### Using DigitalOcean App Platform
1. Connect GitHub repository
2. Set environment variables in dashboard
3. Deploy automatically

## Security Checklist

- [ ] Use HTTPS in production
- [ ] Set strong webhook secrets
- [ ] Implement proper authentication
- [ ] Use environment variables for all secrets
- [ ] Enable request logging
- [ ] Set up monitoring and alerts

## Revenue Tracking

The backend automatically tracks:
- Subscription conversions
- Daily active users
- Translation usage patterns
- Revenue metrics

Access via database queries or integrate with analytics services.
EOF

echo "✅ Language Translator Backend setup complete!"
echo ""
echo "📁 Created backend in: language-translator-backend/"
echo "🔧 Next steps:"
echo "   1. cd language-translator-backend"
echo "   2. ./start.sh (this will prompt for Stripe keys)"
echo "   3. Edit .env with your Stripe configuration"
echo "   4. npm run dev to start development server"
echo ""
echo "🔑 You'll need:"
echo "   - Stripe Secret Key (sk_test_... or sk_live_...)"
echo "   - Stripe Publishable Key (pk_test_... or pk_live_...)"
echo "   - Stripe Webhook Secret (whsec_...)"
echo "   - Price IDs from Stripe Dashboard"
echo ""
echo "📖 See MONETIZATION_GUIDE.md for complete setup instructions"