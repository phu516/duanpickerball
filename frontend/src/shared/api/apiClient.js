const baseUrl = import.meta.env.VITE_API_URL || '/api/v1';
let csrf;

export const apiUrl = path => `${baseUrl}${path}`;

async function loadCsrf() {
  const response = await fetch(`${baseUrl}/auth/csrf`, { credentials: 'include' });
  if (!response.ok) throw new Error('Không lấy được phiên bảo mật.');
  csrf = await response.json();
  return csrf;
}

export async function api(path, { method = 'GET', body, token, form = false, requestKey, csrfRetry = true } = {}) {
  // Fetch a fresh token for each write. Login/session rotation can invalidate a
  // cached token even though the user's authentication cookie is still valid.
  if (method !== 'GET') await loadCsrf();
  const headers = {};
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json';
  if (csrf && method !== 'GET') headers[csrf.headerName] = csrf.token;
  if (token) headers['X-Guest-Access-Token'] = token;
  if (requestKey) headers['Idempotency-Key'] = requestKey;
  const response = await fetch(`${baseUrl}${path}`, {
    method, credentials: 'include', headers,
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 403 && data?.code === 'CSRF_INVALID' && csrfRetry) {
      csrf = undefined;
      return api(path, { method, body, token, form, requestKey, csrfRetry: false });
    }
    const message = data?.code === 'CSRF_INVALID'
      ? 'Không duy trì được phiên bảo mật. Hãy tải lại trang rồi thử lại; nếu vẫn lỗi, cần kiểm tra cấu hình cookie và địa chỉ API.'
      : data?.message || `Yêu cầu thất bại (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    error.code = data?.code;
    throw error;
  }
  return data;
}

export function resetCsrf() { csrf = undefined; }
