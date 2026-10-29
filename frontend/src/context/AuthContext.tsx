import React, { createContext, useContext, useEffect, useState, useMemo, useCallback } from 'react';
import { Session, User } from '@supabase/supabase-js';
import { supabase } from '../lib/supabase';
import { api } from '../api/client';
import { MeResponse, WorkspaceProfile, WorkspaceFlags, UpdateWorkspaceProfileRequest } from '../api/schemas';

interface AuthContextType {
  session: Session | null;
  user: User | null;
  isAnonymous: boolean;
  isLoading: boolean;
  profile: MeResponse | null;
  workspace: WorkspaceProfile | null;
  flags: WorkspaceFlags | null;
  signInWithGoogle: () => Promise<void>;
  signInWithPassword: (email: string, password: string) => Promise<{ error: Error | null }>;
  signUpWithPassword: (email: string, password: string, captchaToken?: string) => Promise<{ error: Error | null; needsEmailVerification?: boolean }>;
  signInAnonymously: (captchaToken?: string) => Promise<{ error: Error | null }>;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  updateProfile: (data: UpdateWorkspaceProfileRequest) => Promise<MeResponse>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [profile, setProfile] = useState<MeResponse | null>(null);

  const isAnonymous = useMemo(() => {
    return Boolean(user?.is_anonymous || user?.app_metadata?.provider === 'anonymous');
  }, [user]);

  const loadProfile = useCallback(async (currentSession?: Session | null) => {
    if (!currentSession) {
      setProfile(null);
      return;
    }
    try {
      const data = await api.getMe();
      setProfile(data);
    } catch {
      // If getMe fails (e.g. backend offline or mock mode), provide fallback
      setProfile({
        user: {
          id: currentSession.user.id,
          email: currentSession.user.email ?? null,
          isAnonymous: Boolean(currentSession.user.is_anonymous),
        },
        workspace: {
          id: '00000000-0000-0000-0000-000000000001',
          name: 'DuesPilot Workspace',
          businessName: 'DuesPilot Workspace',
          supplierSizeClass: 'MICRO',
          isDemo: Boolean(currentSession.user.is_anonymous),
        },
        flags: {
          demoReadOnly: Boolean(currentSession.user.is_anonymous),
          canCreateCases: !currentSession.user.is_anonymous,
          requiresOnboarding: false,
        },
      });
    }
  }, []);

  useEffect(() => {
    let mounted = true;

    // 1. Initial session load
    supabase.auth.getSession().then(({ data: { session: initialSession } }) => {
      if (!mounted) return;
      setSession(initialSession);
      setUser(initialSession?.user ?? null);
      if (initialSession) {
        loadProfile(initialSession).finally(() => {
          if (mounted) setIsLoading(false);
        });
      } else {
        setIsLoading(false);
      }
    }).catch(() => {
      if (mounted) setIsLoading(false);
    });

    // 2. Auth state subscription
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_event, newSession) => {
      if (!mounted) return;
      setSession(newSession);
      setUser(newSession?.user ?? null);
      if (newSession) {
        await loadProfile(newSession);
      } else {
        setProfile(null);
      }
      setIsLoading(false);
    });

    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, [loadProfile]);

  const signInWithGoogle = useCallback(async () => {
    const redirectTo = `${window.location.origin}/auth/callback`;
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
        queryParams: {
          access_type: 'offline',
          prompt: 'consent',
        },
      },
    });
    if (error) throw error;
  }, []);

  const signInWithPassword = useCallback(async (email: string, password: string) => {
    try {
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password,
      });
      if (error) return { error };
      setSession(data.session);
      setUser(data.user);
      await loadProfile(data.session);
      return { error: null };
    } catch (err: unknown) {
      return { error: err instanceof Error ? err : new Error(String(err)) };
    }
  }, [loadProfile]);

  const signUpWithPassword = useCallback(async (email: string, password: string, captchaToken?: string) => {
    try {
      const { data, error } = await supabase.auth.signUp({
        email,
        password,
        options: {
          captchaToken,
          emailRedirectTo: `${window.location.origin}/auth/callback`,
        },
      });
      if (error) return { error };

      // If user is returned without active session, email verification is required
      const needsEmailVerification = !data.session;
      if (data.session) {
        setSession(data.session);
        setUser(data.user);
        await loadProfile(data.session);
      }
      return { error: null, needsEmailVerification };
    } catch (err: unknown) {
      return { error: err instanceof Error ? err : new Error(String(err)) };
    }
  }, [loadProfile]);

  const signInAnonymously = useCallback(async (captchaToken?: string) => {
    try {
      const { data, error } = await supabase.auth.signInAnonymously({
        options: {
          captchaToken,
        },
      });
      if (error) {
        // Fallback to offline mock session
        const mockUser = {
          id: '00000000-0000-0000-0000-000000000001',
          aud: 'authenticated',
          role: 'authenticated',
          email: undefined,
          is_anonymous: true,
          app_metadata: { provider: 'anonymous' },
          user_metadata: {},
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        } as unknown as User;
        const mockSession: Session = {
          access_token: 'mock-anon-token',
          token_type: 'bearer',
          expires_in: 3600,
          refresh_token: 'mock-anon-refresh',
          user: mockUser,
        };
        setSession(mockSession);
        setUser(mockUser);
        await loadProfile(mockSession);
        return { error: null };
      }
      setSession(data.session);
      setUser(data.user);
      await loadProfile(data.session);
      return { error: null };
    } catch {
      const mockUser = {
        id: '00000000-0000-0000-0000-000000000001',
        aud: 'authenticated',
        role: 'authenticated',
        email: undefined,
        is_anonymous: true,
        app_metadata: { provider: 'anonymous' },
        user_metadata: {},
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      } as unknown as User;
      const mockSession: Session = {
        access_token: 'mock-anon-token',
        token_type: 'bearer',
        expires_in: 3600,
        refresh_token: 'mock-anon-refresh',
        user: mockUser,
      };
      setSession(mockSession);
      setUser(mockUser);
      await loadProfile(mockSession);
      return { error: null };
    }
  }, [loadProfile]);

  const signOut = useCallback(async () => {
    await supabase.auth.signOut();
    setSession(null);
    setUser(null);
    setProfile(null);
  }, []);

  const refreshProfile = useCallback(async () => {
    if (session) {
      await loadProfile(session);
    }
  }, [session, loadProfile]);

  const updateProfile = useCallback(async (data: UpdateWorkspaceProfileRequest) => {
    const updated = await api.updateMe(data);
    setProfile(updated);
    return updated;
  }, []);

  const value = useMemo<AuthContextType>(() => ({
    session,
    user,
    isAnonymous,
    isLoading,
    profile,
    workspace: profile?.workspace ?? null,
    flags: profile?.flags ?? null,
    signInWithGoogle,
    signInWithPassword,
    signUpWithPassword,
    signInAnonymously,
    signOut,
    refreshProfile,
    updateProfile,
  }), [
    session,
    user,
    isAnonymous,
    isLoading,
    profile,
    signInWithGoogle,
    signInWithPassword,
    signUpWithPassword,
    signInAnonymously,
    signOut,
    refreshProfile,
    updateProfile,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
