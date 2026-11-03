// ============================================================
// Действия с документами: список, карточка, регистрация (модалка),
// действия (в т.ч. REVIEW → EXECUTE), файлы, история (таблица), печать.
// ============================================================

var ACTIONS_MAP = {
    'CREATE':   'Создание документа',
    'EDIT':     'Редактирование',
    'ROUTE':    'Создание маршрута',
    'ADD_FILE': 'Добавление файла',
    'APPROVE':  'Согласование',
    'SIGN':     'Подписание',
    'REGISTER': 'Регистрация',
    'REVIEW':   'Рассмотрение',
    'EXECUTE':  'Исполнение',
    'READ':     'Ознакомление',
    'REJECT':   'Отклонение',
    'REDIRECT': 'Перенаправление'
};

var currentDocData = null;
// В новой схеме БД "ADMINISTRATIVE" и "ORGANIZATIONAL" — это КОДЫ КАТЕГОРИЙ (doc_categories)
var currentDocCategoryCode = null;
var pendingNewFile = null;

async function loadTasks(taskCode) {
    var task = TASKS.find(function (t) { return t.code === taskCode; });
    var statusFilter = document.getElementById('taskStatusFilter').value;

    document.getElementById('tableContainer').innerHTML = '<div style="padding:32px;text-align:center;">Загрузка...</div>';
    try {
        var url = '/api/documents/tasks?action=' + encodeURIComponent(task.action) + '&status=' + encodeURIComponent(statusFilter);
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка загрузки задач (HTTP ' + resp.status + ')');
        }
        var data = await resp.json();
        if (!Array.isArray(data)) throw new Error('Некорректный формат данных');
        currentData = data;
        applySearch();
    } catch (e) {
        document.getElementById('tableContainer').innerHTML =
            '<div class="muted" style="padding:32px;text-align:center;color:#b00020;">' + escapeHtml(e.message) + '</div>';
    }
}

async function loadDocuments(categoryCode) {
    document.getElementById('tableContainer').innerHTML = '<div style="padding:32px;text-align:center;">Загрузка...</div>';
    try {
        // бэкенд: /api/documents/my?type=... → findMyDocsByCategoryCode
        var resp = await fetch('/api/documents/my?type=' + encodeURIComponent(categoryCode),
            { headers: { 'Authorization': 'Bearer ' + token } });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка загрузки документов (HTTP ' + resp.status + ')');
        }
        var data = await resp.json();
        if (!Array.isArray(data)) throw new Error('Некорректный формат данных');
        data = data.map(function (d) {
            return Object.assign({}, d, {
                authorName: d.author ? (d.author.lastName + ' ' + (d.author.firstName ? d.author.firstName.charAt(0) : '') + '.') : '—'
            });
        });
        currentData = data;
        applySearch();
    } catch (e) {
        document.getElementById('tableContainer').innerHTML =
            '<div class="muted" style="padding:32px;text-align:center;color:#b00020;">' + escapeHtml(e.message) + '</div>';
    }
}

async function openDocumentView(id) {
    currentDocId = id;
    currentDocData = null;
    document.getElementById('standardView').style.display = 'none';
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('documentView').style.display = 'block';

    document.getElementById('btnRegisterDoc').style.display = 'none';
    document.getElementById('btnEditDoc').style.display = 'none';
    document.getElementById('btnSaveDoc').style.display = 'none';
    document.getElementById('btnAddFileVersion').style.display = 'none';
    document.getElementById('mv_title').disabled = true;
    document.getElementById('mv_content').disabled = true;

    // Автоматически отмечаем прочтение задачи текущего исполнителя.
    // Если у пользователя есть задача в статусе «Не прочитано» (PENDING)
    // и предыдущие этапы завершены — она переходит в «В процессе» (IN_PROGRESS).
    try {
        await fetch('/api/documents/' + id + '/mark-read', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
    } catch (_) { /* не критично */ }

    var fromTasks = currentSection && currentSection.type === 'task';
    switchDocTab(fromTasks ? 'route' : 'doc');

    try {
        var resp = await fetch('/api/documents/' + id, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка загрузки документа');
        }
        var data = await resp.json();
        if (!data || !data.document) throw new Error('Документ не найден или нет доступа');

        var doc = data.document;
        currentDocData = doc;
        // код категории — для определения ветки REVIEW → EXECUTE и т.п.
        currentDocCategoryCode = doc.category ? doc.category.code : null;
        var files = data.files || [];

        currentDocAuthor = doc.author ? (doc.author.lastName + ' ' + (doc.author.firstName ? doc.author.firstName.charAt(0) : '') + '.' +
            (doc.author.middleName ? ' ' + doc.author.middleName.charAt(0) + '.' : '')) : '—';

        document.getElementById('docViewTitle').textContent = doc.title || 'Документ';
        document.getElementById('mv_title').value = doc.title || '—';
        document.getElementById('mv_content').value = doc.content || '—';
        document.getElementById('mv_type').value = doc.documentType ? doc.documentType.name : '—';
        document.getElementById('mv_category').value = doc.category ? doc.category.name : '—';
        document.getElementById('mv_nomenclature').value = doc.nomenclature
            ? (doc.nomenclature.caseCode + ' — ' + doc.nomenclature.title)
            : '—';
        document.getElementById('mv_reg').value = doc.registrationNumber || 'Не зарегистрирован';
        document.getElementById('mv_status').value = STATUS_MAP[doc.status] || doc.status;
        document.getElementById('mv_author').value = doc.author ? (doc.author.lastName + ' ' + doc.author.firstName + ' ' + (doc.author.middleName || '')) : '—';
        document.getElementById('mv_created').value = doc.createdAt ? new Date(doc.createdAt).toLocaleString('ru-RU') : '—';

        if (data.canRegister) document.getElementById('btnRegisterDoc').style.display = 'inline-block';
        if (data.canEdit) {
            document.getElementById('btnEditDoc').style.display = 'inline-block';
            document.getElementById('btnAddFileVersion').style.display = 'inline-block';
        }

        var filesContainer = document.getElementById('mv_files');
        if (files.length > 0) {
            filesContainer.innerHTML = files.map(function (f) {
                return '<div class="file-row">' +
                    '<span>📄 ' + escapeHtml(f.fileName) + ' <span class="muted">(Версия ' + f.versionNumber + ')</span></span>' +
                    '<a class="action-link" href="/api/documents/' + id + '/files/' + f.fileId + '/download" ' +
                    'onclick="downloadWithToken(event, this)">Скачать</a>' +
                    '</div>';
            }).join('');
        } else {
            filesContainer.innerHTML = '<div class="muted">Файлы отсутствуют</div>';
        }
    } catch (e) {
        alert(e.message);
        closeDocumentView();
        return;
    }

    // Загрузка маршрута
    try {
        var respR = await fetch('/api/documents/' + id + '/routes', { headers: { 'Authorization': 'Bearer ' + token } });
        var container = document.getElementById('routeTimeline');

        if (respR.ok) {
            var routes = await respR.json();
            if (!Array.isArray(routes) || routes.length === 0) {
                container.innerHTML = '<div class="muted" style="text-align:center;">Маршрут не задан</div>';
            } else {
                routes.sort(function (a, b) { return a.stepNumber - b.stepNumber; });
                container.innerHTML = routes.map(function (r, index) {
                    var sentDate = r.createdAt ? new Date(r.createdAt).toLocaleString('ru-RU') : '—';
                    var deadlineDate = r.deadline ? new Date(r.deadline).toLocaleString('ru-RU') : 'Не задан';
                    var completedDate = r.completedAt ? new Date(r.completedAt).toLocaleString('ru-RU') : '—';
                    var statusText = ROUTE_STATUS_MAP[r.status] || r.status;

                    var toUser = r.assignedUser
                        ? r.assignedUser.lastName + ' ' + (r.assignedUser.firstName ? r.assignedUser.firstName.charAt(0) : '') + '.' +
                          (r.assignedUser.middleName ? ' ' + r.assignedUser.middleName.charAt(0) + '.' : '')
                        : 'Не назначен';
                    var fromUser = index === 0
                        ? currentDocAuthor
                        : (routes[index - 1].assignedUser
                            ? routes[index - 1].assignedUser.lastName + ' ' + (routes[index - 1].assignedUser.firstName ? routes[index - 1].assignedUser.firstName.charAt(0) : '') + '.'
                            : 'Предыдущий этап');

                    var taskType = (r.stage ? r.stage.name : 'Этап') + ' → ' + (r.action ? r.action.name : 'Действие');
                    var indent = index * 40;

                    var isMyActiveTask = r.assignedUser
                        && r.assignedUser.userId === currentUserId
                        && (r.status === 'PENDING' || r.status === 'IN_PROGRESS');

                    var taskActionsHtml = '';
                    if (isMyActiveTask) {
                        // Для REVIEW распорядительного документа — "Рассмотреть" с выбором исполнителя
                        var mainBtnLabel = r.action.name;
                        if (r.action.code === 'REVIEW' && currentDocCategoryCode === 'ADMINISTRATIVE') {
                            mainBtnLabel = 'Рассмотреть (назначить исполнителя)';
                        }
                        taskActionsHtml =
                            '<div class="task-actions">' +
                                '<button class="button button--primary" onclick="openActionModal(' + r.routeId + ', \'' + r.action.code + '\', \'' + mainBtnLabel + '\')">' + mainBtnLabel + '</button>' +
                                '<button class="button button--secondary" style="color: #b00020; border-color: #b00020;" onclick="openActionModal(' + r.routeId + ', \'REJECT\', \'Отклонить\')">Отклонить</button>' +
                                '<button class="button button--secondary" onclick="openActionModal(' + r.routeId + ', \'REDIRECT\', \'Перенаправить\')">Перенаправить</button>' +
                            '</div>';
                    }

                    return '<div class="step-card ' + (isMyActiveTask ? 'active-task' : '') + '" style="margin-left: ' + indent + 'px;">' +
                        '<div class="step-badge">' + r.stepNumber + '</div>' +
                        '<div class="step-header">' +
                            '<div class="step-title">' + taskType + '</div>' +
                            '<span class="step-status ' + r.status + '">' + statusText + '</span>' +
                        '</div>' +
                        '<div class="step-meta">' +
                            '<div class="meta-item"><span class="meta-label">Отправил</span><span class="meta-value">' + fromUser + '</span></div>' +
                            '<div class="meta-item"><span class="meta-label">Кому</span><span class="meta-value">' + toUser + '</span></div>' +
                            '<div class="meta-item"><span class="meta-label">Когда отправлено</span><span class="meta-value">' + sentDate + '</span></div>' +
                            '<div class="meta-item"><span class="meta-label">Срок</span><span class="meta-value">' + deadlineDate + '</span></div>' +
                            '<div class="meta-item" style="grid-column: span 2;"><span class="meta-label">Дата выполнения</span><span class="meta-value">' + completedDate + '</span></div>' +
                        '</div>' +
                        (r.instruction ? '<div class="step-instruction">' + escapeHtml(r.instruction) + '</div>' : '') +
                        (r.comment ? '<div class="step-comment">💬 Комментарий: ' + escapeHtml(r.comment) + '</div>' : '') +
                        taskActionsHtml +
                    '</div>';
                }).join('');
            }
        } else {
            container.innerHTML = '<div class="muted" style="text-align:center;color:#b00020;">Не удалось загрузить маршрут</div>';
        }
    } catch (e) {
        document.getElementById('routeTimeline').innerHTML = '<div class="muted" style="text-align:center;color:#b00020;">Ошибка загрузки маршрута</div>';
    }

    // Загрузка истории
    try {
        var respH = await fetch('/api/documents/' + id + '/history', { headers: { 'Authorization': 'Bearer ' + token } });
        var tbody = document.getElementById('historyTableBody');

        if (respH.ok) {
            var history = await respH.json();
            renderHistoryTable(history || []);
        } else {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#b00020;">Не удалось загрузить историю</td></tr>';
        }
    } catch (e) {
        document.getElementById('historyTableBody').innerHTML =
            '<tr><td colspan="5" style="text-align:center;color:#b00020;">Ошибка загрузки истории</td></tr>';
    }
}

function renderHistoryTable(history) {
    var tbody = document.getElementById('historyTableBody');
    if (!history || history.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">Действий пока нет</td></tr>';
        return;
    }
    tbody.innerHTML = history.map(function (h) {
        var d = h.createdAt ? new Date(h.createdAt) : null;
        var dateStr = d ? d.toLocaleDateString('ru-RU') : '—';
        var timeStr = d ? d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }) : '—';
        var fio = h.user
            ? (h.user.lastName + ' ' + h.user.firstName + ' ' + (h.user.middleName || '')).trim()
            : 'Система';
        var actionCode = h.action ? h.action.code : '';
        var actionName = ACTIONS_MAP[actionCode] || (h.action ? h.action.name : actionCode) || 'Действие';
        return '<tr>' +
            '<td>' + dateStr + '</td>' +
            '<td>' + timeStr + '</td>' +
            '<td>' + escapeHtml(fio) + '</td>' +
            '<td>' + escapeHtml(actionName) + '</td>' +
            '<td>' + (h.comment ? escapeHtml(h.comment) : '—') + '</td>' +
            '</tr>';
    }).join('');
}

// ============================================================
// СКАЧИВАНИЕ ФАЙЛА
// ============================================================
async function downloadWithToken(event, linkEl) {
    event.preventDefault();
    var url = linkEl.getAttribute('href');
    try {
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!resp.ok) throw new Error('Ошибка скачивания файла');
        var blob = await resp.blob();
        var contentDisposition = resp.headers.get('Content-Disposition') || '';
        var match = contentDisposition.match(/filename="?([^"]+)"?/);
        var filename = match ? match[1] : 'file';
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        URL.revokeObjectURL(a.href);
        a.remove();
    } catch (e) { alert(e.message); }
}

function closeDocumentView() {
    document.getElementById('documentView').style.display = 'none';
    document.getElementById('standardView').style.display = 'block';
    if (currentSection) {
        if (currentSection.type === 'task') loadTasks(currentSection.code);
        else if (currentSection.type === 'doc') loadDocuments(currentSection.code);
    }
}

function switchDocTab(tabName) {
    document.querySelectorAll('.doc-tab-btn').forEach(function (btn) { btn.classList.remove('active'); });
    var btn = document.getElementById('btnTab' + tabName.charAt(0).toUpperCase() + tabName.slice(1));
    if (btn) btn.classList.add('active');
    document.querySelectorAll('.tab-content').forEach(function (el) { el.classList.remove('active'); });
    var tab = document.getElementById('tab-' + tabName);
    if (tab) tab.classList.add('active');
}

function enableEditMode() {
    document.getElementById('mv_title').disabled = false;
    document.getElementById('mv_content').disabled = false;
    document.getElementById('btnEditDoc').style.display = 'none';
    document.getElementById('btnRegisterDoc').style.display = 'none';
    document.getElementById('btnSaveDoc').style.display = 'inline-block';
    document.getElementById('btnAddFileVersion').style.display = 'inline-block';
    switchDocTab('doc');
}

async function saveDocChanges() {
    var title = document.getElementById('mv_title').value;
    var content = document.getElementById('mv_content').value;
    try {
        var resp = await fetch('/api/documents/' + currentDocId, {
            method: 'PUT',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: title, content: content })
        });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка сохранения документа');
        }
        alert('Документ сохранён');
        openDocumentView(currentDocId);
    } catch (e) { alert(e.message); }
}

// ============================================================
// ДОБАВЛЕНИЕ НОВОЙ ВЕРСИИ ФАЙЛА
// ============================================================
function addFileVersion() {
    var input = document.createElement('input');
    input.type = 'file';
    input.onchange = async function (e) {
        var file = e.target.files[0];
        if (!file) return;
        var fd = new FormData();
        fd.append('file', file);
        try {
            var resp = await fetch('/api/documents/' + currentDocId + '/files', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token },
                body: fd
            });
            if (!resp.ok) {
                var err = await resp.json().catch(function () { return {}; });
                throw new Error(err.error || 'Ошибка загрузки файла');
            }
            alert('Новая версия файла добавлена');
            openDocumentView(currentDocId);
        } catch (e) { alert(e.message); }
    };
    input.click();
}

// ============================================================
// РЕГИСТРАЦИЯ
// ============================================================
async function openRegisterModal() {
    try {
        var resp = await fetch('/api/documents/' + currentDocId + '/preview-reg-number',
            { headers: { 'Authorization': 'Bearer ' + token } });
        var data = await resp.json();

        document.getElementById('regNumberPreview').textContent = data.regNumber || '—';
        var dateInput = document.getElementById('regDateInput');
        dateInput.value = new Date().toISOString().slice(0, 10);
        dateInput.max = new Date().toISOString().slice(0, 10);
        if (data.lastRegistrationDate) {
            dateInput.min = data.lastRegistrationDate;
        }

        document.getElementById('registerModalError').style.display = 'none';
        document.getElementById('registerModal').classList.add('open');
    } catch (e) {
        alert('Не удалось получить номер: ' + e.message);
    }
}

function closeRegisterModal() {
    document.getElementById('registerModal').classList.remove('open');
}

async function submitRegistration() {
    var dateValue = document.getElementById('regDateInput').value;
    var errEl = document.getElementById('registerModalError');
    errEl.style.display = 'none';

    if (!dateValue) {
        errEl.textContent = 'Укажите дату регистрации';
        errEl.style.display = 'block';
        return;
    }

    try {
        var resp = await fetch('/api/documents/' + currentDocId + '/register', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            body: JSON.stringify({ registrationDate: dateValue })
        });
        var data = await resp.json().catch(function () { return {}; });
        if (!resp.ok) throw new Error(data.error || 'Ошибка регистрации');

        closeRegisterModal();
        alert('Документ зарегистрирован.\nНомер: ' + data.registrationNumber + '\nДата: ' + data.registrationDate);
        openDocumentView(currentDocId);
    } catch (e) {
        errEl.textContent = e.message;
        errEl.style.display = 'block';
    }
}

function registerDocument() { openRegisterModal(); }

// ============================================================
// ДЕЙСТВИЯ С ЗАДАЧЕЙ (включая REVIEW → EXECUTE)
// В новой схеме "ADMINISTRATIVE" — это код КАТЕГОРИИ (родителя).
// ============================================================
async function openActionModal(routeId, actionCode, actionName) {
    if (actionCode === 'REGISTER') { openRegisterModal(); return; }

    currentRouteId = routeId;
    currentActionCode = actionCode;

    document.getElementById('actionModalTitle').textContent = actionName;
    document.getElementById('actionComment').value = '';

    var userGroup = document.getElementById('actionUserGroup');
    var userSelect = document.getElementById('actionNewAssignee');
    var deadlineGroup = document.getElementById('actionDeadlineGroup');
    var deadlineInput = document.getElementById('actionDeadline');
    deadlineGroup.style.display = 'none';

    // REVIEW для распорядительной КАТЕГОРИИ → нужен исполнитель + срок
    var needAssignee = (actionCode === 'REDIRECT')
        || (actionCode === 'REVIEW' && currentDocCategoryCode === 'ADMINISTRATIVE');

    if (needAssignee) {
        userGroup.style.display = 'flex';
        userSelect.innerHTML = '<option value="">Загрузка...</option>';

        var permAction = (actionCode === 'REDIRECT')
            ? (currentDocData && currentDocData.currentRouteAction || 'APPROVE')
            : 'EXECUTE';

        try {
            var resp = await fetch('/api/documents/eligible-users', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    actionCode: permAction,
                    documentTypeId: currentDocData.documentType ? currentDocData.documentType.documentTypeId : null,
                    categoryId: currentDocData.category ? currentDocData.category.categoryId : null,
                    departmentId: currentDocData.department ? currentDocData.department.departmentId : null,
                    sectorId: currentDocData.sector ? currentDocData.sector.sectorId : null
                })
            });
            var users = await resp.json();

            if (actionCode === 'REDIRECT') {
                users = users.filter(function (u) { return u.userId !== currentUserId; });
            }

            if (!users.length) {
                userSelect.innerHTML = '<option value="">Нет подходящих сотрудников</option>';
            } else {
                userSelect.innerHTML = '<option value="">Выберите сотрудника</option>' +
                    users.map(function (u) {
                        return '<option value="' + u.userId + '">' +
                            escapeHtml(u.lastName) + ' ' + escapeHtml(u.firstName) + ' ' +
                            escapeHtml(u.middleName || '') + ' (' + escapeHtml(u.tabNumber) + ')' +
                            '</option>';
                    }).join('');
            }
        } catch (_) {
            userSelect.innerHTML = '<option value="">Ошибка загрузки</option>';
        }

        if (actionCode === 'REVIEW' && currentDocCategoryCode === 'ADMINISTRATIVE') {
            deadlineGroup.style.display = 'flex';
            var d = new Date();
            d.setDate(d.getDate() + 7);
            deadlineInput.value = d.toISOString().slice(0, 16);
        }
    } else {
        userGroup.style.display = 'none';
    }

    document.getElementById('actionModal').classList.add('open');
}

function closeActionModal() {
    document.getElementById('actionModal').classList.remove('open');
    currentRouteId = null;
    currentActionCode = null;
}

async function submitTaskAction() {
    var comment = document.getElementById('actionComment').value.trim();
    var newAssigneeId = document.getElementById('actionNewAssignee').value;
    var deadline = document.getElementById('actionDeadline').value;

    if (currentActionCode === 'REJECT' && !comment) {
        alert('При отклонении задачи необходимо указать причину (комментарий).');
        return;
    }

    var payload = {
        routeId: currentRouteId,
        actionCode: currentActionCode,
        comment: comment
    };

    var needAssignee = (currentActionCode === 'REDIRECT')
        || (currentActionCode === 'REVIEW' && currentDocCategoryCode === 'ADMINISTRATIVE');
    if (needAssignee) {
        if (!newAssigneeId) { alert('Выберите сотрудника.'); return; }
        payload.newAssigneeId = newAssigneeId;
    }
    if (currentActionCode === 'REVIEW' && currentDocCategoryCode === 'ADMINISTRATIVE') {
        if (!deadline) { alert('Укажите срок исполнения.'); return; }
        payload.deadline = deadline + ':00';
    }

    try {
        var resp = await fetch('/api/documents/' + currentDocId + '/action', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка выполнения действия');
        }
        closeActionModal();
        openDocumentView(currentDocId);
    } catch (e) {
        alert(e.message);
    }
}

// ============================================================
// ПЕЧАТЬ
// ============================================================
function printSection(elementId, title) {
    var el = document.getElementById(elementId);
    if (!el) { alert('Нечего печатать'); return; }
    var win = window.open('', '_blank');
    win.document.write('<html><head><title>' + title + '</title>');
    win.document.write('<meta charset="UTF-8">');
    win.document.write('<style>' +
        'body{font-family:Arial,sans-serif;padding:20px;color:#333;}' +
        'h1{font-size:20px;margin-bottom:16px;}' +
        'table{border-collapse:collapse;width:100%;margin-top:16px;font-size:12px;}' +
        'th,td{border:1px solid #888;padding:8px;text-align:left;}' +
        'th{background:#f0f0f0;}' +
        '.step-card{border:1px solid #888;padding:12px;margin-bottom:12px;border-radius:6px;}' +
        '.step-badge{display:none;}' +
        '.muted{color:#666;}' +
        '.step-meta{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-top:8px;}' +
        '.meta-label{font-size:10px;color:#666;text-transform:uppercase;}' +
        '.meta-value{font-weight:600;}' +
        '.task-actions{display:none !important;}' +
        '.doc-tabs{display:none !important;}' +
        '.button{display:none !important;}' +
        '</style></head><body>');
    win.document.write('<h1>' + title + '</h1>');
    win.document.write(el.innerHTML);
    win.document.write('</body></html>');
    win.document.close();
    setTimeout(function () { win.print(); }, 500);
}

function printHistory() {
    printSection('historyTableContainer', 'История документа: ' + (currentDocData ? currentDocData.title : ''));
}

function printRoute() {
    printSection('routeTimeline', 'Маршрут документа: ' + (currentDocData ? currentDocData.title : ''));
}

async function printDiscipline() {
    var el = document.getElementById('dashboardView');
    if (!el) { alert('Откройте аналитику'); return; }
    if (typeof html2pdf === 'undefined') {
        printSection('dashboardView', 'Исполнительская дисциплина');
        return;
    }
    html2pdf().set({
        margin: 10,
        filename: 'discipline_' + new Date().toISOString().slice(0, 10) + '.pdf',
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
    }).from(el).save();
}