import { api } from '../../../shared/api/apiClient';

export const paymentApi = {
  booking: (code, token, purpose) => api(`/guest/bookings/${code}/payments`, {
    method: 'POST', token, body: { purpose },
  }),
  order: (code, token) => api(`/guest/orders/${code}/payments`, { method: 'POST', token }),
  sandboxComplete: (id, token) => api(`/dev/payments/${id}/complete`, {
    method: 'POST', token, body: { success: true },
  }),
};
