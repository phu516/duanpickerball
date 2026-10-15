const baseUrl = import.meta.env.VITE_API_URL || '/api/v1';
let csrf;

export const apiUrl = path => `${baseUrl}${path}`;

export async function api(path, { method = 'GET', body, token, form = false, requestKey } = {}) {
  if (method !== 'GET' && !csrf) {
    const response = await fetch(`${baseUrl}/auth/csrf`, { credentials: 'include' });
    if (!response.ok) throw new Error('Không lấy được phiên bảo mật.');
    csrf = await response.json();
  }
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
    if (response.status === 403 && method !== 'GET') csrf = undefined;
    throw new Error(data?.message || `Yêu cầu thất bại (${response.status})`);
  }
  return data;
}

export function resetCsrf() { csrf = undefined; }
