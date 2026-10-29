#!/usr/bin/env node

/**
 * scripts/check-env.js
 *
 * Verifies that .env exists at the repository root and contains all required
 * environment variable keys.
 *
 * SECURITY REQUIREMENT:
 * Lists missing variables BY NAME ONLY. NEVER prints variable values.
 */

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');
const ENV_PATH = path.join(ROOT_DIR, '.env');
const EXAMPLE_PATH = path.join(ROOT_DIR, '.env.example');

// Core variables required for local run
const REQUIRED_CORE_VARS = [
  'POSTGRES_DB',
  'POSTGRES_USER',
  'POSTGRES_PASSWORD',
  'POSTGRES_PORT',
  'PORT',
  'DEMO_MODE',
  'VITE_SUPABASE_URL',
  'VITE_API_BASE_URL',
];

// Recommended provider variables
const RECOMMENDED_VARS = [
  'NEBIUS_API_KEY',
  'TAVILY_API_KEY',
];

if (!fs.existsSync(ENV_PATH)) {
  console.error('\x1b[31m[ERROR]\x1b[0m .env file not found at repository root.');
  console.error('Please copy the template to create your .env:');
  console.error('  cp .env.example .env\n');
  process.exit(1);
}

const envContent = fs.readFileSync(ENV_PATH, 'utf-8');
const envVars = new Map();

for (const line of envContent.split('\n')) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) continue;
  const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
  if (match) {
    const key = match[1];
    const val = match[2].trim();
    envVars.set(key, val);
  }
}

const missingCore = [];
for (const varName of REQUIRED_CORE_VARS) {
  if (!envVars.has(varName) || envVars.get(varName) === '') {
    missingCore.push(varName);
  }
}

const missingRecommended = [];
for (const varName of RECOMMENDED_VARS) {
  if (!envVars.has(varName) || envVars.get(varName) === '') {
    missingRecommended.push(varName);
  }
}

if (missingCore.length > 0) {
  console.error('\x1b[31m[FAIL]\x1b[0m Missing required environment variables:');
  for (const v of missingCore) {
    console.error(`  - ${v}`);
  }
  console.error('\nPlease populate these variables in your .env file (names only shown above).');
  process.exit(1);
}

if (missingRecommended.length > 0) {
  console.warn('\x1b[33m[WARN]\x1b[0m Optional/recommended keys empty (replay/mock fallback will be used):');
  for (const v of missingRecommended) {
    console.warn(`  - ${v}`);
  }
}

console.log('\x1b[32m[PASS]\x1b[0m All required environment variables are set in .env.');
process.exit(0);
