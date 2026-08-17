// API is same-origin when using run.py (port 3000) or Docker nginx proxy.
const API_BASE = '';

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

function showToast(msg, isError = false) {
  const toast = $('#toast');
  toast.textContent = msg;
  toast.style.borderColor = isError ? 'rgba(255,107,122,0.5)' : 'rgba(34,201,151,0.5)';
  toast.classList.remove('hidden');
  setTimeout(() => toast.classList.add('hidden'), 4000);
}

function scorePill(value) {
  if (value >= 75) return 'good';
  if (value >= 45) return 'fair';
  return 'poor';
}

function renderPlanTree(node, indent = 0) {
  const pad = '  '.repeat(indent);
  const time = node.actualTimeMs != null ? `${node.actualTimeMs.toFixed(1)}ms` : '';
  const rows = node.actualRows != null ? `${node.actualRows} rows` : '';
  const flag = node.nodeType === 'Seq Scan' ? ' 🔴' : '';
  let out = `${pad}${node.nodeType}${node.relationName ? ` on ${node.relationName}` : ''}  ${time}  ${rows}${flag}\n`;
  (node.children || []).forEach((c) => { out += renderPlanTree(c, indent + 1); });
  return out;
}

function renderMetrics(score) {
  if (!score) return;
  const ring = $('#score-ring');
  ring.textContent = score.overall;
  ring.style.setProperty('--score-deg', `${(score.overall / 100) * 360}deg`);

  const items = [
    ['Execution latency', score.executionLatency],
    ['Scan efficiency', score.scanEfficiency],
    ['Index utilization', score.indexUtilization],
    ['Cardinality accuracy', score.cardinalityAccuracy],
    ['Query frequency', score.queryFrequency],
  ];

  $('#metric-grid').innerHTML = items.map(([label, val]) => `
    <div class="metric-row">
      <span>${label}</span>
      <span class="pill ${scorePill(val)}">${Math.round(val)}</span>
    </div>
  `).join('');
}

function renderRecommendations(recs) {
  const container = $('#recommendations');
  if (!recs || recs.length === 0) {
    container.innerHTML = '<p class="muted">No issues detected. Query looks healthy.</p>';
    return;
  }

  container.innerHTML = recs.map((r) => `
    <div class="rec-card" data-id="${r.id}">
      <h4>${r.title}</h4>
      <div class="rec-meta">
        <span class="pill ${r.severity === 'HIGH' || r.severity === 'CRITICAL' ? 'poor' : 'fair'}">${r.severity}</span>
        <span class="pill good">${Math.round(r.confidence * 100)}% confidence</span>
        ${r.verified ? '<span class="pill good">VERIFIED</span>' : ''}
      </div>
      <p>${r.description}</p>
      ${r.evidence?.length ? `<ul class="evidence">${r.evidence.map(e => `<li><strong>${e.key}:</strong> ${e.value}</li>`).join('')}</ul>` : ''}
      ${r.suggestedSql ? `<pre class="code-block">${r.suggestedSql}</pre>` : ''}
      ${r.suggestedSql?.toUpperCase().includes('CREATE INDEX') ? `<button class="btn ghost small verify-btn" data-id="${r.id}">Verify optimization</button>` : ''}
    </div>
  `).join('');

  container.querySelectorAll('.verify-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      btn.textContent = 'Running experiment...';
      try {
        const res = await fetch(`${API_BASE}/api/recommendations/${btn.dataset.id}/verify`, { method: 'POST' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Verification failed');
        showToast(`Verified: ${data.improvementPercent.toFixed(1)}% improvement (${data.baselineTimeMs.toFixed(0)}ms → ${data.optimizedTimeMs.toFixed(0)}ms)`);
        btn.textContent = 'Verified ✓';
      } catch (e) {
        showToast(e.message, true);
        btn.disabled = false;
        btn.textContent = 'Verify optimization';
      }
    });
  });
}

async function analyzeQuery() {
  const sql = $('#sql-input').value.trim();
  const btn = $('#analyze-btn');
  btn.disabled = true;
  btn.textContent = 'Analyzing...';

  try {
    const res = await fetch(`${API_BASE}/api/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sql }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || 'Analysis failed');

    $('#results').classList.remove('hidden');
    renderMetrics(data.performanceScore);
    $('#plan-tree').textContent = renderPlanTree(data.planRoot);
    renderRecommendations(data.recommendations);
    showToast(`Analysis complete in ${data.executionTimeMs.toFixed(0)}ms`);
  } catch (e) {
    showToast(e.message, true);
  } finally {
    btn.disabled = false;
    btn.textContent = 'Run Analysis';
  }
}

async function loadDashboard() {
  try {
    const [overview, jobs] = await Promise.all([
      fetch(`${API_BASE}/api/dashboard/overview`).then(r => r.json()),
      fetch(`${API_BASE}/api/dashboard/jobs`).then(r => r.json()),
    ]);

    $('#overview-stats').innerHTML = `
      <div class="stat-card"><div class="label">Queries Analyzed</div><div class="value">${overview.queriesAnalyzed || 0}</div></div>
      <div class="stat-card"><div class="label">Unique Patterns</div><div class="value">${overview.uniqueFingerprints || 0}</div></div>
      <div class="stat-card"><div class="label">Slow Queries</div><div class="value">${overview.slowQueries || 0}</div></div>
      <div class="stat-card"><div class="label">High Priority Issues</div><div class="value">${overview.highPriorityIssues || 0}</div></div>
      <div class="stat-card"><div class="label">Target Tables</div><div class="value">${overview.targetTableCount || 0}</div></div>
    `;

    $('#recent-jobs').innerHTML = jobs.length ? jobs.map(j => `
      <div class="metric-row">
        <span class="mono">${j.sql}</span>
        <span class="pill ${j.status === 'COMPLETED' ? 'good' : j.status === 'FAILED' ? 'poor' : 'fair'}">${j.status}</span>
      </div>
    `).join('') : '<p class="muted">No jobs yet. Run an analysis first.</p>';
  } catch (e) {
    showToast('Failed to load dashboard', true);
  }
}

async function loadLeaderboard() {
  try {
    const rows = await fetch(`${API_BASE}/api/dashboard/queries`).then(r => r.json());
    $('#leaderboard-body').innerHTML = rows.length ? rows.map(r => `
      <tr>
        <td class="mono">${r.normalizedSql}</td>
        <td>${r.executions}</td>
        <td>${r.avgMs.toFixed(1)}</td>
        <td>${r.totalMs.toFixed(0)}</td>
      </tr>
    `).join('') : '<tr><td colspan="4">No query patterns yet.</td></tr>';
  } catch (e) {
    showToast('Failed to load leaderboard', true);
  }
}

$$('.nav-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    $$('.nav-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    $$('.view').forEach(v => v.classList.remove('active'));
    $(`#view-${btn.dataset.view}`).classList.add('active');
    if (btn.dataset.view === 'dashboard') loadDashboard();
    if (btn.dataset.view === 'leaderboard') loadLeaderboard();
  });
});

$('#analyze-btn').addEventListener('click', analyzeQuery);
$('#sample-btn').addEventListener('click', () => {
  $('#sql-input').value = `SELECT o.*, c.name, p.name AS product_name
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN products p ON o.product_id = p.id
WHERE c.country = 'IN'
AND o.created_at > '2025-01-01';`;
});
