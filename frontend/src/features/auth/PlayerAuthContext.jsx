import { createContext, useContext, useEffect, useState } from 'react';
import { api, resetCsrf } from '../../shared/api/apiClient';

const PlayerAuthContext = createContext(null);

export function PlayerAuthProvider({ children }) {
  const [user, setUser] = useState(undefined);

  useEffect(() => {
    let active = true;
    api('/auth/me').then(account => { if (active) setUser(account); })
      .catch(() => { if (active) setUser(null); });
    return () => { active = false; };
  }, []);

  async function login(username, password) {
    await api('/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }), form: true });
    resetCsrf();
    try {
      const account = await api('/auth/me');
      setUser(account);
      return account;
    } catch (error) {
      if (error.status === 403) {
        await api('/auth/logout', { method: 'POST' }).catch(() => {});
        resetCsrf();
        setUser(null);
        throw new Error('Vui lòng dùng tài khoản người chơi để đăng nhập tại đây.');
      }
      throw error;
    }
  }

  async function logout() {
    await api('/auth/logout', { method: 'POST' });
    resetCsrf();
    setUser(null);
  }

  return <PlayerAuthContext.Provider value={{ user, login, logout }}>{children}</PlayerAuthContext.Provider>;
}

export function usePlayerAuth() { return useContext(PlayerAuthContext); }
