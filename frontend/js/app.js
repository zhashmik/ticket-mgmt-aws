/* UI state */
const state = {
    page: 0,
    size: 10,
    status: '',
    totalPages: 1,
    employees: [],
    employeeById: {},
};

const STATUSES = [
    'OPEN', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
    'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED', 'AUTO_CLOSED',
];

/* ---------- helpers ---------- */
const $ = (sel) => document.querySelector(sel);
const el = (tag, cls, html) => {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
};
const fmtDate = (iso) => iso ? new Date(iso).toLocaleString() : '—';
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(msg, kind = 'ok') {
    const t = $('#toast');
    t.textContent = msg;
    t.className = `toast ${kind}`;
    setTimeout(() => t.classList.add('hidden'), 3200);
}

function empName(id) {
    if (id == null) return '—';
    const e = state.employeeById[id];
    return e ? `${e.name}` : `#${id}`;
}

/* ---------- rendering ---------- */
function renderStats(stats) {
    const wrap = $('#stats');
    wrap.innerHTML = '';
    wrap.appendChild(statCard('Total', stats.total, 'total'));
    const interesting = ['OPEN', 'PENDING_APPROVAL', 'IN_PROGRESS', 'RESOLVED'];
    interesting.forEach((s) => wrap.appendChild(statCard(label(s), stats.byStatus[s] ?? 0)));
}

function statCard(labelText, value, extra = '') {
    const c = el('div', `stat ${extra}`);
    c.appendChild(el('div', 'label', labelText));
    c.appendChild(el('div', 'value', value));
    return c;
}

const label = (s) => s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());

function renderTickets(pageData) {
    const body = $('#ticketBody');
    body.innerHTML = '';
    state.totalPages = pageData.totalPages || 1;

    if (!pageData.content || pageData.content.length === 0) {
        body.appendChild(el('tr', '', `<td colspan="6"><div class="empty">No tickets found.</div></td>`));
    } else {
        pageData.content.forEach((t) => {
            const tr = el('tr');
            tr.innerHTML = `
                <td>${t.id}</td>
                <td>${esc(t.title)}</td>
                <td><span class="badge p-${t.priority}">${t.priority}</span></td>
                <td><span class="badge s-${t.status}">${label(t.status)}</span></td>
                <td>${fmtDate(t.creationDate)}</td>
                <td><button class="btn sm" data-id="${t.id}">View</button></td>`;
            tr.querySelector('button').addEventListener('click', () => openTicket(t.id));
            body.appendChild(tr);
        });
    }
    $('#pageInfo').textContent = `page ${state.page + 1} of ${state.totalPages}`;
    $('#prevPage').disabled = state.page === 0;
    $('#nextPage').disabled = state.page + 1 >= state.totalPages;
}

async function openTicket(id) {
    try {
        const [t, history] = await Promise.all([Api.getTicket(id), Api.getHistory(id)]);
        $('#modalTitle').textContent = `Ticket #${t.id}`;
        const body = $('#modalBody');
        body.innerHTML = '';

        const details = el('div');
        const rows = [
            ['Title', esc(t.title)],
            ['Status', `<span class="badge s-${t.status}">${label(t.status)}</span>`],
            ['Priority', `<span class="badge p-${t.priority}">${t.priority}</span>`],
            ['Category', esc(t.category) || '—'],
            ['Created by', esc(empName(t.createdById))],
            ['Assignee', esc(empName(t.assigneeId))],
            ['Created', fmtDate(t.creationDate)],
            ['Resolved', fmtDate(t.resolvedDate)],
        ];
        rows.forEach(([k, v]) => {
            const r = el('div', 'detail-row');
            r.innerHTML = `<span class="k">${k}</span><span class="v">${v}</span>`;
            details.appendChild(r);
        });
        if (t.description) {
            const d = el('div', 'detail-section');
            d.innerHTML = `<h4>Description</h4><div>${esc(t.description)}</div>`;
            details.appendChild(d);
        }
        if (t.resolutionNotes) {
            const d = el('div', 'detail-section');
            d.innerHTML = `<h4>Resolution</h4><div>${esc(t.resolutionNotes)}</div>`;
            details.appendChild(d);
        }
        body.appendChild(details);

        // Actions
        const actions = el('div', 'detail-section');
        actions.innerHTML = '<h4>Actions</h4>';
        const bar = el('div', 'actions-bar');
        const quick = [
            ['APPROVED', 'Approve'], ['REJECTED', 'Reject'],
            ['IN_PROGRESS', 'Start'], ['CLOSED', 'Close'],
        ];
        quick.forEach(([status, text]) => {
            const b = el('button', 'btn sm', text);
            b.addEventListener('click', () => changeStatus(t.id, status));
            bar.appendChild(b);
        });
        const resolveBtn = el('button', 'btn sm primary', 'Resolve…');
        resolveBtn.addEventListener('click', () => resolveTicket(t.id));
        bar.appendChild(resolveBtn);
        actions.appendChild(bar);
        body.appendChild(actions);

        // History
        const hist = el('div', 'detail-section');
        hist.innerHTML = '<h4>History</h4>';
        if (history.length === 0) {
            hist.appendChild(el('div', 'empty', 'No history yet.'));
        } else {
            history.forEach((h) => {
                const item = el('div', 'history-item');
                item.innerHTML = `<strong>${esc(h.action)}</strong> — ${esc(h.comments || '')}
                    <div class="meta">${esc(h.actionBy)} · ${fmtDate(h.actionDate)}</div>`;
                hist.appendChild(item);
            });
        }
        body.appendChild(hist);

        $('#modal').classList.remove('hidden');
    } catch (e) {
        toast(e.message, 'err');
    }
}

async function changeStatus(id, status) {
    const actedById = defaultActor();
    try {
        await Api.updateStatus(id, { status, actedById, comments: `Set to ${status} from UI` });
        toast(`Ticket #${id} → ${label(status)}`);
        closeModal();
        await loadAll();
    } catch (e) {
        toast(e.message, 'err');
    }
}

async function resolveTicket(id) {
    const notes = prompt('Resolution notes:');
    if (!notes) return;
    try {
        await Api.resolveTicket(id, { resolutionNotes: notes, resolvedById: defaultActor() });
        toast(`Ticket #${id} resolved`);
        closeModal();
        await loadAll();
    } catch (e) {
        toast(e.message, 'err');
    }
}

function defaultActor() {
    // First manager/admin-ish employee, else first employee.
    return state.employees[0]?.id ?? 1;
}

function closeModal() { $('#modal').classList.add('hidden'); }

/* ---------- data loading ---------- */
async function loadEmployees() {
    try {
        state.employees = await Api.listEmployees();
        state.employeeById = {};
        state.employees.forEach((e) => (state.employeeById[e.id] = e));

        const creator = $('#creatorSelect');
        const assignee = $('#assigneeSelect');
        creator.innerHTML = '';
        assignee.innerHTML = '<option value="">— none —</option>';
        state.employees.forEach((e) => {
            creator.appendChild(new Option(`${e.name} (${e.email})`, e.id));
            assignee.appendChild(new Option(`${e.name} (${e.email})`, e.id));
        });
    } catch (e) {
        toast('Could not load employees: ' + e.message, 'err');
    }
}

async function loadTickets() {
    try {
        const data = await Api.listTickets(state.status, state.page, state.size);
        renderTickets(data);
    } catch (e) {
        toast('Could not load tickets: ' + e.message, 'err');
    }
}

async function loadStats() {
    try {
        renderStats(await Api.stats());
    } catch (e) {
        /* stats are non-critical */
    }
}

async function loadAll() {
    await Promise.all([loadStats(), loadTickets()]);
}

async function checkHealth() {
    const badge = $('#envBadge');
    try {
        const h = await Api.health();
        badge.textContent = h.status === 'UP' ? 'API: online' : `API: ${h.status}`;
    } catch {
        badge.textContent = 'API: offline';
    }
}

/* ---------- wiring ---------- */
function initFilters() {
    const sel = $('#statusFilter');
    STATUSES.forEach((s) => sel.appendChild(new Option(label(s), s)));
    sel.addEventListener('change', () => {
        state.status = sel.value;
        state.page = 0;
        loadTickets();
    });
}

function initEvents() {
    $('#refreshBtn').addEventListener('click', loadAll);
    $('#prevPage').addEventListener('click', () => { if (state.page > 0) { state.page--; loadTickets(); } });
    $('#nextPage').addEventListener('click', () => { if (state.page + 1 < state.totalPages) { state.page++; loadTickets(); } });
    $('#closeModal').addEventListener('click', closeModal);
    $('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });

    $('#createForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const f = e.target;
        const msg = $('#formMsg');
        msg.textContent = '';
        const payload = {
            title: f.title.value.trim(),
            description: f.description.value.trim() || null,
            priority: f.priority.value,
            category: f.category.value.trim() || null,
            createdById: Number(f.createdById.value),
            assigneeId: f.assigneeId.value ? Number(f.assigneeId.value) : null,
        };
        try {
            const created = await Api.createTicket(payload);
            msg.textContent = `Created ticket #${created.id}`;
            msg.className = 'form-msg ok';
            f.reset();
            f.priority.value = 'MEDIUM';
            await loadAll();
        } catch (err) {
            const fieldMsg = err.body?.fieldErrors
                ? Object.values(err.body.fieldErrors).join(', ')
                : err.message;
            msg.textContent = fieldMsg;
            msg.className = 'form-msg err';
        }
    });
}

async function init() {
    initFilters();
    initEvents();
    await checkHealth();
    await loadEmployees();
    await loadAll();
}

document.addEventListener('DOMContentLoaded', init);
