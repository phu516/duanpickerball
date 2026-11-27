import { api } from '../../../shared/api/apiClient';

const cacheDurationMs = 60_000;
let productList = null;
let listExpiresAt = 0;
let listRequest = null;
const detailRequests = new Map();

function invalidateProducts() {
  productList = null;
  listExpiresAt = 0;
  detailRequests.clear();
}

function listProducts() {
  if (productList && Date.now() < listExpiresAt) return Promise.resolve(productList);
  if (listRequest) return listRequest;
  listRequest = api('/products').then(data => {
    productList = data;
    listExpiresAt = Date.now() + cacheDurationMs;
    return data;
  }).finally(() => { listRequest = null; });
  return listRequest;
}

function productDetail(id) {
  const cached = Date.now() < listExpiresAt && productList?.find(item => String(item.id) === String(id));
  if (cached) return Promise.resolve(cached);
  if (listRequest) return listRequest.then(() => productDetail(id));
  const key = String(id);
  if (detailRequests.has(key)) return detailRequests.get(key);
  const request = api(`/products/${id}`).finally(() => detailRequests.delete(key));
  detailRequests.set(key, request);
  return request;
}

export const productApi = {
  list: listProducts,
  detail: productDetail,
  categories: () => api('/categories'),
  create: async request => {
    const result = await api('/admin/products', { method: 'POST', body: request });
    invalidateProducts();
    return result;
  },
  updateImage: async (id, imageUrl) => {
    const result = await api(`/admin/products/${id}/image`, {
      method: 'PUT', body: { imageUrl },
    });
    invalidateProducts();
    return result;
  },
  updateVariantImage: async (id, imageUrl) => {
    const result = await api(`/admin/product-variants/${id}/image`, {
      method: 'PUT', body: { imageUrl },
    });
    invalidateProducts();
    return result;
  },
};
