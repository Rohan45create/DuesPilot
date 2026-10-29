import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const PublicOnlyRoute: React.FC = () => {
  const { user, session, isLoading, isAnonymous, flags } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen w-full bg-background flex flex-col items-center justify-center p-6 text-foreground">
        <div className="w-10 h-10 rounded-full bg-brand/10 text-brand flex items-center justify-center mb-3">
          <Loader2 className="w-5 h-5 animate-spin text-brand" />
        </div>
        <p className="text-xs text-foreground-muted">Checking authentication...</p>
      </div>
    );
  }

  // Only redirect away if user is authenticated with a non-anonymous account.
  // Anonymous demo users can access /signin and /signup to create/link an account.
  if (session && user && !isAnonymous) {
    if (flags?.requiresOnboarding) {
      return <Navigate to="/onboarding" replace />;
    }
    return <Navigate to="/app" replace />;
  }

  return <Outlet />;
};
