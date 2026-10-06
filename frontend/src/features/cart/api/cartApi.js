import { api } from '../../../shared/api/apiClient';

export const cartApi = {
  open: () => api('/guest/cart', { method: 'POST' }),
  add: (variantId, quantity = 1) => api('/guest/cart/items', {
    method: 'POST', body: { variantId, quantity },
  }),
  change: (itemId, quantity) => api(`/guest/cart/items/${itemId}`, {
    method: 'PATCH', body: { quantity },
  }),
  remove: itemId => api(`/guest/cart/items/${itemId}`, { method: 'DELETE' }),
};
