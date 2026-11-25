import { useEffect, useState } from 'react';

export function useRoute() {
  const [route, setRoute] = useState(() => {
    if (location.hash) return location.hash.slice(1);
    const params = new URLSearchParams(location.search);
    const type = params.get('paymentType');
    const code = params.get('resourceCode');
    if (['booking', 'order'].includes(type) && /^[A-Za-z0-9]+$/.test(code || '')) {
      return `/${type === 'booking' ? 'bookings' : 'orders'}/${code}`;
    }
    return '/';
  });
  useEffect(() => {
    const update = () => setRoute(location.hash.slice(1) || '/');
    window.addEventListener('hashchange', update);
    return () => window.removeEventListener('hashchange', update);
  }, []);
  return route;
}

export function navigate(path) {
  location.hash = path;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
