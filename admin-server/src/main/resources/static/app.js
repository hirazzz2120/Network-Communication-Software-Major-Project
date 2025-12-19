const REFRESH_INTERVAL_MS = 10_000;
const STREAM_RETRY_MS = 3000;

const metrics = {
    total: document.getElementById("metric-total"),
    online: document.getElementById("metric-online"),
    calls: document.getElementById("metric-calls"),
    messages: document.getElementById("metric-messages")
};
const $users = document.getElementById("users-body");
const $calls = document.getElementById("calls-body");
const $messages = document.getElementById("messages-body");
const $lastUpdated = document.getElementById("last-updated");
const $refreshBtn = document.getElementById("refresh-btn");
const supportsEventSource = typeof window.EventSource !== "undefined";
let stream;

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Request failed: ${response.status} ${response.statusText}`);
    }
    return response.json();
}

function renderUsers(users) {
    if (!users || !users.length) {
        $users.innerHTML = `<tr><td colspan="3" class="placeholder">暂无用户</td></tr>`;
        return;
    }
    $users.innerHTML = users.map(user => `
        <tr>
            <td>${user.username || user.id || '-'}</td>
            <td>${user.nickname || user.displayName || '-'}</td>
            <td>
                <span class="status-dot ${user.online || user.isOnline ? "status-online" : "status-offline"}">
                    ${user.online || user.isOnline ? "在线" : "离线"}
                </span>
            </td>
        </tr>
    `).join("");
}

function renderMessages(messages) {
    if (!messages || !messages.length) {
        $messages.innerHTML = `<tr><td colspan="4" class="placeholder">暂无消息记录</td></tr>`;
        return;
    }
    // 显示最近 20 条消息
    const recent = messages.slice(-20).reverse();
    $messages.innerHTML = recent.map(msg => `
        <tr>
            <td>${msg.sender || '-'}</td>
            <td>${msg.receiver || '-'}</td>
            <td>${truncate(msg.content, 50)}</td>
            <td>${formatTime(msg.timestamp)}</td>
        </tr>
    `).join("");
}

function renderCalls(calls) {
    if (!calls || !calls.length) {
        $calls.innerHTML = `<tr><td colspan="5" class="placeholder">暂无通话记录</td></tr>`;
        return;
    }
    $calls.innerHTML = calls.map(call => `
        <tr>
            <td>${call.id}</td>
            <td>${call.caller}</td>
            <td>${call.callee}</td>
            <td>${call.duration || '-'}</td>
            <td>${formatTime(call.startTime || call.startedAt)}</td>
        </tr>
    `).join("");
}

function renderStats(stats) {
    metrics.total.textContent = stats?.totalUsers ?? 0;
    metrics.online.textContent = stats?.onlineUsers ?? 0;
    metrics.calls.textContent = stats?.activeCalls ?? 0;
    metrics.messages.textContent = stats?.messagesToday ?? 0;
}

function formatTime(timeStr) {
    if (!timeStr) return "N/A";
    const date = new Date(timeStr);
    if (Number.isNaN(date.getTime())) {
        return timeStr;
    }
    return date.toLocaleString('zh-CN');
}

function truncate(str, len) {
    if (!str) return '-';
    return str.length > len ? str.substring(0, len) + '...' : str;
}

function showError(targetBody, message) {
    targetBody.innerHTML = `<tr><td colspan="${targetBody.dataset.columns || 3}" class="placeholder">${message}</td></tr>`;
}

function applySnapshot(snapshot) {
    if (!snapshot) {
        return;
    }
    renderStats(snapshot.stats);
    renderUsers(snapshot.users ?? []);
    renderMessages(snapshot.messages ?? []);
    renderCalls(snapshot.calls ?? []);
    const message = snapshot.timestamp
        ? `更新于 ${formatTime(snapshot.timestamp)}`
        : `更新于 ${new Date().toLocaleTimeString('zh-CN')}`;
    $lastUpdated.textContent = message;
}

async function refreshAll() {
    setLoading(true);
    try {
        const snapshot = await fetchJson("/api/dashboard");
        applySnapshot(snapshot);
    } catch (error) {
        console.error(error);
        renderStats({totalUsers: 0, onlineUsers: 0, activeCalls: 0, messagesToday: 0});
        $lastUpdated.textContent = "刷新失败";
        showError($users, "无法加载用户");
        showError($messages, "无法加载消息");
        showError($calls, "无法加载通话记录");
    } finally {
        setLoading(false);
    }
}

function setLoading(isLoading) {
    $refreshBtn.disabled = isLoading;
    $refreshBtn.textContent = isLoading ? "刷新中..." : "刷新数据";
}

function beginStream() {
    if (!supportsEventSource) {
        return;
    }
    if (stream) {
        stream.close();
    }
    stream = new EventSource("/api/stream");
    stream.addEventListener("dashboard", event => {
        const snapshot = JSON.parse(event.data);
        applySnapshot(snapshot);
    });
    stream.onerror = () => {
        $lastUpdated.textContent = "实时连接断开，正在重连...";
        stream.close();
        setTimeout(beginStream, STREAM_RETRY_MS);
    };
}

$refreshBtn.addEventListener("click", refreshAll);
if (supportsEventSource) {
    beginStream();
    refreshAll();
} else {
    refreshAll();
    setInterval(refreshAll, REFRESH_INTERVAL_MS);
}
