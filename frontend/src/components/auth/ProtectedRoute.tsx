import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const ProtectedRoute: React.FC = () => {
  const { user, session, isLoading, isAnonymous, flags } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen w-full bg-background flex flex-col items-center justify-center p-6 text-foreground">
        <div className="w-10 h-10 rounded-full bg-brand/10 text-brand flex items-center justify-center mb-3">
          <Loader2 className="w-5 h-5 animate-spin text-brand" />
        </div>
        <p className="text-xs text-foreground-muted">Loading DuesPilot session...</p>
      </div>
    );
  }

  if (!session || !user) {
    return <Navigate to="/signin" state={{ from: location }} replace />;
  }

  // If user is not anonymous and requires onboarding, send to /onboarding
  if (!isAnonymous && flags?.requiresOnboarding && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />;
  }

  return <Outlet />;
};
