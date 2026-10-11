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
        if (!Array.isArray(data)) {
            throw new Error('Некорректный формат данных от сервера');
        }

        currentData = data;
        applySearch();
    } catch (e) {
        document.getElementById('tableContainer').innerHTML = '<div class="muted" style="padding:32px;text-align:center;color:#b00020;">' + escapeHtml(e.message) + '</div>';
    }
}

async function loadDocuments(typeCode) {
    document.getElementById('tableContainer').innerHTML = '<div style="padding:32px;text-align:center;">Загрузка...</div>';
    try {
        var resp = await fetch('/api/documents/my?type=' + encodeURIComponent(typeCode), { headers: { 'Authorization': 'Bearer ' + token } });

        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка загрузки документов (HTTP ' + resp.status + ')');
        }

        var data = await resp.json();
        if (!Array.isArray(data)) {
            throw new Error('Некорректный формат данных от сервера');
        }

        data = data.map(function (d) {
            return Object.assign({}, d, {
                authorName: d.author ? (d.author.lastName + ' ' + (d.author.firstName ? d.author.firstName.charAt(0) : '') + '.') : '—'
            });
        });
        currentData = data;
        applySearch();
    } catch (e) {
        document.getElementById('tableContainer').innerHTML = '<div class="muted" style="padding:32px;text-align:center;color:#b00020;">' + escapeHtml(e.message) + '</div>';
    }
}

async function openDocumentView(id) {
    currentDocId = id;
    document.getElementById('standardView').style.display = 'none';
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('documentView').style.display = 'block';

    document.getElementById('btnRegisterDoc').style.display = 'none';
    document.getElementById('btnEditDoc').style.display = 'none';
    document.getElementById('btnSaveDoc').style.display = 'none';
    document.getElementById('mv_title').disabled = true;
    document.getElementById('mv_content').disabled = true;

    var fromTasks = currentSection && currentSection.type === 'task';
    switchDocTab(fromTasks ? 'route' : 'doc');

    try {
        var resp = await fetch('/api/documents/' + id, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!resp.ok) {
            var err = await resp.json().catch(function () { return {}; });
            throw new Error(err.error || 'Ошибка загрузки документа');
        }

        var data = await resp.json();
        if (!data || !data.document) {
            throw new Error('Документ не найден или нет доступа');
        }

        var doc = data.document;
        var files = data.files || [];

        currentDocAuthor = doc.author ? (doc.author.lastName + ' ' + (doc.author.firstName ? doc.author.firstName.charAt(0) : '') + '.' +
            (doc.author.middleName ? ' ' + doc.author.middleName.charAt(0) + '.' : '')) : '—';

        document.getElementById('docViewTitle').textContent = doc.title || 'Документ';
        document.getElementById('mv_title').value = doc.title || '—';
        document.getElementById('mv_content').value = doc.content || '—';
        document.getElementById('mv_type').value = doc.documentType ? doc.documentType.name : '—';
        document.getElementById('mv_category').value = doc.category ? doc.category.name : '—';
        document.getElementById('mv_nomenclature').value = doc.nomenclature ? doc.nomenclature.caseCode + ' — ' + doc.nomenclature.title : '—';
        document.getElementById('mv_reg').value = doc.registrationNumber || 'Не зарегистрирован';
        document.getElementById('mv_status').value = STATUS_MAP[doc.status] || doc.status;
        document.getElementById('mv_author').value = doc.author ? (doc.author.lastName + ' ' + doc.author.firstName + ' ' + (doc.author.middleName || '')) : '—';
        document.getElementById('mv_created').value = doc.createdAt ? new Date(doc.createdAt).toLocaleString('ru-RU') : '—';

        if (data.canRegister) document.getElementById('btnRegisterDoc').style.display = 'inline-block';
        if (data.canEdit)     document.getElementById('btnEditDoc').style.display = 'inline-block';

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

                    var taskType = (r.stage ? r.stage.name : 'Этап') + ' &rarr; ' + (r.action ? r.action.name : 'Действие');
                    var indent = index * 40;

                    var isMyActiveTask = r.assignedUser
                        && r.assignedUser.userId === currentUserId
                        && (r.status === 'PENDING' || r.status === 'IN_PROGRESS');

                    var taskActionsHtml = '';
                    if (isMyActiveTask) {
                        taskActionsHtml =
                            '<div class="task-actions">' +
                                '<button class="button button--primary" onclick="openActionModal(' + r.routeId + ', \'' + r.action.code + '\', \'' + r.action.name + '\')">' + r.action.name + '</button>' +
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
        var container = document.getElementById('historyTimeline');

        if (respH.ok) {
            var history = await respH.json();
            if (!Array.isArray(history) || history.length === 0) {
                container.innerHTML = '<div class="muted" style="text-align:center;">Действий пока нет</div>';
            } else {
                container.innerHTML = history.map(function (h) {
                    var uName = h.user ? (h.user.lastName + ' ' + (h.user.firstName ? h.user.firstName.charAt(0) : '') + '.') : 'Система';
                    var aName = h.action ? h.action.name : 'Действие';
                    return '<div class="timeline-item" style="display: flex; gap: 16px; background: var(--bg-milk); padding: 16px; border-radius: 12px; border: 1px solid var(--border-pink); margin-bottom: 16px;">' +
                        '<div style="width: 32px; height: 32px; border-radius: 50%; background: var(--bg-pink-medium); display: flex; align-items: center; justify-content: center; font-weight: bold; color: var(--magenta); flex-shrink: 0;">📝</div>' +
                        '<div style="flex: 1;">' +
                            '<div style="font-weight: 600; color: var(--text-dark); margin-bottom: 4px; display: flex; justify-content: space-between; align-items: center;">' +
                                '<span>' + aName + '</span>' +
                                '<span class="muted" style="font-size:12px;">' + (h.createdAt ? new Date(h.createdAt).toLocaleString('ru-RU') : '') + '</span>' +
                            '</div>' +
                            '<div style="font-size: 13px; color: var(--text-muted); margin-bottom: 8px;">Пользователь: <strong>' + uName + '</strong></div>' +
                            (h.comment ? '<div style="font-size:13px; color:var(--text-dark);">' + escapeHtml(h.comment) + '</div>' : '') +
                        '</div>' +
                    '</div>';
                }).join('');
            }
        } else {
            container.innerHTML = '<div class="muted" style="text-align:center;color:#b00020;">Не удалось загрузить историю</div>';
        }
    } catch (e) {
        document.getElementById('historyTimeline').innerHTML = '<div class="muted" style="text-align:center;color:#b00020;">Ошибка загрузки истории</div>';
    }
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
    document.getElementById('btnSaveDoc').style.display = 'inline-block';
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

async function registerDocument() {
    if (!confirm('Зарегистрировать документ? Регистрационный номер будет присвоен автоматически.')) return;
    try {
        var resp = await fetch('/api/documents/' + currentDocId + '/register', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json().catch(function () { return {}; });

        if (!resp.ok) throw new Error(data.error || 'Ошибка регистрации документа');

        alert('Документ зарегистрирован.\nРегистрационный номер: ' + data.registrationNumber);
        openDocumentView(currentDocId);
    } catch (e) {
        alert(e.message);
    }
}

function openActionModal(routeId, actionCode, actionName) {
    // Регистрация — отдельный endpoint, делегируем в registerDocument()
    if (actionCode === 'REGISTER') {
        registerDocument();
        return;
    }

    currentRouteId = routeId;
    currentActionCode = actionCode;

    document.getElementById('actionModalTitle').textContent = actionName;
    document.getElementById('actionComment').value = '';

    var userGroup = document.getElementById('actionUserGroup');
    var userSelect = document.getElementById('actionNewAssignee');

    if (actionCode === 'REDIRECT') {
        userGroup.style.display = 'flex';
        userSelect.innerHTML = allUsers
            .filter(function (u) { return u.userId !== currentUserId; })
            .map(function (u) {
                return '<option value="' + u.userId + '">' + escapeHtml(u.lastName) + ' ' + escapeHtml(u.firstName) + ' ' + escapeHtml(u.middleName || '') + ' (' + escapeHtml(u.tabNumber) + ')</option>';
            }).join('');
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
        if (!newAssigneeId) { alert('Выберите сотрудника для перенаправления.'); return; }
        payload.newAssigneeId = newAssigneeId;
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