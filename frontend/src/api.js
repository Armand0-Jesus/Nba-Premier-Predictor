export async function apiGet(path) {
  return apiRequest(path);
}

export async function apiPost(path, body) {
  const options = {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  };
  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }
  return apiRequest(path, options);
}

async function apiRequest(path, options = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 20000);
  let response;
  try {
    response = await fetch(path, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error('That took too long. Try another game or refresh.');
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(payload?.message || `Request failed with status ${response.status}`);
  }

  return payload;
}

export function pageItems(payload) {
  return Array.isArray(payload) ? payload : payload?.content || [];
}

export function compactNumber(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'Pending';
  }
  return Number(value).toFixed(digits).replace(/\.0$/, '');
}

export function percent(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'Pending';
  }
  return `${Math.round(Number(value) * 100)}%`;
}
