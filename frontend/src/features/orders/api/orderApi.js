import { api } from '../../../shared/api/apiClient';

export const orderApi = {
  quote: fulfillmentMethod => api('/guest/order-quotes', { method: 'POST', body: { fulfillmentMethod } }),
  create: (request, requestKey) => api('/guest/orders', { method: 'POST', body: request, requestKey }),
  detail: (code, token) => api(`/guest/orders/${code}`, { token }),
  cancel: (code, token) => api(`/guest/orders/${code}/cancel`, { method: 'POST', token }),
};
