import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { AppShell } from './components/layout/AppShell';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { PublicOnlyRoute } from './components/auth/PublicOnlyRoute';

import { LandingPage } from './pages/LandingPage';
import { AuthPage } from './pages/AuthPage';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { OverviewPage } from './pages/OverviewPage';
import { CasesListPage } from './pages/CasesListPage';
import { NewCasePage } from './pages/NewCasePage';
import { CaseWorkspacePage } from './pages/CaseWorkspacePage';
import { ReviewQueuePage } from './pages/ReviewQueuePage';
import { BenchmarksPage } from './pages/BenchmarksPage';
import { ModelsPage } from './pages/ModelsPage';
import { SettingsPage } from './pages/SettingsPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

// Helper component for backward-compatible case redirection
const CaseRedirect: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  return <Navigate to={`/app/cases/${id}`} replace />;
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <ToastProvider>
            <BrowserRouter>
              <Routes>
                {/* Public landing page at root "/" */}
                <Route path="/" element={<LandingPage />} />

                {/* Public-only authentication routes */}
                <Route element={<PublicOnlyRoute />}>
                  <Route path="/signin" element={<AuthPage initialMode="signin" />} />
                  <Route path="/signup" element={<AuthPage initialMode="signup" />} />
                </Route>

                {/* OAuth redirect callback */}
                <Route path="/auth/callback" element={<AuthCallbackPage />} />

                {/* Post-login onboarding */}
                <Route path="/onboarding" element={<OnboardingPage />} />

                {/* Protected app routes inside AppShell under /app/* */}
                <Route element={<ProtectedRoute />}>
                  <Route path="/app" element={<AppShell />}>
                    <Route index element={<OverviewPage />} />
                    <Route path="cases/new" element={<NewCasePage />} />
                    <Route path="cases" element={<CasesListPage />} />
                    <Route path="cases/:id" element={<CaseWorkspacePage />} />
                    <Route path="review" element={<ReviewQueuePage />} />
                    <Route path="benchmarks" element={<BenchmarksPage />} />
                    <Route path="models" element={<ModelsPage />} />
                    <Route path="settings" element={<SettingsPage />} />
                    <Route path="*" element={<Navigate to="/app" replace />} />
                  </Route>
                </Route>

                {/* Backward compatibility redirects for legacy routes */}
                <Route path="/cases/new" element={<Navigate to="/app/cases/new" replace />} />
                <Route path="/cases/:id" element={<CaseRedirect />} />
                <Route path="/cases" element={<Navigate to="/app/cases" replace />} />
                <Route path="/review" element={<Navigate to="/app/review" replace />} />
                <Route path="/benchmarks" element={<Navigate to="/app/benchmarks" replace />} />
                <Route path="/models" element={<Navigate to="/app/models" replace />} />
                <Route path="/settings" element={<Navigate to="/app/settings" replace />} />

                {/* Fallback to landing */}
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </BrowserRouter>
          </ToastProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
};

export default App;
