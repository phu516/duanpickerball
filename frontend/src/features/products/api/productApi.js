import { api } from '../../../shared/api/apiClient';

export const productApi = {
  list: () => api('/products'),
  categories: () => api('/categories'),
  create: request => api('/admin/products', { method: 'POST', body: request }),
};
