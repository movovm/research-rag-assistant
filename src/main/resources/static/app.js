const state = {
    sessionId: `demo-${Date.now()}`,
    userId: 'demo-user',
    busy: false,
    documents: []
};

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

document.addEventListener('DOMContentLoaded', async () => {
    if (window.lucide) lucide.createIcons();
    bindNavigation();
    bindChat();
    bindDialogs();
    bindDebug();
    await refreshDocuments();
});

function bindNavigation() {
    $$('.tab').forEach(tab => tab.addEventListener('click', () => showView(tab.dataset.view)));
}

function showView(name) {
    $$('.tab').forEach(tab => tab.classList.toggle('active', tab.dataset.view === name));
    $$('.view').forEach(view => view.classList.remove('active'));
    $(`#${name}View`).classList.add('active');
    if (name === 'documents') renderDocumentTable();
}

function bindChat() {
    const input = $('#questionInput');
    input.addEventListener('input', () => {
        $('#charCount').textContent = `${input.value.length} / 1000`;
        input.style.height = 'auto';
        input.style.height = `${Math.min(input.scrollHeight, 130)}px`;
    });
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            $('#chatForm').requestSubmit();
        }
    });
    $('#chatForm').addEventListener('submit', event => {
        event.preventDefault();
        sendQuestion(input.value.trim());
    });
    $$('.prompt-chip').forEach(button => button.addEventListener('click', () => sendQuestion(button.textContent.trim())));
    $('#clearButton').addEventListener('click', clearConversation);
}

async function sendQuestion(question) {
    if (!question || state.busy) return;
    state.busy = true;
    $('#sendButton').disabled = true;
    $('#questionInput').value = '';
    $('#charCount').textContent = '0 / 1000';
    $('.welcome')?.remove();
    appendUserMessage(question);
    const answerNode = appendAssistantMessage();
    const started = performance.now();
    try {
        const response = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'Accept': 'text/event-stream'},
            body: JSON.stringify({sessionId: state.sessionId, userId: state.userId, question})
        });
        if (!response.ok) throw new Error(`请求失败：${response.status}`);
        await consumeSse(response, (event, data) => {
            if (event === 'context') renderTrace(data, performance.now() - started);
            if (event === 'token') {
                answerNode.textContent += data.token;
                $('#messages').scrollTop = $('#messages').scrollHeight;
            }
        });
    } catch (error) {
        answerNode.textContent = `无法完成问答：${error.message}`;
        toast('问答请求失败');
    } finally {
        state.busy = false;
        $('#sendButton').disabled = false;
        $('#questionInput').focus();
    }
}

async function consumeSse(response, handler) {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
        const {value, done} = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, {stream: true});
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop();
        blocks.forEach(block => {
            let event = 'message';
            let data = '';
            block.split(/\r?\n/).forEach(line => {
                if (line.startsWith('event:')) event = line.slice(6).trim();
                if (line.startsWith('data:')) data += line.slice(5).trim();
            });
            if (data) handler(event, JSON.parse(data));
        });
    }
}

function appendUserMessage(question) {
    const node = document.createElement('div');
    node.className = 'message user';
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = question;
    node.appendChild(bubble);
    $('#messages').appendChild(node);
}

function appendAssistantMessage() {
    const node = document.createElement('div');
    node.className = 'message assistant';
    node.innerHTML = '<div class="avatar"><i data-lucide="sparkles"></i></div><div class="assistant-content"></div>';
    $('#messages').appendChild(node);
    if (window.lucide) lucide.createIcons({nodes: [node]});
    return node.querySelector('.assistant-content');
}

function renderTrace(context, elapsed) {
    $('#traceEmpty').hidden = true;
    $('#traceContent').hidden = false;
    $('#traceTime').textContent = `${Math.round(elapsed)} ms`;
    $('#pipeline').replaceChildren(...context.stages.map(stage => {
        const item = document.createElement('li');
        item.textContent = stage;
        return item;
    }));
    const rewrite = $('#queryRewrite');
    rewrite.replaceChildren();
    const label = document.createElement('strong');
    label.textContent = context.rewrittenQuery === context.originalQuestion ? 'QUERY · 无需改写' : 'QUERY REWRITE';
    rewrite.append(label, document.createTextNode(context.rewrittenQuery));
    $('#evidenceList').replaceChildren(...context.evidence.map(renderEvidence));
}

function renderEvidence(item) {
    const node = document.createElement('article');
    node.className = 'evidence-item';
    const max = value => Math.max(3, Math.min(100, Math.round(value * 100)));
    node.innerHTML = `
        <div class="evidence-top"><strong></strong><span class="rank">#${item.rank}</span></div>
        <p></p>
        ${scoreRow('融合', max(item.combinedScore), item.combinedScore.toFixed(2))}
        ${scoreRow('Dense', max(item.denseScore), item.denseScore.toFixed(2))}`;
    node.querySelector('strong').textContent = item.chunk.source;
    node.querySelector('p').textContent = item.chunk.content;
    return node;
}

function scoreRow(label, width, value) {
    return `<div class="score-row"><span>${label}</span><div class="score-track"><div class="score-fill" style="width:${width}%"></div></div><b>${value}</b></div>`;
}

async function clearConversation() {
    await fetch(`/api/chat/sessions/${encodeURIComponent(state.sessionId)}`, {method: 'DELETE'});
    state.sessionId = `demo-${Date.now()}`;
    $('#messages').innerHTML = '<div class="welcome"><div class="welcome-icon"><i data-lucide="check"></i></div><h2>新会话已创建</h2><p>短期上下文已清空，知识库和长期记忆保持不变。</p></div>';
    $('#traceContent').hidden = true;
    $('#traceEmpty').hidden = false;
    $('#traceTime').textContent = '待运行';
    if (window.lucide) lucide.createIcons();
    toast('当前会话已清空');
}

async function refreshDocuments() {
    const [documents, stats] = await Promise.all([
        fetch('/api/documents').then(response => response.json()),
        fetch('/api/documents/stats').then(response => response.json())
    ]);
    state.documents = documents;
    $('#docCount').textContent = stats.documents;
    $('#chunkCount').textContent = stats.chunks;
    const items = documents.slice(0, 5).map(documentItem);
    $('#sidebarDocuments').replaceChildren(...items);
    renderDocumentTable();
    if (window.lucide) lucide.createIcons();
}

function documentItem(doc) {
    const node = document.createElement('div');
    node.className = 'document-item';
    node.innerHTML = '<div class="document-icon"><i data-lucide="file-text"></i></div><div><strong></strong><span></span></div>';
    node.querySelector('strong').textContent = doc.source;
    node.querySelector('span').textContent = `${doc.documentType} · ${doc.chunks} 块`;
    return node;
}

function renderDocumentTable() {
    const table = $('#documentTable');
    const header = document.createElement('div');
    header.className = 'document-row header';
    header.innerHTML = '<span>文件名</span><span>类型</span><span>所属项目</span><span>语义块</span>';
    const rows = state.documents.map(doc => {
        const row = document.createElement('div');
        row.className = 'document-row';
        [doc.source, doc.documentType, doc.project, `${doc.chunks} chunks`].forEach(value => {
            const cell = document.createElement('span'); cell.textContent = value; row.appendChild(cell);
        });
        return row;
    });
    table.replaceChildren(header, ...rows);
}

function bindDialogs() {
    const upload = $('#uploadDialog');
    ['#uploadButton', '#sidebarUploadButton', '#documentsUploadButton'].forEach(selector => $(selector).addEventListener('click', () => upload.showModal()));
    $$('.dialog-close').forEach(button => button.addEventListener('click', () => upload.close()));
    $('#uploadForm').addEventListener('submit', uploadDocument);
    const memory = $('#memoryDialog');
    $('#memoryButton').addEventListener('click', async () => { await refreshMemories(); memory.showModal(); });
    $$('.memory-close').forEach(button => button.addEventListener('click', () => memory.close()));
    $('#memoryForm').addEventListener('submit', addMemory);
}

async function uploadDocument(event) {
    event.preventDefault();
    const file = $('#fileInput').files[0];
    if (!file) return;
    const data = new FormData();
    data.append('file', file);
    data.append('documentType', $('#documentType').value);
    data.append('project', $('#projectName').value);
    $('#uploadStatus').textContent = '正在解析并建立索引...';
    const response = await fetch('/api/documents', {method: 'POST', body: data});
    if (!response.ok) {
        const error = await response.json();
        $('#uploadStatus').textContent = error.message || '上传失败';
        return;
    }
    const result = await response.json();
    $('#uploadStatus').textContent = `已生成 ${result.chunks} 个语义块`;
    await refreshDocuments();
    setTimeout(() => $('#uploadDialog').close(), 700);
    toast('文档已加入知识库');
}

async function refreshMemories() {
    const values = await fetch(`/api/memories?userId=${state.userId}`).then(response => response.json());
    $('#memoryList').replaceChildren(...values.map(value => {
        const node = document.createElement('div');
        node.className = 'memory-entry';
        node.innerHTML = '<strong></strong><span></span>';
        node.querySelector('strong').textContent = value.label;
        node.querySelector('span').textContent = value.content;
        return node;
    }));
}

async function addMemory(event) {
    event.preventDefault();
    await fetch('/api/memories', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({userId: state.userId, label: $('#memoryLabel').value, content: $('#memoryContent').value})
    });
    $('#memoryLabel').value = '';
    $('#memoryContent').value = '';
    await refreshMemories();
    toast('长期记忆已添加');
}

function bindDebug() {
    $('#debugForm').addEventListener('submit', async event => {
        event.preventDefault();
        const query = $('#debugInput').value.trim();
        if (!query) return;
        const results = await fetch(`/api/retrieval/debug?query=${encodeURIComponent(query)}`).then(response => response.json());
        $('#debugResults').replaceChildren(...results.map(renderDebugResult));
    });
}

function renderDebugResult(item) {
    const node = document.createElement('article');
    node.className = 'debug-card';
    node.innerHTML = `
        <div class="debug-rank">${String(item.rank).padStart(2, '0')}</div>
        <div class="debug-copy"><strong></strong><p></p></div>
        <div class="debug-scores">
            ${scoreRow('BM25', scorePercent(item.bm25Score), item.bm25Score.toFixed(2))}
            ${scoreRow('Dense', scorePercent(item.denseScore), item.denseScore.toFixed(2))}
            ${scoreRow('融合', scorePercent(item.combinedScore), item.combinedScore.toFixed(2))}
        </div>`;
    node.querySelector('strong').textContent = `${item.chunk.source} · ${item.chunk.documentType}`;
    node.querySelector('p').textContent = item.chunk.content;
    return node;
}

function scorePercent(value) { return Math.max(3, Math.min(100, Math.round(value * 100))); }

let toastTimer;
function toast(message) {
    const node = $('#toast');
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove('show'), 2200);
}
