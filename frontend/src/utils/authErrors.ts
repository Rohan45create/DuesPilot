export const getFriendlyAuthErrorMessage = (error: unknown): string => {
  if (!error) return '';
  const message = error instanceof Error ? error.message : String(error);
  const lower = message.toLowerCase();

  if (lower.includes('invalid login credentials') || lower.includes('invalid credentials')) {
    return 'Incorrect email or password. Please check your credentials or continue with Google.';
  }
  if (lower.includes('user already registered') || lower.includes('already exists')) {
    return 'An account with this email already exists. Please switch to Sign In.';
  }
  if (lower.includes('rate limit') || lower.includes('too many requests') || lower.includes('over_email_send_rate_limit')) {
    return 'Too many login attempts. Please wait a minute before trying again.';
  }
  if (lower.includes('email not confirmed')) {
    return 'Your email has not been confirmed yet. Please check your inbox for the confirmation link.';
  }
  if (lower.includes('password should be at least') || lower.includes('weak password')) {
    return 'Password is too weak. Please use at least 8 characters with a mix of letters, numbers, and symbols.';
  }
  if (lower.includes('anonymous sign-ins are disabled') || lower.includes('anonymous provider disabled')) {
    return 'Anonymous demo sign-in is disabled in Supabase. Please sign up with email or use Google.';
  }
  if (lower.includes('turnstile') || lower.includes('captcha')) {
    return 'Security verification failed. Please complete the captcha and try again.';
  }

  return message;
};
