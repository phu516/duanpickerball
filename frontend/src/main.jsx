import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './app/App';
import { AppProviders } from './app/providers';
import { PlayerAuthProvider } from './features/auth/PlayerAuthContext';
import './assets/styles/main.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode><AppProviders><PlayerAuthProvider><App /></PlayerAuthProvider></AppProviders></React.StrictMode>
);
