import { api } from '../../../shared/api/apiClient';

export const adminBookingApi = {
  list: () => api('/admin/bookings'),
  cancel: (id, reason) => api(`/admin/bookings/${id}/cancel`, { method: 'POST', body: { reason } }),
  checkIn: id => api(`/admin/bookings/${id}/check-in`, { method: 'POST' }),
  noShow: id => api(`/admin/bookings/${id}/no-show`, { method: 'POST' }),
  complete: id => api(`/admin/bookings/${id}/complete`, { method: 'POST' }),
};
