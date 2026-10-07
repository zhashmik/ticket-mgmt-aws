/**
 * Thin API client for the Ticket Management backend.
 * Base URL can be overridden with ?api=... in the query string,
 * otherwise defaults to the local backend context path.
 */
const API_BASE = (() => {
    const override = new URLSearchParams(window.location.search).get('api');
    if (override) return override.replace(/\/$/, '');
    // Served from the backend or a static host; default to local backend.
    return 'http://localhost:8080/api';
})();

async function request(path, options = {}) {
    const res = await fetch(`${API_BASE}${path}`, {
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options,
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
        const message = data?.message || `Request failed (${res.status})`;
        const err = new Error(message);
        err.status = res.status;
        err.body = data;
        throw err;
    }
    return data;
}

const Api = {
    health: () => request('/actuator/health'),
    stats: () => request('/dashboard/stats'),
    listTickets: (status, page = 0, size = 10) => {
        const q = new URLSearchParams({ page, size });
        if (status) q.set('status', status);
        return request(`/tickets?${q.toString()}`);
    },
    getTicket: (id) => request(`/tickets/${id}`),
    getHistory: (id) => request(`/tickets/${id}/history`),
    createTicket: (payload) => request('/tickets', { method: 'POST', body: JSON.stringify(payload) }),
    updateStatus: (id, payload) => request(`/tickets/${id}/status`, { method: 'PATCH', body: JSON.stringify(payload) }),
    resolveTicket: (id, payload) => request(`/tickets/resolve/${id}`, { method: 'POST', body: JSON.stringify(payload) }),
    listEmployees: () => request('/employees'),
};
