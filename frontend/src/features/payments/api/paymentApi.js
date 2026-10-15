import { api } from '../../../shared/api/apiClient';

export const paymentApi = {
  booking: (code, token, purpose) => api(`/guest/bookings/${code}/payments`, {
    method: 'POST', token, body: { purpose },
  }),
  order: (code, token) => api(`/guest/orders/${code}/payments`, { method: 'POST', token }),
  bookingDetail: (code, token, id) => api(`/guest/bookings/${code}/payments/${id}`, { token }),
  orderDetail: (code, token, id) => api(`/guest/orders/${code}/payments/${id}`, { token }),
  bookingList: (code, token) => api(`/guest/bookings/${code}/payments`, { token }),
  orderList: (code, token) => api(`/guest/orders/${code}/payments`, { token }),
  sandboxComplete: (id, token) => api(`/dev/payments/${id}/complete`, {
    method: 'POST', token, body: { success: true },
  }),
};
