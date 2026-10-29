import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, AlertCircle } from 'lucide-react';
import { supabase } from '../lib/supabase';
import { api } from '../api/client';

export const AuthCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const checkOnboardingAndRedirect = async () => {
      try {
        const mePromise = api.getMe();
        const timeoutPromise = new Promise<null>((resolve) => setTimeout(() => resolve(null), 2500));
        const me = await Promise.race([mePromise, timeoutPromise]);

        if (me && me.flags?.requiresOnboarding) {
          navigate('/onboarding', { replace: true });
        } else {
          navigate('/app', { replace: true });
        }
      } catch {
        navigate('/app', { replace: true });
      }
    };

    const handleCallback = async () => {
      try {
        // 1. Check for provider errors in URL query params
        const urlParams = new URLSearchParams(window.location.search);
        const urlError = urlParams.get('error_description') || urlParams.get('error');
        if (urlError) {
          if (isMounted) setErrorMsg(urlError);
          return;
        }

        // 2. PKCE authorization code exchange
        const code = urlParams.get('code');
        if (code) {
          try {
            const { data, error } = await supabase.auth.exchangeCodeForSession(code);
            if (!error && data?.session) {
              await checkOnboardingAndRedirect();
              return;
            }
          } catch (codeErr) {
            console.warn('PKCE exchange error, checking fallback session:', codeErr);
          }
        }

        // 3. Check for existing active session
        const { data: { session }, error } = await supabase.auth.getSession();
        if (error) throw error;

        if (session) {
          await checkOnboardingAndRedirect();
          return;
        }

        // 4. Listen for auth state change with timeout safety
        let handled = false;
        const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_event, newSession) => {
          if (handled) return;
          if (newSession) {
            handled = true;
            subscription.unsubscribe();
            await checkOnboardingAndRedirect();
          }
        });

        // 5. Safety timeout: if no session received within 3.5s, resolve gracefully
        setTimeout(async () => {
          if (handled || !isMounted) return;
          handled = true;
          subscription.unsubscribe();

          const { data: { session: finalSession } } = await supabase.auth.getSession();
          if (finalSession) {
            await checkOnboardingAndRedirect();
          } else if (window.location.hash.includes('access_token')) {
            navigate('/app', { replace: true });
          } else {
            // Redirect to signin or allow retry
            setErrorMsg('Sign-in session timed out. Please try signing in again.');
          }
        }, 3500);

      } catch (err: unknown) {
        if (isMounted) {
          setErrorMsg(err instanceof Error ? err.message : 'Authentication verification failed.');
        }
      }
    };

    handleCallback();

    return () => {
      isMounted = false;
    };
  }, [navigate]);

  return (
    <div className="min-h-screen w-full bg-background flex flex-col items-center justify-center p-6 text-foreground">
      <div className="w-full max-w-sm p-8 bg-surface border border-border rounded-2xl shadow-xl text-center">
        {errorMsg ? (
          <div>
            <div className="w-12 h-12 rounded-full bg-status-red/10 text-status-red mx-auto flex items-center justify-center mb-4">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h2 className="text-lg font-bold font-heading mb-2">Sign-in Failed</h2>
            <p className="text-xs text-foreground-muted mb-6">{errorMsg}</p>
            <button
              type="button"
              onClick={() => navigate('/signin', { replace: true })}
              className="w-full py-2.5 rounded-xl bg-foreground text-background font-semibold text-xs cursor-pointer hover:opacity-90"
            >
              Return to Sign In
            </button>
          </div>
        ) : (
          <div>
            <div className="w-12 h-12 rounded-full bg-brand/10 text-brand mx-auto flex items-center justify-center mb-4">
              <Loader2 className="w-6 h-6 animate-spin text-brand" />
            </div>
            <h2 className="text-lg font-bold font-heading mb-2">Completing Sign-In</h2>
            <p className="text-xs text-foreground-muted">
              Securing your session and loading your MSME recovery workspace...
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
