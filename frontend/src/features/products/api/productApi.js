import { api } from '../../../shared/api/apiClient';

export const productApi = {
  list: () => api('/products'),
  detail: id => api(`/products/${id}`),
  categories: () => api('/categories'),
  create: request => api('/admin/products', { method: 'POST', body: request }),
  updateImage: (id, imageUrl) => api(`/admin/products/${id}/image`, {
    method: 'PUT', body: { imageUrl },
  }),
  updateVariantImage: (id, imageUrl) => api(`/admin/product-variants/${id}/image`, {
    method: 'PUT', body: { imageUrl },
  }),
};
