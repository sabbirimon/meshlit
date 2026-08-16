// Phase Hivemind-1 — Meshlit Cluster browser chat client.
// Talks to /v1/chat/completions (OpenAI-compatible SSE) and
// /v1/health for the cluster state. No external dependencies;
// 200 lines of plain JS that work in any modern browser.

const $ = (id) => document.getElementById(id);

const messagesEl = $("messages");
const composerEl = $("composer");
const promptEl = $("prompt");
const sendBtn = $("send-btn");
const modelPicker = $("model-picker");
const membersEl = $("members");
const hostNameEl = $("host-name");
const kubeScoreEl = $("kube-score");
const reelectBtn = $("reelect-btn");

let currentModel = null;
let streamingEl = null;

function drawOrb() {
  const c = $("orb-bg");
  const ctx = c.getContext("2d");
  const w = (c.width = window.innerWidth * devicePixelRatio);
  const h = (c.height = window.innerHeight * devicePixelRatio);
  const cx = w / 2, cy = h / 2;
  const t0 = performance.now();
  function frame() {
    const t = (performance.now() - t0) / 1000;
    const r = Math.min(w, h) * 0.45;
    ctx.fillStyle = "rgba(9,12,23,1)";
    ctx.fillRect(0, 0, w, h);
    for (let i = 0; i < 3; i++) {
      const phase = t * 0.15 + i * 0.33;
      const grad = ctx.createRadialGradient(
        cx + Math.cos(phase * 6.28) * r * 0.15,
        cy + Math.sin(phase * 6.28) * r * 0.15,
        r * 0.05,
        cx, cy, r,
      );
      const colors = [
        ["rgba(34,211,238,0.18)", "rgba(34,211,238,0)"],
        ["rgba(168,85,247,0.18)", "rgba(168,85,247,0)"],
        ["rgba(16,185,129,0.18)", "rgba(16,185,129,0)"],
      ];
      grad.addColorStop(0, colors[i][0]);
      grad.addColorStop(1, colors[i][1]);
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
    }
    requestAnimationFrame(frame);
  }
  frame();
}

function appendMessage(role, text) {
  const div = document.createElement("div");
  div.className = `msg ${role}`;
  div.textContent = text;
  messagesEl.appendChild(div);
  messagesEl.scrollTop = messagesEl.scrollHeight;
  return div;
}

async function loadModels() {
  try {
    const resp = await fetch("/v1/models");
    if (!resp.ok) throw new Error("models fetch failed");
    const data = await resp.json();
    modelPicker.innerHTML = "";
    for (const m of data.data ?? []) {
      const opt = document.createElement("option");
      opt.value = m.id;
      opt.textContent = m.id;
      modelPicker.appendChild(opt);
    }
    if (modelPicker.options.length > 0) {
      currentModel = modelPicker.options[0].value;
      modelPicker.value = currentModel;
    }
  } catch (e) {
    modelPicker.innerHTML = `<option>unavailable</option>`;
  }
}

async function loadHealth() {
  try {
    const resp = await fetch("/v1/health");
    if (!resp.ok) throw new Error("health fetch failed");
    const data = await resp.json();
    hostNameEl.textContent = data.clusterHostOfRecord || "self";
    kubeScoreEl.textContent = (data.kubeScore ?? 0).toFixed(2);
    renderMembers(data.clusterMembers ?? []);
  } catch (e) {
    hostNameEl.textContent = "offline";
    kubeScoreEl.textContent = "·";
  }
}

function renderMembers(members) {
  membersEl.innerHTML = "";
  if (!Array.isArray(members) || members.length === 0) {
    membersEl.innerHTML = `<li class="muted">No cluster members</li>`;
    return;
  }
  for (const m of members) {
    const li = document.createElement("li");
    li.className = `member${m.role === "host" ? " host" : ""}`;
    li.innerHTML = `
      <span class="member-name"><span class="pip"></span>${m.nodeId ?? m.id ?? "?"}</span>
      <span class="member-score">${(m.score ?? 0).toFixed(2)} · ${m.tier ?? "?"}</span>
    `;
    membersEl.appendChild(li);
  }
}

async function sendPrompt(prompt) {
  if (!currentModel) await loadModels();
  appendMessage("user", prompt);
  streamingEl = appendMessage("assistant streaming", "");
  sendBtn.disabled = true;
  try {
    const resp = await fetch("/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: currentModel ?? "auto",
        messages: [{ role: "user", content: prompt }],
        stream: true,
      }),
    });
    if (!resp.ok || !resp.body) {
      streamingEl.textContent = `(error: ${resp.status})`;
      streamingEl.classList.remove("streaming");
      return;
    }
    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buf = "";
    let acc = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      const lines = buf.split("\n");
      buf = lines.pop() ?? "";
      for (const line of lines) {
        if (!line.startsWith("data: ")) continue;
        const payload = line.slice(6).trim();
        if (payload === "[DONE]") continue;
        try {
          const obj = JSON.parse(payload);
          const delta = obj.choices?.[0]?.delta?.content ?? "";
          acc += delta;
          streamingEl.textContent = acc;
        } catch {}
      }
    }
    streamingEl.classList.remove("streaming");
  } catch (e) {
    streamingEl.textContent = `(error: ${e.message})`;
    streamingEl.classList.remove("streaming");
  } finally {
    sendBtn.disabled = false;
    promptEl.focus();
  }
}

composerEl.addEventListener("submit", (e) => {
  e.preventDefault();
  const text = promptEl.value.trim();
  if (!text) return;
  promptEl.value = "";
  sendPrompt(text);
});

reelectBtn.addEventListener("click", async () => {
  try {
    await fetch("/v1/cluster/yield", { method: "POST" });
  } catch {}
  loadHealth();
});

promptEl.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    composerEl.requestSubmit();
  }
});

window.addEventListener("resize", () => {
  const c = $("orb-bg");
  c.width = window.innerWidth * devicePixelRatio;
  c.height = window.innerHeight * devicePixelRatio;
});

drawOrb();
loadModels();
loadHealth();
setInterval(loadHealth, 10_000);