export async function apiGet(path) {
  return apiRequest(path);
}

export async function apiPost(path, body) {
  return apiRequest(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function apiRequest(path, options = {}) {
  const response = await fetch(path, options);
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
    return 'Unknown';
  }
  return Number(value).toFixed(digits).replace(/\.0$/, '');
}

export function percent(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'Unknown';
  }
  return `${Math.round(Number(value) * 100)}%`;
}
