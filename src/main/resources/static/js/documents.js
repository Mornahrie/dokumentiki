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
var currentDocCategoryCode = null;
var pendingNewFile = null;
var currentRouteDocId = null;
var isCreatingNewRoute = false;

// Глобальные справочники для маршрута
var allCategories    = [];
var allDocTypes      = [];
var allNomenclatures = [];
var allUsers         = [];
var allRouteStages   = [];
var allActions       = [];
var approvalStage, signStage, registerStage, reviewStage;
var approveAction, signAction, registerAction, reviewAction;

function findStage(codes, nameParts) {
    let s = allRouteStages.find(x => codes.includes(x.code));
    if (!s) s = allRouteStages.find(x => nameParts.some(n => (x.name || '').toLowerCase().includes(n)));
    return s;
}

async function loadRouteReferences() {
    try {
        const headers = { 'Authorization': 'Bearer ' + token };
        const [cats, types, noms, users, stages, actions] = await Promise.all([
            fetch('/api/ref/doc-categories', { headers }).then(r => r.json()),
            fetch('/api/ref/doc-types',      { headers }).then(r => r.json()),
            fetch('/api/ref/nomenclatures',  { headers }).then(r => r.json()),
            fetch('/api/ref/users',          { headers }).then(r => r.json()),
            fetch('/api/ref/route-stages',   { headers }).then(r => r.json()),
            fetch('/api/ref/doc-actions',    { headers }).then(r => r.json())
        ]);

        allCategories    = (cats  || []).filter(c => c.isActive !== false);
        allDocTypes      = (types || []).filter(t => t.isActive !== false);
        allNomenclatures = (noms  || []).filter(n => n.isActive !== false);
        allUsers         = (users || []).filter(u => u.isActive !== false);
        allRouteStages   = stages || [];
        allActions       = actions || [];

        approvalStage = findStage(['APPROVAL', 'APPROVE'],              ['согласован']);
        signStage     = findStage(['SIGNING', 'SIGN'],                  ['подписан']);
        registerStage = findStage(['REGISTRATION', 'REGISTER'],         ['регистрац']);
        reviewStage   = findStage(['REVIEW', 'CONSIDERATION'],          ['рассмотрен']);

        approveAction  = allActions.find(a => a.code === 'APPROVE');
        signAction     = allActions.find(a => a.code === 'SIGN');
        registerAction = allActions.find(a => a.code === 'REGISTER');
        reviewAction   = allActions.find(a => a.code === 'REVIEW');

        return true;
    } catch (e) {
        alert('Ошибка загрузки справочников: ' + e.message);
        return false;
    }
}

function populateRouteModalSelects() {
    // Убрали вывод табельного номера, оставили только ФИО
    const options = allUsers.map(u =>
        `<option value="${u.userId}">${escapeHtml(u.lastName)} ${escapeHtml(u.firstName)} ${escapeHtml(u.middleName || '')}</option>`
    ).join('');

    ['assigneeApproval', 'assigneeSign', 'assigneeRegister', 'assigneeReview'].forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        el.innerHTML = '<option value="">Выберите</option>' + options;
        el.value = '';
    });

    ['paperApprover', 'paperSigner'].forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        el.innerHTML = '<option value="">Выберите</option>' + options;
        el.value = '';
    });

    ['deadlineApproval', 'deadlineSign', 'deadlineRegister', 'deadlineReview'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
}

function togglePaperFlow() {
    const isPaper = document.getElementById('isPaperFlow').checked;
    const standardBlock = document.getElementById('standardStagesBlock');
    const paperBlock = document.getElementById('paperHistoricalBlock');
    const numRegister = document.getElementById('numRegister');
    const numReview = document.getElementById('numReview');

    if (isPaper) {
        standardBlock.style.display = 'none';
        paperBlock.style.display = 'block';
        numRegister.textContent = '1';
        numReview.textContent = '2';
    } else {
        standardBlock.style.display = 'block';
        paperBlock.style.display = 'none';
        numRegister.textContent = '3';
        numReview.textContent = '4';
    }
}

async function openRouteModalForNew() {
    if (!document.getElementById('categoryId').value) { showError('Выберите категорию'); return; }
    if (!document.getElementById('typeId').value)     { showError('Выберите тип документа'); return; }
    if (!document.getElementById('nomenclatureId').value) { showError('Выберите номенклатуру дел'); return; }

    const loaded = await loadRouteReferences();
    if (!loaded) return;

    populateRouteModalSelects();

    document.getElementById('paperHistoricalBlock').style.display = 'none';
    document.getElementById('standardStagesBlock').style.display = 'block';
    document.getElementById('isPaperFlow').checked = false;
    togglePaperFlow();

    currentRouteDocId = null;
    isCreatingNewRoute = true;

    document.getElementById('routeModal').classList.add('open');
}

async function openRouteModalForExisting(docId) {
    if (!currentDocData) return;

    const loaded = await loadRouteReferences();
    if (!loaded) return;

    populateRouteModalSelects();

    document.getElementById('paperHistoricalBlock').style.display = 'none';
    document.getElementById('standardStagesBlock').style.display = 'block';
    document.getElementById('isPaperFlow').checked = false;
    togglePaperFlow();

    currentRouteDocId = docId;
    isCreatingNewRoute = false;

    document.getElementById('routeModal').classList.add('open');
}

function closeRouteModal() {
    document.getElementById('routeModal').classList.remove('open');
    currentRouteDocId = null;
    isCreatingNewRoute = false;
}

function readDeadline(inputId) {
    const v = document.getElementById(inputId).value;
    return v ? v : null;
}

function validateDeadline(value, label) {
    if (!value) throw new Error('Укажите срок для этапа «' + label + '»');
    const d = new Date(value);
    if (isNaN(d.getTime())) throw new Error('Неверный формат срока для этапа «' + label + '»');
    if (d.getTime() < Date.now()) throw new Error('Срок для этапа «' + label + '» не может быть в прошлом');
}

async function submitRoute() {
    const modalError = document.getElementById('modalError');
    if(modalError) modalError.style.display = 'none';

    try {
        const isPaperFlow = document.getElementById('isPaperFlow').checked;
        const stages = [];

        let paperApproverId = null;
        let paperSignerId = null;

        if (!isPaperFlow) {
            const approvalId = document.getElementById('assigneeApproval').value;
            const signId     = document.getElementById('assigneeSign').value;
            if (!approvalId) throw new Error('Выберите исполнителя для согласования');
            if (!signId)     throw new Error('Выберите исполнителя для подписания');

            const dApprove = readDeadline('deadlineApproval');
            const dSign = readDeadline('deadlineSign');
            validateDeadline(dApprove, 'Согласование');
            validateDeadline(dSign, 'Подписание');

            stages.push({ stageCode: approvalStage.code, actionCode: approveAction.code, assigneeId: parseInt(approvalId), deadline: dApprove });
            stages.push({ stageCode: signStage.code, actionCode: signAction.code, assigneeId: parseInt(signId), deadline: dSign });
        } else {
            paperApproverId = document.getElementById('paperApprover').value;
            paperSignerId = document.getElementById('paperSigner').value;
            if (!paperApproverId) throw new Error('Укажите, кто согласовал документ на бумаге');
            if (!paperSignerId) throw new Error('Укажите, кто подписал документ на бумаге');
        }

        const registerId = document.getElementById('assigneeRegister').value;
        const reviewId   = document.getElementById('assigneeReview').value;
        if (!registerId) throw new Error('Выберите исполнителя для регистрации');
        if (!reviewId)   throw new Error('Выберите исполнителя для рассмотрения');

        const dRegister = readDeadline('deadlineRegister');
        const dReview   = readDeadline('deadlineReview');
        validateDeadline(dRegister, 'Регистрация');
        validateDeadline(dReview, 'Рассмотрение');

        stages.push({ stageCode: registerStage.code, actionCode: registerAction.code, assigneeId: parseInt(registerId), deadline: dRegister });
        stages.push({ stageCode: reviewStage.code, actionCode: reviewAction.code, assigneeId: parseInt(reviewId), deadline: dReview });

        if (!isCreatingNewRoute && currentRouteDocId) {
            await createRoute(
                currentRouteDocId, stages, isPaperFlow, paperApproverId, paperSignerId,
                approvalStage.code, signStage.code, approveAction.code, signAction.code
            );
            closeRouteModal();
            alert('Маршрут отправлен!');
            openDocumentView(currentRouteDocId);
        } else {
            const doc = await createDocument();
            await uploadFiles(doc.documentId);
            await createRoute(
                doc.documentId, stages, isPaperFlow, paperApproverId, paperSignerId,
                approvalStage.code, signStage.code, approveAction.code, signAction.code
            );
            closeRouteModal();
            alert('Документ создан и отправлен по маршруту.');
            window.location.href = '/main';
        }
    } catch (e) {
        if(modalError) {
            modalError.textContent = e.message;
            modalError.style.display = 'block';
        } else {
            alert(e.message);
        }
    }
}

async function createDocument() {
    const title          = document.getElementById('title') ? document.getElementById('title').value.trim() : null;
    const categoryId     = document.getElementById('categoryId').value;
    const typeId         = document.getElementById('typeId').value;
    const nomenclatureId = document.getElementById('nomenclatureId').value;

    if (!title && document.getElementById('title')) throw new Error('Введите название документа');
    if (!categoryId)     throw new Error('Выберите категорию');
    if (!typeId)         throw new Error('Выберите тип');
    if (!nomenclatureId) throw new Error('Выберите номенклатуру дел');

    const payload = {
        title: title || '',
        content: document.getElementById('content') ? document.getElementById('content').value : '',
        categoryId: parseInt(categoryId),
        typeId: parseInt(typeId),
        nomenclatureId: parseInt(nomenclatureId)
    };

    const resp = await fetch('/api/documents', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!resp.ok) {
        let errMsg = 'Ошибка создания документа';
        try { const err = await resp.json(); if (err.error) errMsg = err.error; } catch (_) {}
        throw new Error(errMsg);
    }
    return await resp.json();
}

async function uploadFiles(docId) {
    const fileInput = document.getElementById('fileInput');
    const files = fileInput ? Array.from(fileInput.files) : [];
    for (const file of files) {
        const fd = new FormData();
        fd.append('file', file);
        const resp = await fetch(`/api/documents/${docId}/files`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token },
            body: fd
        });
        if (!resp.ok) {
            let errMsg = 'Ошибка загрузки файла: ' + file.name;
            try { const err = await resp.json(); if (err.error) errMsg = err.error; } catch (_) {}
            throw new Error(errMsg);
        }
    }
}

async function createRoute(docId, stages, isPaperFlow, paperApproverId, paperSignerId, paperApproveStageCode, paperSignStageCode, paperApproveActionCode, paperSignActionCode) {
    const payload = {
        stages: stages,
        isPaperFlow: isPaperFlow,
        paperApproverId: paperApproverId,
        paperSignerId: paperSignerId,
        paperApproveStageCode: paperApproveStageCode,
        paperSignStageCode: paperSignStageCode,
        paperApproveActionCode: paperApproveActionCode,
        paperSignActionCode: paperSignActionCode
    };

    const resp = await fetch(`/api/documents/${docId}/route`, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!resp.ok) {
        let errMsg = 'Ошибка создания маршрута';
        try { const err = await resp.json(); if (err.error) errMsg = err.error; } catch (_) {}
        throw new Error(errMsg);
    }
}

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
    document.getElementById('btnCreateRoute').style.display = 'none';

    document.getElementById('mv_title').disabled = true;
    document.getElementById('mv_content').disabled = true;

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
            if (doc.status === 'DRAFT' || doc.status === 'REVISION') {
                document.getElementById('btnCreateRoute').style.display = 'inline-block';
            }
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
                        var mainBtnLabel = r.action.name;
                        if (r.action.code === 'REVIEW') {
                            mainBtnLabel = 'Рассмотреть (назначить исполнителей)';
                        }
                        taskActionsHtml =
                            '<div class="task-actions">' +
                                '<button class="button button--primary" onclick="openActionModal(' + r.routeId + ', \'' + r.action.code + '\', \'' + mainBtnLabel + '\')">' + mainBtnLabel + '</button>' +
                                '<button class="button button--secondary" style="color: #b00020; border-color: #b00020;" onclick="openActionModal(' + r.routeId + ', \'REJECT\', \'Отклонить\')">Отклонить</button>' +
                                '<button class="button button--secondary" onclick="openActionModal(' + r.routeId + ', \'REDIRECT\', \'Перенаправить\', \'' + r.action.code + '\')">Перенаправить</button>' +
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
    document.getElementById('btnCreateRoute').style.display = 'none';
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
// ДЕЙСТВИЯ С ЗАДАЧЕЙ (REVIEW и REDIRECT)
// ============================================================
async function openActionModal(routeId, actionCode, actionName, originalActionCode) {
    if (actionCode === 'REGISTER') { openRegisterModal(); return; }

    currentRouteId = routeId;
    currentActionCode = actionCode;

    document.getElementById('actionModalTitle').textContent = actionName;
    document.getElementById('actionComment').value = '';

    var userGroup = document.getElementById('actionUserGroup');
    var userSelect = document.getElementById('actionNewAssignee');
    var deadlineGroup = document.getElementById('actionDeadlineGroup');
    var deadlineInput = document.getElementById('actionDeadline');

    if(deadlineGroup) deadlineGroup.style.display = 'none';

    var needAssignee = (actionCode === 'REDIRECT') || (actionCode === 'REVIEW');

    if (needAssignee) {
        userGroup.style.display = 'flex';
        userSelect.innerHTML = '<option value="">Загрузка...</option>';

        var permAction = (actionCode === 'REDIRECT') ? (originalActionCode || 'APPROVE') :
                         (currentDocCategoryCode === 'ADMINISTRATIVE' ? 'EXECUTE' : 'READ');

        try {
            var resp = await fetch('/api/documents/eligible-users', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    actionCode: permAction,
                    documentId: currentDocId
                })
            });
            var users = await resp.json();

            if (users.error) {
                userSelect.innerHTML = '<option value="">Ошибка: ' + users.error + '</option>';
            } else if (!users.length) {
                userSelect.innerHTML = '<option value="">Нет подходящих сотрудников</option>';
            } else {
                if (actionCode === 'REVIEW') {
                    userSelect.setAttribute('multiple', 'true');
                    userSelect.style.height = '120px';
                    // Убрали вывод табельного номера
                    userSelect.innerHTML = users.map(function (u) {
                        return '<option value="' + u.userId + '">' +
                            escapeHtml(u.lastName) + ' ' + escapeHtml(u.firstName) + ' ' +
                            escapeHtml(u.middleName || '') +
                            '</option>';
                    }).join('');
                } else {
                    userSelect.removeAttribute('multiple');
                    userSelect.style.height = '';
                    var filteredUsers = users.filter(u => u.userId !== currentUserId);

                    if (filteredUsers.length === 0) {
                        userSelect.innerHTML = '<option value="">Нет других подходящих сотрудников</option>';
                    } else {
                        // Убрали вывод табельного номера
                        userSelect.innerHTML = '<option value="">Выберите сотрудника</option>' +
                            filteredUsers.map(function (u) {
                                return '<option value="' + u.userId + '">' +
                                    escapeHtml(u.lastName) + ' ' + escapeHtml(u.firstName) + ' ' +
                                    escapeHtml(u.middleName || '') +
                                    '</option>';
                            }).join('');
                    }
                }
            }
        } catch (_) {
            userSelect.innerHTML = '<option value="">Ошибка загрузки</option>';
        }

        if (actionCode === 'REVIEW' && deadlineGroup) {
            deadlineGroup.style.display = 'flex';
            var d = new Date();
            d.setDate(d.getDate() + 7);
            deadlineInput.value = d.toISOString().slice(0, 16);
        }
    } else {
        userGroup.style.display = 'none';
        userSelect.removeAttribute('multiple');
        userSelect.style.height = '';
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
    var userSelect = document.getElementById('actionNewAssignee');
    var deadline = document.getElementById('actionDeadline') ? document.getElementById('actionDeadline').value : null;

    if (currentActionCode === 'REJECT' && !comment) {
        alert('При отклонении задачи необходимо указать причину (комментарий).');
        return;
    }

    var payload = {
        routeId: currentRouteId,
        actionCode: currentActionCode,
        comment: comment
    };

    if (currentActionCode === 'REDIRECT') {
        var newAssigneeId = userSelect.value;
        if (!newAssigneeId) { alert('Выберите сотрудника.'); return; }
        payload.newAssigneeId = newAssigneeId;
    }
    else if (currentActionCode === 'REVIEW') {
        var selectedOptions = Array.from(userSelect.selectedOptions).map(opt => opt.value).filter(v => v);
        if (selectedOptions.length === 0) {
            alert('Выберите хотя бы одного исполнителя.');
            return;
        }
        payload.executorIds = selectedOptions;

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