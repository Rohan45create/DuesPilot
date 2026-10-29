import { describe, it, expect } from 'vitest';
import { isMockModeActive, IS_MOCK_MODE } from './client';

describe('API Client Mock Mode Guards (F1)', () => {
  it('is opt-in only when explicitly set to true in non-production', () => {
    expect(isMockModeActive(false, 'true')).toBe(true);
  });

  it('defaults to false when VITE_MOCK_API is undefined or empty', () => {
    expect(isMockModeActive(false, undefined)).toBe(false);
    expect(isMockModeActive(false, '')).toBe(false);
  });

  it('is false when VITE_MOCK_API is false or any other string', () => {
    expect(isMockModeActive(false, 'false')).toBe(false);
    expect(isMockModeActive(false, '0')).toBe(false);
    expect(isMockModeActive(false, 'no')).toBe(false);
  });

  it('is strictly forced OFF in production builds regardless of env variable', () => {
    expect(isMockModeActive(true, 'true')).toBe(false);
    expect(isMockModeActive(true, 'false')).toBe(false);
    expect(isMockModeActive(true, undefined)).toBe(false);
  });

  it('evaluates to false in default test/dev environment without opt-in', () => {
    // Unless VITE_MOCK_API is explicitly 'true', IS_MOCK_MODE must be false
    if (import.meta.env.VITE_MOCK_API !== 'true') {
      expect(IS_MOCK_MODE).toBe(false);
    }
  });
});
