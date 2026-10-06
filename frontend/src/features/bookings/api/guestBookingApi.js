import { api } from '../../../shared/api/apiClient';

export const guestBookingApi = {
  availability: (date, courtId) => api(`/court-availability?date=${date}${courtId ? `&courtId=${courtId}` : ''}`),
  quote: selection => api('/booking-quotes', { method: 'POST', body: selection }),
  create: (form, requestKey) => api('/guest/bookings', { method: 'POST', body: form, requestKey }),
  detail: (code, token) => api(`/guest/bookings/${encodeURIComponent(code)}`, { token }),
  cancelHold: (code, token) => api(`/guest/bookings/${encodeURIComponent(code)}/cancel`, { method: 'POST', token }),
  requestCancel: (code, token, reason) => api(`/guest/bookings/${encodeURIComponent(code)}/cancellation-requests`, { method: 'POST', token, body: { reason } }),
};
