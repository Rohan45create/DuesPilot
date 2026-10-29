import fs from 'fs';
import path from 'path';

/**
 * Loads environment variables from .env file without any external dependencies.
 * SECURITY: NEVER print, log, or serialize any API keys.
 */
export function loadEnv() {
  const envPath = path.resolve(process.cwd(), '.env');
  const env = { ...process.env };

  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf8');
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx !== -1) {
        const key = trimmed.slice(0, eqIdx).trim();
        let val = trimmed.slice(eqIdx + 1).trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        if (val) {
          env[key] = val;
        }
      }
    }
  }

  return {
    NEBIUS_API_KEY: env.NEBIUS_API_KEY || '',
    TAVILY_API_KEY: env.TAVILY_API_KEY || '',
    NEBIUS_BASE_URL: env.NEBIUS_BASE_URL || '',
  };
}

export function getChatEndpoint(baseUrlOverride) {
  if (baseUrlOverride) {
    return `${baseUrlOverride.replace(/\/+$/, '')}/chat/completions`;
  }
  const { NEBIUS_API_KEY, NEBIUS_BASE_URL } = loadEnv();
  if (NEBIUS_BASE_URL) {
    return `${NEBIUS_BASE_URL.replace(/\/+$/, '')}/chat/completions`;
  }
  if (NEBIUS_API_KEY.startsWith('nvapi-')) {
    return 'https://integrate.api.nvidia.com/v1/chat/completions';
  }
  return 'https://api.tokenfactory.nebius.com/v1/chat/completions';
}
