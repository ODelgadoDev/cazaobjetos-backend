// Example integration helper. Cookie session + CSRF; no automatic mutation retries.
export function createApi(baseUrl, fetcher = fetch) {
  let token;
  async function decode(response) {
    if (response.status === 204) return null;
    return response.headers.get('content-type')?.includes('json') ? response.json() : response.blob();
  }
  async function csrf() {
    const response = await fetcher(`${baseUrl}/auth/csrf`, {credentials: 'include'});
    if (!response.ok) { token = undefined; throw Object.assign(new Error('Solicita una nueva sesión y vuelve a intentar.'), {status: response.status}); }
    token = await response.json();
  }
  async function request(path, {method = 'GET', body, headers = {}} = {}) {
    const outgoing = new Headers(headers);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      if (!token) await csrf();
      outgoing.set(token.headerName, token.token);
    }
    if (body != null && !(body instanceof FormData) && !(body instanceof URLSearchParams) && !(body instanceof Blob)) {
      outgoing.set('Content-Type', 'application/json'); body = JSON.stringify(body);
    }
    const response = await fetcher(`${baseUrl}${path}`, {method, body, headers: outgoing, credentials: 'include'});
    const result = await decode(response);
    if (!response.ok) {
      if ([401,403].includes(response.status)) token = undefined;
      throw Object.assign(new Error(result?.message || `HTTP ${response.status}`), {status: response.status});
    }
    return result;
  }
  return {
    request,
    async login(email, password) {
      await request('/auth/login', {method: 'POST', body: new URLSearchParams({username: email, password})});
      token = undefined; await csrf(); return request('/auth/me');
    },
    async logout() { try { await request('/auth/logout', {method: 'POST'}); } finally { token = undefined; } },
    async changePassword(currentPassword,newPassword) {
      try { await request('/auth/change-password',{method:'POST',body:{currentPassword,newPassword}}); } finally { token=undefined; }
    },
    createFound(body, key) { if (!/^[A-Za-z0-9._:-]{8,100}$/.test(key || '')) throw new Error('Genera y conserva una clave para esta publicación.'); return request('/found-items', {method:'POST',body,headers:{'Idempotency-Key':key}}); }
  };
}
