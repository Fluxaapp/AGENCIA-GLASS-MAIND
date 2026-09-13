(() => {
  "use strict";

  const script = document.currentScript;
  if (!script || script.dataset.fluxaMounted === "true") return;
  script.dataset.fluxaMounted = "true";

  const projectKey = String(script.dataset.projectKey || "").trim();
  const apiUrl =
    String(script.dataset.apiUrl || "").trim() ||
    "https://cybkcwdhhcvmvbyiylln.supabase.co/functions/v1/support-hub";
  const label = String(script.dataset.label || "Precisa de ajuda?").trim();
  const brand = String(script.dataset.brand || "Suporte").trim();
  const areas = String(
    script.dataset.areas || "Agenda,Clientes,Financeiro,Serviços,Acessos,Configurações,Outro",
  )
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
    .slice(0, 30);

  if (!/^fluxa_pub_[a-f0-9]{64}$/.test(projectKey)) {
    console.error("[Fluxa Support] data-project-key ausente ou inválida.");
    return;
  }

  const storageKey = `fluxa_support:${projectKey.slice(0, 22)}`;
  const state = {
    open: false,
    tab: "new",
    busy: false,
    notice: "",
    error: "",
    entries: readEntries(),
    activeId: null,
    detail: null,
  };

  const host = document.createElement("div");
  host.id = "fluxa-support-root";
  host.style.position = "fixed";
  host.style.right = "18px";
  host.style.bottom = "18px";
  host.style.zIndex = "2147483000";
  host.style.pointerEvents = "none";
  document.documentElement.appendChild(host);

  const root = host.attachShadow({ mode: "open" });
  const style = document.createElement("style");
  style.textContent = `
    :host{all:initial}*,*::before,*::after{box-sizing:border-box}button,input,textarea,select{font:inherit}
    .fx{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#edf9fc}
    button{cursor:pointer}.launcher{pointer-events:auto;display:flex;align-items:center;gap:9px;min-height:48px;padding:0 15px;border-radius:999px;border:1px solid rgba(82,214,239,.27);background:linear-gradient(145deg,#10252c,#071317);color:#eafaff;box-shadow:0 18px 50px rgba(0,0,0,.38);transition:.18s ease}
    .launcher:hover{transform:translateY(-2px);border-color:rgba(82,214,239,.48)}.mark{display:grid;place-items:center;width:29px;height:29px;border-radius:9px;background:rgba(74,211,237,.11);color:#66dff3;font-weight:800}.launcher strong{font-size:12px}
    .panel{pointer-events:auto;position:absolute;right:0;bottom:59px;width:min(410px,calc(100vw - 24px));max-height:min(700px,calc(100vh - 92px));display:flex;flex-direction:column;overflow:hidden;border:1px solid rgba(84,210,234,.2);border-radius:21px;background:rgba(6,16,20,.988);box-shadow:0 30px 90px rgba(0,0,0,.55);animation:open .17s ease-out}
    @keyframes open{from{opacity:0;transform:translateY(8px) scale(.98)}to{opacity:1;transform:none}}.head{padding:14px 15px 11px;border-bottom:1px solid rgba(255,255,255,.07);background:linear-gradient(180deg,rgba(57,199,225,.07),transparent)}.headrow{display:flex;align-items:center;justify-content:space-between;gap:10px}.brand{display:flex;align-items:center;gap:9px}.brand b{font-size:13px}.brand small{display:block;margin-top:2px;color:#70878f;font-size:9px}.close{width:31px;height:31px;border:0;border-radius:9px;background:rgba(255,255,255,.04);color:#8fa4ac;font-size:18px}.tabs{display:grid;grid-template-columns:1fr 1fr;gap:4px;margin-top:11px;padding:4px;border-radius:11px;background:rgba(255,255,255,.035)}.tab{border:0;border-radius:8px;padding:8px;background:transparent;color:#789099;font-size:10.5px;font-weight:700}.tab.on{background:rgba(68,207,233,.11);color:#d9f9ff}
    .body{overflow:auto;padding:14px 15px 16px}.intro{margin:0 0 11px;color:#8299a1;font-size:11px;line-height:1.5}.notice,.error{margin-bottom:10px;padding:9px 10px;border-radius:10px;font-size:10.5px;line-height:1.4}.notice{border:1px solid rgba(67,202,228,.15);background:rgba(67,202,228,.07);color:#b7ebf4}.error{border:1px solid rgba(236,91,91,.16);background:rgba(236,91,91,.07);color:#efb2b2}
    label{display:block;margin:10px 0 5px;color:#82979f;font-size:9.5px;font-weight:700;text-transform:uppercase;letter-spacing:.07em}input,textarea,select{width:100%;padding:9px 10px;border:1px solid rgba(255,255,255,.09);border-radius:10px;outline:0;background:rgba(255,255,255,.035);color:#eefbfe;font-size:11px}input:focus,textarea:focus,select:focus{border-color:rgba(75,211,238,.42)}textarea{min-height:90px;resize:vertical;line-height:1.45}select option{background:#0a171b}.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.file{display:flex;align-items:center;gap:8px;padding:9px 10px;border:1px dashed rgba(255,255,255,.13);border-radius:10px;color:#82979f;font-size:10px;cursor:pointer}.file input{display:none}.primary{width:100%;margin-top:13px;padding:10px 12px;border:1px solid rgba(79,218,243,.26);border-radius:11px;background:linear-gradient(135deg,#61e0f4,#45c5dd);color:#061316;font-size:11px;font-weight:800}.primary:disabled{opacity:.45;cursor:not-allowed}.ghost{padding:8px 10px;border:1px solid rgba(255,255,255,.1);border-radius:9px;background:rgba(255,255,255,.025);color:#9fb3ba;font-size:10px}.danger{color:#f0aaaa;border-color:rgba(239,97,97,.18)}
    .empty{padding:34px 9px;text-align:center;color:#71878f;font-size:11px;line-height:1.5}.request{width:100%;margin-bottom:7px;padding:10px;border:1px solid rgba(255,255,255,.075);border-radius:12px;background:rgba(255,255,255,.02);color:inherit;text-align:left}.request:hover{border-color:rgba(75,210,235,.2);background:rgba(75,210,235,.035)}.row{display:flex;align-items:center;justify-content:space-between;gap:8px}.code{color:#71858d;font:600 8.5px ui-monospace,SFMono-Regular,Menlo,monospace}.rtitle{margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#e8f7fa;font-size:11px;font-weight:700}.meta{margin-top:4px;color:#6f858d;font-size:9.5px}.pill{display:inline-flex;padding:3px 7px;border:1px solid rgba(78,210,235,.16);border-radius:999px;background:rgba(78,210,235,.07);color:#afe9f2;font-size:8.5px;font-weight:700}
    .back{padding:0;border:0;background:transparent;color:#72d8e9;font-size:10px}.section{margin-top:9px;padding:10px;border:1px solid rgba(255,255,255,.075);border-radius:12px;background:rgba(255,255,255,.018)}.section h4{margin:0 0 6px;color:#d9eaee;font-size:10.5px}.section p{margin:0;color:#8da2aa;font-size:10.5px;line-height:1.48;white-space:pre-wrap}.msg{margin-top:6px;padding:8px 9px;border-radius:9px;background:rgba(255,255,255,.03);color:#9db1b8;font-size:10px;line-height:1.45}.quote{margin-top:7px;padding:9px;border:1px solid rgba(76,211,237,.13);border-radius:11px;background:rgba(76,211,237,.04)}.quote b{font-size:11px}.money{margin-top:4px;color:#e8fbff;font-size:16px;font-weight:800}.actions{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.actions button{flex:1;min-width:85px}.small{margin-top:4px;color:#6e838b;font-size:9px}.loading{padding:25px;text-align:center;color:#778c94;font-size:10.5px}
    @media(max-width:520px){.panel{position:fixed;inset:9px 9px 72px;width:auto;max-height:none}.launcher{min-height:46px;padding:0 12px}.launcher strong{display:none}.grid{grid-template-columns:1fr}}
  `;
  root.appendChild(style);

  const wrap = document.createElement("div");
  wrap.className = "fx";
  root.appendChild(wrap);

  function readEntries() {
    try {
      const value = JSON.parse(localStorage.getItem(storageKey) || "[]");
      return Array.isArray(value) ? value : [];
    } catch {
      return [];
    }
  }

  function saveEntries() {
    try {
      localStorage.setItem(storageKey, JSON.stringify(state.entries.slice(0, 40)));
    } catch {}
  }

  function esc(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function money(cents) {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(cents || 0) / 100);
  }

  function when(value) {
    if (!value) return "";
    try {
      return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
    } catch {
      return "";
    }
  }

  const statusLabel = {
    new: "Novo",
    analysis: "Em análise",
    waiting_client: "Aguardando você",
    waiting_quote: "Preparando orçamento",
    waiting_approval: "Aguardando aprovação",
    quote_approved: "Orçamento aprovado",
    waiting_payment: "Aguardando pagamento",
    ready: "Pronto para iniciar",
    in_progress: "Em andamento",
    validation: "Aguardando validação",
    resolved: "Resolvido",
    archived: "Arquivado",
  };
  const typeLabel = {
    bug: "Erro / correção",
    adjustment: "Ajuste",
    improvement: "Melhoria",
    feature: "Nova funcionalidade",
    question: "Dúvida",
    other: "Outro",
  };
  const quoteStatusLabel = {
    draft: "Rascunho",
    sent: "Aguardando aprovação",
    approved: "Aprovado",
    rejected: "Não aprovado",
    superseded: "Substituído",
    expired: "Expirado",
  };

  async function api(action, data, token) {
    const response = await fetch(apiUrl, {
      method: "POST",
      credentials: "omit",
      headers: {
        "Content-Type": "application/json",
        "x-project-key": projectKey,
        ...(token ? { "x-client-token": token } : {}),
      },
      body: JSON.stringify({ action, data: data || {} }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || "Não foi possível concluir a solicitação.");
    return body;
  }

  async function upload(reportId, file, token) {
    const form = new FormData();
    form.append("data", JSON.stringify({ report_id: reportId, visibility: "client" }));
    form.append("file", file);
    const response = await fetch(apiUrl, {
      method: "POST",
      credentials: "omit",
      headers: {
        "x-project-key": projectKey,
        "x-client-token": token,
      },
      body: form,
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || "Não foi possível enviar o arquivo.");
    return body;
  }

  function context() {
    return {
      page: document.title || location.pathname,
      url: location.href,
      browser: navigator.userAgent,
      os: navigator.platform || "",
      resolution: `${window.innerWidth}x${window.innerHeight}`,
    };
  }

  function notify(message, error = false) {
    state.notice = error ? "" : message;
    state.error = error ? message : "";
    render();
  }

  function render() {
    wrap.innerHTML = `${state.open ? panel() : ""}<button class="launcher" type="button" data-act="toggle"><span class="mark">ϟ</span><strong>${esc(label)}</strong></button>`;
    bind();
  }

  function panel() {
    return `<section class="panel" role="dialog" aria-label="${esc(brand)}">
      <header class="head"><div class="headrow"><div class="brand"><span class="mark">ϟ</span><div><b>${esc(brand)}</b><small>Central de solicitações</small></div></div><button class="close" type="button" data-act="close">×</button></div>
      <div class="tabs"><button class="tab ${state.tab === "new" ? "on" : ""}" data-tab="new">Novo pedido</button><button class="tab ${state.tab === "mine" ? "on" : ""}" data-tab="mine">Meus pedidos${state.entries.length ? ` (${state.entries.length})` : ""}</button></div></header>
      <div class="body">${state.notice ? `<div class="notice">${esc(state.notice)}</div>` : ""}${state.error ? `<div class="error">${esc(state.error)}</div>` : ""}${state.tab === "new" ? newForm() : requestsView()}</div>
    </section>`;
  }

  function newForm() {
    const areaOptions = areas.map((area) => `<option value="${esc(area)}">${esc(area)}</option>`).join("");
    return `<form data-form="new"><p class="intro">Descreva o que precisa. O pedido chega diretamente à equipe responsável, junto com o contexto desta página.</p>
      <label>Seu nome</label><input name="name" maxlength="160" placeholder="Como podemos chamar você?">
      <label>Área do sistema</label><select name="area" required><option value="">Selecione onde está o problema...</option>${areaOptions}</select>
      <label>Título</label><input name="title" maxlength="200" required placeholder="Ex.: Ajustar data do agendamento">
      <label>Descrição</label><textarea name="description" maxlength="20000" required placeholder="Explique o que precisa ser corrigido ou desenvolvido..."></textarea>
      <div class="grid"><div><label>Tipo</label><select name="type"><option value="bug">Erro / correção</option><option value="adjustment">Ajuste</option><option value="improvement">Melhoria</option><option value="feature">Nova funcionalidade</option><option value="question">Dúvida</option><option value="other">Outro</option></select></div><div><label>Prioridade</label><select name="priority"><option value="normal">Normal</option><option value="low">Baixa</option><option value="high">Alta</option><option value="urgent">Urgente</option></select></div></div>
      <label>Anexo opcional</label><label class="file">📎 PNG, JPG, WEBP ou PDF · até 10 MB<input name="file" type="file" accept=".png,.jpg,.jpeg,.webp,.pdf"></label>
      <button class="primary" ${state.busy ? "disabled" : ""} type="submit">${state.busy ? "Enviando..." : "Enviar solicitação"}</button></form>`;
  }

  function requestsView() {
    if (state.activeId) return detailView();
    if (!state.entries.length) return `<div class="empty">Nenhum pedido salvo neste navegador ainda.<br>Se você enviar uma solicitação, ela aparecerá aqui.</div>`;
    return state.entries
      .map((item) => `<button class="request" type="button" data-open="${esc(item.id)}"><div class="row"><span class="code">${esc(code(item.id))}</span><span class="pill">${esc(item.status ? statusLabel[item.status] || item.status : "Abrir")}</span></div><div class="rtitle">${esc(item.title || "Solicitação")}</div><div class="meta">${esc(when(item.created_at))}</div></button>`)
      .join("");
  }

  function detailView() {
    if (state.busy && !state.detail) return `<button class="back" data-act="back">← Voltar</button><div class="loading">Carregando solicitação...</div>`;
    if (!state.detail) return `<button class="back" data-act="back">← Voltar</button><div class="empty">Não foi possível carregar este pedido.</div>`;
    const d = state.detail;
    const report = d.report || {};
    const messages = Array.isArray(d.messages) ? d.messages : [];
    const quotes = Array.isArray(d.quotes) ? d.quotes : [];
    const attachments = Array.isArray(d.attachments) ? d.attachments : [];
    const requestMeta = [
      report.category ? `Área: ${report.category}` : "",
      report.type ? `Tipo: ${typeLabel[report.type] || report.type}` : "",
    ].filter(Boolean).join(" · ");
    return `<button class="back" data-act="back">← Meus pedidos</button>
      <div class="row"><span class="code">${esc(code(report.id))}</span><span class="pill">${esc(statusLabel[report.status] || report.status || "Pedido")}</span></div>
      <div class="section"><h4>${esc(report.title || "Solicitação")}</h4>${requestMeta ? `<div class="small" style="margin-bottom:7px">${esc(requestMeta)}</div>` : ""}<p>${esc(report.description || "")}</p></div>
      ${quotes.length ? `<div class="section"><h4>Orçamento</h4>${quotes.map(quoteHtml).join("")}</div>` : ""}
      <div class="section"><h4>Conversa</h4>${messages.length ? messages.map((m) => `<div class="msg"><b>${m.kind === "from_client" ? "Você" : "Equipe"}</b><br>${esc(m.body)}</div>`).join("") : `<p>Ainda não há mensagens.</p>`}<form data-form="reply"><label>Enviar mensagem</label><textarea name="message" maxlength="10000" required placeholder="Escreva sua mensagem..."></textarea><button class="primary" ${state.busy ? "disabled" : ""} type="submit">Enviar mensagem</button></form></div>
      <div class="section"><h4>Anexos</h4>${attachments.length ? attachments.map((a) => `<div class="msg row"><span>${esc(a.name)}</span><button class="ghost" type="button" data-download="${esc(a.id)}">Abrir</button></div>`).join("") : `<p>Nenhum anexo.</p>`}<label class="file" style="margin-top:8px">📎 Adicionar arquivo<input data-upload type="file" accept=".png,.jpg,.jpeg,.webp,.pdf"></label></div>`;
  }

  function quoteHtml(q) {
    const canDecide = q.status === "sent";
    return `<div class="quote"><div class="row"><b>v${esc(q.version)} · ${esc(q.title)}</b><span class="pill">${esc(quoteStatusLabel[q.status] || q.status)}</span></div>${q.description ? `<div class="small">${esc(q.description)}</div>` : ""}<div class="money">${esc(money(q.amount_cents))}</div>${q.estimated_time ? `<div class="small">Prazo: ${esc(q.estimated_time)}</div>` : ""}${q.valid_until ? `<div class="small">Válido até ${esc(new Date(`${q.valid_until}T12:00:00`).toLocaleDateString("pt-BR"))}</div>` : ""}${canDecide ? `<div class="actions"><button class="ghost" type="button" data-quote="${esc(q.id)}" data-decision="approved">Aprovar</button><button class="ghost danger" type="button" data-quote="${esc(q.id)}" data-decision="rejected">Não aprovar</button></div>` : ""}</div>`;
  }

  function code(id) {
    return `SUP-${String(id || "").replace(/-/g, "").slice(0, 8).toUpperCase()}`;
  }

  function entry(id) {
    return state.entries.find((item) => item.id === id) || null;
  }

  function tokenFor(id) {
    return entry(id)?.token || null;
  }

  async function openDetail(id) {
    const token = tokenFor(id);
    if (!token) return notify("Este navegador não possui mais a credencial deste pedido.", true);
    state.activeId = id;
    state.detail = null;
    state.busy = true;
    render();
    try {
      const result = await api("detail", { report_id: id }, token);
      state.detail = result.data;
      const item = entry(id);
      if (item && result.data?.report) item.status = result.data.report.status;
      saveEntries();
    } catch (error) {
      state.error = error.message || "Não foi possível carregar o pedido.";
    } finally {
      state.busy = false;
      render();
    }
  }

  function bind() {
    wrap.querySelector('[data-act="toggle"]')?.addEventListener("click", () => {
      state.open = !state.open;
      state.error = "";
      state.notice = "";
      render();
    });
    wrap.querySelector('[data-act="close"]')?.addEventListener("click", () => {
      state.open = false;
      render();
    });
    wrap.querySelectorAll("[data-tab]").forEach((button) => button.addEventListener("click", () => {
      state.tab = button.dataset.tab;
      state.activeId = null;
      state.detail = null;
      state.error = "";
      state.notice = "";
      render();
    }));
    wrap.querySelector('[data-act="back"]')?.addEventListener("click", () => {
      state.activeId = null;
      state.detail = null;
      state.error = "";
      render();
    });
    wrap.querySelectorAll("[data-open]").forEach((button) => button.addEventListener("click", () => openDetail(button.dataset.open)));

    wrap.querySelector('[data-form="new"]')?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (state.busy) return;
      const form = event.currentTarget;
      const data = new FormData(form);
      const area = String(data.get("area") || "").trim();
      const title = String(data.get("title") || "").trim();
      const description = String(data.get("description") || "").trim();
      if (!area || !title || !description) return;
      state.busy = true;
      state.error = "";
      render();
      try {
        const result = await api("report_create", {
          title,
          description,
          category: area,
          type: String(data.get("type") || "other"),
          priority: String(data.get("priority") || "normal"),
          reporter_name: String(data.get("name") || "").trim(),
          context: context(),
        });
        if (!result.data?.id || !result.token) throw new Error("O pedido foi criado sem credencial de acompanhamento.");
        const record = {
          id: result.data.id,
          token: result.token,
          title: result.data.title || title,
          status: result.data.status || "new",
          created_at: result.data.created_at || new Date().toISOString(),
        };
        state.entries = [record, ...state.entries.filter((item) => item.id !== record.id)];
        saveEntries();
        const file = data.get("file");
        if (file instanceof File && file.size > 0) await upload(record.id, file, record.token);
        state.tab = "mine";
        state.notice = "Solicitação enviada para o suporte. Você pode acompanhar o andamento em Meus pedidos.";
        state.activeId = record.id;
        state.detail = null;
        await openDetail(record.id);
      } catch (error) {
        state.error = error.message || "Não foi possível enviar a solicitação.";
      } finally {
        state.busy = false;
        render();
      }
    });

    wrap.querySelector('[data-form="reply"]')?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!state.activeId || state.busy) return;
      const token = tokenFor(state.activeId);
      const data = new FormData(event.currentTarget);
      const message = String(data.get("message") || "").trim();
      if (!token || !message) return;
      state.busy = true;
      render();
      try {
        await api("message_create", { report_id: state.activeId, body: message }, token);
        await openDetail(state.activeId);
      } catch (error) {
        state.error = error.message || "Não foi possível enviar a mensagem.";
      } finally {
        state.busy = false;
        render();
      }
    });

    wrap.querySelectorAll("[data-quote]").forEach((button) => button.addEventListener("click", async () => {
      if (!state.activeId || state.busy) return;
      const token = tokenFor(state.activeId);
      if (!token) return;
      const decision = button.dataset.decision;
      const confirmed = window.confirm(
        decision === "approved"
          ? "Confirmar a aprovação deste orçamento?"
          : "Confirmar que você não aprova este orçamento?",
      );
      if (!confirmed) return;
      state.busy = true;
      render();
      try {
        await api("quote_decide", {
          report_id: state.activeId,
          quote_id: button.dataset.quote,
          decision,
          message: "",
        }, token);
        await openDetail(state.activeId);
      } catch (error) {
        state.error = error.message || "Não foi possível registrar sua decisão.";
      } finally {
        state.busy = false;
        render();
      }
    }));

    wrap.querySelector("[data-upload]")?.addEventListener("change", async (event) => {
      const file = event.currentTarget.files?.[0];
      if (!file || !state.activeId || state.busy) return;
      const token = tokenFor(state.activeId);
      if (!token) return;
      state.busy = true;
      render();
      try {
        await upload(state.activeId, file, token);
        await openDetail(state.activeId);
      } catch (error) {
        state.error = error.message || "Não foi possível enviar o arquivo.";
      } finally {
        state.busy = false;
        render();
      }
    });

    wrap.querySelectorAll("[data-download]").forEach((button) => button.addEventListener("click", async () => {
      if (!state.activeId || state.busy) return;
      const token = tokenFor(state.activeId);
      if (!token) return;
      try {
        const result = await api("attachment_get", {
          report_id: state.activeId,
          attachment_id: button.dataset.download,
        }, token);
        if (result.data?.url) window.open(result.data.url, "_blank", "noopener,noreferrer");
      } catch (error) {
        notify(error.message || "Não foi possível abrir o arquivo.", true);
      }
    }));
  }

  render();
})();