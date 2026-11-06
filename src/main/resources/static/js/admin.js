// Управление справочниками и полномочиями (только для ADMIN).
// ВЫБОР ТИПА СПРАВОЧНИКА
function renderRefTypeSelector() {
    document.getElementById('refToolbar').innerHTML =
        '<select id="refTypeSelect" onchange="onRefTypeChange()">' +
            REF_TYPES.map(function (r) { return '<option value="' + r.code + '">' + r.name + '</option>'; }).join('') +
        '</select>' +
        '<button class="button button--primary" onclick="openRefModal()">+ Добавить</button>';
    document.getElementById('refTypeSelect').value = currentRefType;
}

async function onRefTypeChange() {
    currentRefType = document.getElementById('refTypeSelect').value;
    document.getElementById('tableSearch').value = '';
    activeFilters = {};
    sortConfig = { key: null, direction: 'asc' };
    closeFilterPopover();
    await loadReferenceManagement();
}

async function loadReferenceManagement() {
    document.getElementById('tableSearch').style.display = 'block';
    document.getElementById('tableContainer').innerHTML = '<div style="padding:32px;text-align:center;">Загрузка...</div>';
    try {
        var resp = await fetch('/api/admin/' + currentRefType, { headers: { 'Authorization': 'Bearer ' + token } });
        currentData = await resp.json();
        applySearch();
    } catch (e) { showAlert('Ошибка', e.message); }
}

// ТАБЛИЦА СПРАВОЧНИКА
function renderRefTable(data) {
    var configs = {
        'departments':    { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }, { key: 'isActive', label: 'Активно', fmt: function (v) { return v ? 'Да' : 'Нет'; } }], idKey: 'departmentId' },
        'sectors':        { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }, { key: 'department.name', label: 'Подразделение' }, { key: 'isActive', label: 'Активно', fmt: function (v) { return v ? 'Да' : 'Нет'; } }], idKey: 'sectorId' },
        'nomenclatures':  { cols: [
            { key: 'caseCode', label: 'Шифр' },
            { key: 'title', label: 'Заголовок' },
            { key: 'department.name', label: 'Подразделение' },
            { key: 'category.name', label: 'Категория', fmt: function (v) { return v || '—'; } },
            { key: 'type.name', label: 'Тип', fmt: function (v) { return v || '—'; } },
            { key: 'retentionPeriod', label: 'Срок (лет)' },
            { key: 'isActive', label: 'Активно', fmt: function (v) { return v ? 'Да' : 'Нет'; } }
        ], idKey: 'nomenclatureId' },
        'doc-categories': { cols: [
            { key: 'code', label: 'Код' },
            { key: 'name', label: 'Название категории' },
            { key: 'isActive', label: 'Активно', fmt: function (v) { return v ? 'Да' : 'Нет'; } }
        ], idKey: 'categoryId' },
        'doc-types':      { cols: [
            { key: 'code', label: 'Код' },
            { key: 'name', label: 'Название типа' },
            { key: 'category.name', label: 'Категория' },
            { key: 'isActive', label: 'Активно', fmt: function (v) { return v ? 'Да' : 'Нет'; } }
        ], idKey: 'documentTypeId' },
        'doc-actions':    { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }, { key: 'description', label: 'Описание' }], idKey: 'actionId' },
        'route-stages':   { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }, { key: 'description', label: 'Описание' }], idKey: 'stageId' },
        'base-roles':     { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }], idKey: 'baseRoleId' },
        'positions':      { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }], idKey: 'positionId' },
        'permissions':    { cols: [{ key: 'code', label: 'Код' }, { key: 'name', label: 'Название' }, { key: 'description', label: 'Описание' }], idKey: 'permissionId' },
        'users':          { cols: [{ key: 'tabNumber', label: 'Табельный' }, { key: 'lastName', label: 'Фамилия' }, { key: 'firstName', label: 'Имя' }, { key: 'isActive', label: 'Активен', fmt: function (v) { return v ? 'Да' : 'Нет'; } }], idKey: 'userId' }
    };

    var cfg = configs[currentRefType];
    cfg.cols.forEach(function (c) { if (columnVisibility[c.key] === undefined) columnVisibility[c.key] = true; });

    var actions = function (row) {
        return '<button class="icon-btn" title="Изменить" aria-label="Изменить" onclick="openRefModal(' + row[cfg.idKey] + ')">✏️</button>' +
               '<button class="icon-btn icon-btn--danger" title="Удалить" aria-label="Удалить" onclick="confirmDeleteRef(' + row[cfg.idKey] + ', \'' + currentRefType + '\')">🗑️</button>';
    };
    renderTable('tableContainer', data, cfg.cols, false, actions);
}

// УНИВЕРСАЛЬНАЯ МОДАЛКА
var pendingFormSubmit = null;

function showModal(title, fieldsHtml, onSubmit) {
    document.getElementById('formModalTitle').textContent = title;
    document.getElementById('formFieldsContainer').innerHTML = fieldsHtml;
    pendingFormSubmit = onSubmit;
    document.getElementById('formModal').classList.add('open');
}

function submitFormModal(event) {
    event.preventDefault();
    if (!pendingFormSubmit) return;
    var formData = {};
    new FormData(event.target).forEach(function (value, key) { formData[key] = value; });
    pendingFormSubmit(formData);
}

function closeFormModal() {
    document.getElementById('formModal').classList.remove('open');
    pendingFormSubmit = null;
}

// ФОРМА ДОБАВЛЕНИЯ / РЕДАКТИРОВАНИЯ СПРАВОЧНИКА
async function openRefModal(id) {
    var isEdit = (id !== undefined && id !== null);
    var idMap = {
        'departments': 'departmentId', 'sectors': 'sectorId', 'nomenclatures': 'nomenclatureId',
        'doc-types': 'documentTypeId', 'doc-categories': 'categoryId', 'doc-actions': 'actionId',
        'route-stages': 'stageId', 'base-roles': 'baseRoleId', 'positions': 'positionId',
        'permissions': 'permissionId', 'users': 'userId'
    };

    var data = isEdit ? currentData.find(function (x) { return x[idMap[currentRefType]] === id; }) : {};

    var title = isEdit ? 'Редактирование' : 'Добавление';
    var v = function (key) { return data[key] == null ? '' : data[key]; };
    var fields = '';

    var deptOptions = '', categoryOptions = '', posOptions = '', roleOptions = '', typeOptionsForNom = '';

    if (['sectors', 'nomenclatures', 'users'].indexOf(currentRefType) !== -1) {
        var res = await fetch('/api/admin/departments', { headers: { 'Authorization': 'Bearer ' + token } });
        var list = await res.json();
        deptOptions = list.map(function (d) {
            var sel = data.department && data.department.departmentId === d.departmentId ? 'selected' : '';
            return '<option value="' + d.departmentId + '" ' + sel + '>' + d.name + '</option>';
        }).join('');
    }

    if (currentRefType === 'doc-types') {
        var resC = await fetch('/api/admin/doc-categories', { headers: { 'Authorization': 'Bearer ' + token } });
        var listC = await resC.json();
        categoryOptions = listC.map(function (c) {
            var sel = data.category && data.category.categoryId === c.categoryId ? 'selected' : '';
            return '<option value="' + c.categoryId + '" ' + sel + '>' + c.name + '</option>';
        }).join('');
    }

    if (currentRefType === 'users') {
        var resP = await fetch('/api/admin/positions', { headers: { 'Authorization': 'Bearer ' + token } });
        var listP = await resP.json();
        posOptions = listP.map(function (p) {
            var sel = data.position && data.position.positionId === p.positionId ? 'selected' : '';
            return '<option value="' + p.positionId + '" ' + sel + '>' + p.name + '</option>';
        }).join('');

        var resR = await fetch('/api/admin/base-roles', { headers: { 'Authorization': 'Bearer ' + token } });
        var listR = await resR.json();
        roleOptions = listR.map(function (r) {
            var sel = data.baseRole && data.baseRole.code === r.code ? 'selected' : '';
            return '<option value="' + r.code + '" ' + sel + '>' + r.name + '</option>';
        }).join('');
    }

    if (currentRefType === 'nomenclatures') {
        var resCat = await fetch('/api/admin/doc-categories', { headers: { 'Authorization': 'Bearer ' + token } });
        var listCat = await resCat.json();

        var selectedCatId  = data.category ? data.category.categoryId : null;
        var selectedTypeId = data.type ? data.type.documentTypeId : null;

        categoryOptions = '<option value="">— Выберите категорию —</option>' + listCat.map(function (c) {
            var sel = selectedCatId === c.categoryId ? 'selected' : '';
            return '<option value="' + c.categoryId + '" ' + sel + '>' + c.name + '</option>';
        }).join('');

        typeOptionsForNom = '<option value="">— Сначала выберите категорию —</option>';
        if (selectedCatId) {
            var resT = await fetch('/api/admin/doc-types', { headers: { 'Authorization': 'Bearer ' + token } });
            var listT = await resT.json();
            var filteredTypes = listT.filter(t => t.category && t.category.categoryId === selectedCatId);
            typeOptionsForNom = '<option value="">— Выберите тип —</option>' + filteredTypes.map(function (t) {
                var sel = selectedTypeId === t.documentTypeId ? 'selected' : '';
                return '<option value="' + t.documentTypeId + '" ' + sel + '>' + t.name + '</option>';
            }).join('');
        }
    }

    switch (currentRefType) {
        case 'departments':
        case 'base-roles':
        case 'positions':
            fields = '<div class="form-group"><label>Код</label><input name="code" value="' + v('code') + '" required></div>' +
                     '<div class="form-group"><label>Название</label><input name="name" value="' + v('name') + '" required></div>';
            break;
        case 'sectors':
            fields = '<div class="form-group"><label>Подразделение</label><select name="departmentId" required>' + deptOptions + '</select></div>' +
                     '<div class="form-group"><label>Код</label><input name="code" value="' + v('code') + '" required></div>' +
                     '<div class="form-group"><label>Название</label><input name="name" value="' + v('name') + '" required></div>';
            break;
        case 'nomenclatures':
            fields =
                '<div class="form-group"><label>Подразделение</label><select name="departmentId" required>' + deptOptions + '</select></div>' +
                '<div class="form-group"><label>Шифр дела</label><input name="caseCode" value="' + v('caseCode') + '" required></div>' +
                '<div class="form-group"><label>Заголовок</label><input name="title" value="' + v('title') + '" required></div>' +
                '<div class="form-group"><label>Срок хранения (лет)</label><input type="number" name="retentionPeriod" value="' + v('retentionPeriod') + '" required></div>' +
                '<div class="form-group"><label>Категория *</label>' +
                    '<select name="categoryId" id="nomCategorySelect" onchange="onNomCategoryChange()" required>' + categoryOptions + '</select>' +
                '</div>' +
                '<div class="form-group"><label>Тип документа *</label>' +
                    '<select name="typeId" id="nomTypeSelect" required>' + typeOptionsForNom + '</select>' +
                '</div>';
            break;
        case 'doc-categories':
            fields = '<div class="form-group"><label>Код категории</label><input name="code" value="' + v('code') + '" required></div>' +
                     '<div class="form-group"><label>Название категории</label><input name="name" value="' + v('name') + '" required></div>';
            break;
        case 'doc-types':
            fields = '<div class="form-group"><label>Категория</label><select name="categoryId" required>' + categoryOptions + '</select></div>' +
                     '<div class="form-group"><label>Код типа</label><input name="code" value="' + v('code') + '" required></div>' +
                     '<div class="form-group"><label>Название типа</label><input name="name" value="' + v('name') + '" required></div>';
            break;
        case 'doc-actions':
        case 'route-stages':
        case 'permissions':
            fields = '<div class="form-group"><label>Код</label><input name="code" value="' + v('code') + '" required></div>' +
                     '<div class="form-group"><label>Название</label><input name="name" value="' + v('name') + '" required></div>' +
                     '<div class="form-group"><label>Описание</label><textarea name="description" rows="3">' + v('description') + '</textarea></div>';
            break;
        case 'users':
            fields = '<div class="form-group"><label>Табельный номер</label><input name="tabNumber" value="' + v('tabNumber') + '" required ' + (isEdit ? 'readonly' : '') + '></div>' +
                     '<div class="form-group"><label>Фамилия</label><input name="lastName" value="' + v('lastName') + '" required></div>' +
                     '<div class="form-group"><label>Имя</label><input name="firstName" value="' + v('firstName') + '" required></div>' +
                     '<div class="form-group"><label>Отчество</label><input name="middleName" value="' + v('middleName') + '"></div>' +
                     '<div class="form-group"><label>Подразделение</label><select name="departmentId" required>' + deptOptions + '</select></div>' +
                     '<div class="form-group"><label>Должность</label><select name="positionId" required>' + posOptions + '</select></div>' +
                     '<div class="form-group"><label>Роль</label><select name="roleCode">' + roleOptions + '</select></div>' +
                     (!isEdit ? '<div class="form-group"><label>Пароль (по умолч. 12345678)</label><input name="password" placeholder="Оставьте пустым для стандартного"></div>' : '');
            break;
    }

    showModal(title + ' (' + REF_TYPES.find(function (r) { return r.code === currentRefType; }).name + ')', fields, async function (formData) {

        var url = '/api/admin/' + currentRefType + (isEdit ? '/' + id : '');
        var method = isEdit ? 'PUT' : 'POST';
        var submitUrl = (currentRefType === 'permissions' && !isEdit) ? '/api/admin/permissions/create' : url;

        try {
            var res = await fetch(submitUrl, {
                method: method,
                headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                body: JSON.stringify(formData)
            });
            if (!res.ok) {
                var err = await res.json().catch(function () { return {}; });
                throw new Error(err.error || 'Ошибка сохранения');
            }
            closeFormModal();
            await loadReferenceManagement();
        } catch (e) { showAlert('Ошибка', e.message); }
    });
}

async function onNomCategoryChange() {
    var catSelect = document.getElementById('nomCategorySelect');
    var typeSelect = document.getElementById('nomTypeSelect');
    if (!catSelect || !typeSelect) return;

    var catId = parseInt(catSelect.value);
    if (!catId) {
        typeSelect.innerHTML = '<option value="">— Сначала выберите категорию —</option>';
        return;
    }

    try {
        var res = await fetch('/api/admin/doc-types', { headers: { 'Authorization': 'Bearer ' + token } });
        var list = await res.json();
        var filtered = list.filter(t => t.category && t.category.categoryId === catId);

        if (!filtered.length) {
            typeSelect.innerHTML = '<option value="">— Нет типов для этой категории —</option>';
            return;
        }

        typeSelect.innerHTML = '<option value="">— Выберите тип —</option>' + filtered.map(function (t) {
            return '<option value="' + t.documentTypeId + '">' + t.name + '</option>';
        }).join('');
    } catch (e) {
        typeSelect.innerHTML = '<option value="">— Ошибка загрузки —</option>';
    }
}

// УДАЛЕНИЕ СПРАВОЧНИКА
function confirmDeleteRef(id, type) {
    showConfirm(
        'Удаление записи',
        'Вы уверены? Если у записи есть связанные данные — статус изменится на «Не активно». Если связанных данных нет — запись будет удалена полностью.',
        async function () {
            try {
                var res = await fetch('/api/admin/' + type + '/' + id, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                var data = await res.json().catch(function () { return {}; });
                if (!res.ok) {
                    showAlert('Ошибка', data.error || 'Ошибка удаления');
                    return;
                }
                if (data.message) showAlert('Успех', data.message);
                await loadReferenceManagement();
            } catch (e) { showAlert('Ошибка', 'Ошибка удаления: ' + e.message); }
        }
    );
}

// УПРАВЛЕНИЕ ПОЛНОМОЧИЯМИ
async function loadPermissionManagement() {
    currentPermissionUserId = null;
    currentPositionId = null;
    document.getElementById('tableSearch').style.display = 'block';
    document.getElementById('tableContainer').innerHTML = '<div style="padding:32px;text-align:center;">Загрузка...</div>';

    var refToolbar = document.getElementById('refToolbar');
    refToolbar.style.display = 'flex';
    refToolbar.innerHTML =
        '<select id="permModeSelect" onchange="onPermModeChange()">' +
            '<option value="users"' + (currentPermissionMode === 'users' ? ' selected' : '') + '>Пользователи</option>' +
            '<option value="positions"' + (currentPermissionMode === 'positions' ? ' selected' : '') + '>Должности</option>' +
        '</select>';

    try {
        var url = (currentPermissionMode === 'positions') ? '/api/admin/positions' : '/api/admin/users';
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        currentData = await resp.json();
        applySearch();
    } catch (e) { showAlert('Ошибка', e.message); }
}

async function onPermModeChange() {
    currentPermissionMode = document.getElementById('permModeSelect').value;
    currentPermissionUserId = null;
    currentPositionId = null;
    document.getElementById('tableSearch').value = '';
    activeFilters = {};
    sortConfig = { key: null, direction: 'asc' };
    closeFilterPopover();
    await loadPermissionManagement();
}

function renderUsersTable(data) {
    var cols = [
        { key: 'fullName', label: 'ФИО', fmt: function (_, r) { return r.lastName + ' ' + r.firstName + ' ' + (r.middleName || ''); } },
        { key: 'tabNumber', label: 'Табельный' },
        { key: 'baseRole.name', label: 'Роль' },
        { key: 'position.name', label: 'Должность' }
    ];
    cols.forEach(function (c) { if (columnVisibility[c.key] === undefined) columnVisibility[c.key] = true; });
    var actions = function (row) { return '<button class="action-link" onclick="openUserPermissions(' + row.userId + ')">Полномочия →</button>'; };
    renderTable('tableContainer', data, cols, false, actions);
}

function renderPositionsTable(data) {
    var cols = [
        { key: 'code', label: 'Код' },
        { key: 'name', label: 'Название должности' }
    ];
    cols.forEach(function (c) { if (columnVisibility[c.key] === undefined) columnVisibility[c.key] = true; });
    var actions = function (row) {
        return '<button class="action-link" onclick="openPositionPermissions(' + row.positionId + ')">Полномочия →</button>';
    };
    renderTable('tableContainer', data, cols, false, actions);
}

async function openUserPermissions(userId) {
    currentPermissionUserId = userId;
    document.getElementById('tableSearch').style.display = 'none';
    var container = document.getElementById('tableContainer');
    container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Загрузка...</div>';

    try {
        var results = await Promise.all([
            fetch('/api/admin/users/' + userId + '/permissions', { headers: { 'Authorization': 'Bearer ' + token } })
        ]);
        var userPerms = await results[0].json();
        var user = currentData.find(function (u) { return u.userId === userId; });

        renderUserPermissions(user, userPerms);
    } catch (e) {
        container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Ошибка: ' + e.message + '</div>';
    }
}

function renderUserPermissions(user, perms) {
    var container = document.getElementById('tableContainer');
    var fio = user ? ((user.lastName || '') + ' ' + (user.firstName || '')).trim() : 'Пользователь';
    document.getElementById('docCount').textContent = perms.length + ' полномочий';

    var html = '<div style="display:flex; align-items:center; justify-content:space-between; background:var(--bg-pink-light); padding: 16px 24px; border-radius: 16px; margin-bottom: 24px; border: 1px solid var(--border-pink); flex-wrap: wrap; gap: 16px;">' +
        '<div style="display:flex; align-items:center; gap:16px;">' +
            '<button class="button button--secondary" onclick="loadPermissionManagement()">← Назад</button>' +
            '<strong style="font-size:18px; color:var(--text-dark);">' + escapeHtml(fio) + '</strong>' +
        '</div>' +
        '<button class="button button--primary" onclick="openGrantPermissionModal(' + user.userId + ')">+ Выдать полномочие</button>' +
    '</div>';

    if (!perms.length) {
        html += '<div class="muted" style="padding:32px; text-align:center; border:1px dashed var(--border-pink); border-radius:16px;">Полномочий нет</div>';
    } else {
        html += '<table><thead><tr>' +
            '<th>Полномочие</th>' +
            '<th>Тип</th>' +
            '<th>Категория</th>' +
            '<th>Подразделение</th>' +
            '<th>Сектор</th>' +
            '<th>Срок по</th>' +
            '<th></th>' +
        '</tr></thead><tbody>';
        perms.forEach(function (p) {
            html += '<tr>' +
                '<td>' + escapeHtml(p.permissionName || p.permissionCode) + '</td>' +
                '<td>' + escapeHtml(p.documentTypeName || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.categoryName    || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.departmentName  || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.sectorName      || 'Все') + '</td>' +
                '<td>' + (p.expiresAt ? new Date(p.expiresAt).toLocaleString('ru-RU') : 'Бессрочно') + '</td>' +
                '<td><button class="action-link" style="color:#b00020;" onclick="confirmRevokePermission(' + user.userId + ',' + p.userPermissionId + ')">Отозвать</button></td>' +
            '</tr>';
        });
        html += '</tbody></table>';
    }
    container.innerHTML = html;
}

async function openPositionPermissions(positionId) {
    currentPositionId = positionId;
    document.getElementById('tableSearch').style.display = 'none';
    var container = document.getElementById('tableContainer');
    container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Загрузка...</div>';

    try {
        var results = await Promise.all([
            fetch('/api/admin/positions/' + positionId + '/permissions', {
                headers: { 'Authorization': 'Bearer ' + token }
            })
        ]);
        var posPerms = await results[0].json();
        var position = currentData.find(function (p) { return p.positionId === positionId; });

        renderPositionPermissions(position, posPerms);
    } catch (e) {
        container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Ошибка: ' + e.message + '</div>';
    }
}

function renderPositionPermissions(position, perms) {
    var container = document.getElementById('tableContainer');
    // Убрали код должности в скобках
    var title = position ? (position.name || 'Должность') : 'Должность';
    document.getElementById('docCount').textContent = perms.length + ' полномочий';

    var html = '<div style="display:flex; align-items:center; justify-content:space-between; background:var(--bg-pink-light); padding: 16px 24px; border-radius: 16px; margin-bottom: 24px; border: 1px solid var(--border-pink); flex-wrap: wrap; gap: 16px;">' +
        '<div style="display:flex; align-items:center; gap:16px;">' +
            '<button class="button button--secondary" onclick="loadPermissionManagement()">← Назад</button>' +
            '<strong style="font-size:18px; color:var(--text-dark);">' + escapeHtml(title) + '</strong>' +
        '</div>' +
        '<button class="button button--primary" onclick="openGrantPositionPermissionModal(' + position.positionId + ')">+ Выдать полномочие</button>' +
    '</div>';

    if (!perms.length) {
        html += '<div class="muted" style="padding:32px; text-align:center; border:1px dashed var(--border-pink); border-radius:16px;">Полномочий нет</div>';
    } else {
        html += '<table><thead><tr>' +
            '<th>Полномочие</th>' +
            '<th>Тип</th>' +
            '<th>Категория</th>' +
            '<th>Подразделение</th>' +
            '<th>Сектор</th>' +
            '<th></th>' +
        '</tr></thead><tbody>';
        perms.forEach(function (p) {
            html += '<tr>' +
                '<td>' + escapeHtml(p.permissionName || p.permissionCode) + '</td>' +
                '<td>' + escapeHtml(p.documentTypeName || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.categoryName    || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.departmentName  || 'Все') + '</td>' +
                '<td>' + escapeHtml(p.sectorName      || 'Все') + '</td>' +
                '<td><button class="action-link" style="color:#b00020;" onclick="confirmRevokePositionPermission(' + position.positionId + ',' + p.positionPermissionId + ')">Отозвать</button></td>' +
            '</tr>';
        });
        html += '</tbody></table>';
    }
    container.innerHTML = html;
}

async function openGrantPermissionModal(userId) {
    try {
        var results = await Promise.all([
            fetch('/api/admin/permissions',    { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/doc-categories', { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/doc-types',      { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/departments',    { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/sectors',        { headers: { 'Authorization': 'Bearer ' + token } })
        ]);
        var allPerms   = await results[0].json();
        var allCats    = await results[1].json();
        var allTypes   = await results[2].json();
        var allDepts   = await results[3].json();
        var allSectors = await results[4].json();

        var permOptions = allPerms.map(function(p) { return '<option value="' + p.code + '">' + p.name + '</option>'; }).join('');
        var catOptions  = '<option value="">Все категории</option>' + allCats.map(function(c) { return '<option value="' + c.categoryId + '">' + c.name + '</option>'; }).join('');
        var typeOptions = '<option value="">Все типы</option>' + allTypes.map(function(t) { return '<option value="' + t.documentTypeId + '">' + t.name + '</option>'; }).join('');
        var deptOptions = '<option value="">Все подразделения</option>' + allDepts.map(function(d) { return '<option value="' + d.departmentId + '">' + d.name + '</option>'; }).join('');
        var sectOptions = '<option value="">Все секторы</option>' + allSectors.map(function(s) { return '<option value="' + s.sectorId + '">' + s.name + '</option>'; }).join('');

        var fields =
            '<div class="form-group"><label>Полномочие *</label><select name="permissionCode" required>' + permOptions + '</select></div>' +
            '<div class="form-group"><label>Категория</label><select name="categoryId">' + catOptions + '</select></div>' +
            '<div class="form-group"><label>Тип документа</label><select name="documentTypeId">' + typeOptions + '</select></div>' +
            '<div class="form-group"><label>Подразделение</label><select name="departmentId">' + deptOptions + '</select></div>' +
            '<div class="form-group"><label>Сектор</label><select name="sectorId">' + sectOptions + '</select></div>' +
            '<div class="form-group"><label>Действует до (необязательно)</label><input type="datetime-local" name="expiresAt"></div>';

        showModal('Выдача полномочия пользователю', fields, async function(formData) {
            Object.keys(formData).forEach(function(key) {
                if (formData[key] === '') delete formData[key];
            });

            try {
                var res = await fetch('/api/admin/users/' + userId + '/permissions', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                    body: JSON.stringify(formData)
                });
                if (!res.ok) {
                    var err = await res.json().catch(function() { return {}; });
                    throw new Error(err.error || 'Ошибка выдачи полномочия');
                }
                closeFormModal();
                await openUserPermissions(userId);
            } catch (e) {
                showAlert('Ошибка', e.message);
            }
        });
    } catch (e) {
        showAlert('Ошибка', 'Ошибка загрузки справочников для модалки: ' + e.message);
    }
}

async function openGrantPositionPermissionModal(positionId) {
    try {
        var results = await Promise.all([
            fetch('/api/admin/permissions',    { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/doc-categories', { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/doc-types',      { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/departments',    { headers: { 'Authorization': 'Bearer ' + token } }),
            fetch('/api/admin/sectors',        { headers: { 'Authorization': 'Bearer ' + token } })
        ]);
        var allPerms   = await results[0].json();
        var allCats    = await results[1].json();
        var allTypes   = await results[2].json();
        var allDepts   = await results[3].json();
        var allSectors = await results[4].json();

        var permOptions = allPerms.map(function(p) { return '<option value="' + p.code + '">' + p.name + '</option>'; }).join('');
        var catOptions  = '<option value="">Все категории</option>' + allCats.map(function(c) { return '<option value="' + c.categoryId + '">' + c.name + '</option>'; }).join('');
        var typeOptions = '<option value="">Все типы</option>' + allTypes.map(function(t) { return '<option value="' + t.documentTypeId + '">' + t.name + '</option>'; }).join('');
        var deptOptions = '<option value="">Все подразделения</option>' + allDepts.map(function(d) { return '<option value="' + d.departmentId + '">' + d.name + '</option>'; }).join('');
        var sectOptions = '<option value="">Все секторы</option>' + allSectors.map(function(s) { return '<option value="' + s.sectorId + '">' + s.name + '</option>'; }).join('');

        var fields =
            '<div class="form-group"><label>Полномочие *</label><select name="permissionCode" required>' + permOptions + '</select></div>' +
            '<div class="form-group"><label>Категория</label><select name="categoryId">' + catOptions + '</select></div>' +
            '<div class="form-group"><label>Тип документа</label><select name="documentTypeId">' + typeOptions + '</select></div>' +
            '<div class="form-group"><label>Подразделение</label><select name="departmentId">' + deptOptions + '</select></div>' +
            '<div class="form-group"><label>Сектор</label><select name="sectorId">' + sectOptions + '</select></div>';

        showModal('Выдача полномочия должности', fields, async function(formData) {
            Object.keys(formData).forEach(function(key) {
                if (formData[key] === '') delete formData[key];
            });

            try {
                var res = await fetch('/api/admin/positions/' + positionId + '/permissions', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                    body: JSON.stringify(formData)
                });
                if (!res.ok) {
                    var err = await res.json().catch(function() { return {}; });
                    throw new Error(err.error || 'Ошибка выдачи полномочия');
                }
                closeFormModal();
                await openPositionPermissions(positionId);
            } catch (e) {
                showAlert('Ошибка', e.message);
            }
        });
    } catch (e) {
        showAlert('Ошибка', 'Ошибка загрузки справочников для модалки: ' + e.message);
    }
}

function confirmRevokePermission(userId, permId) {
    showConfirm(
        'Отзыв полномочия',
        'Вы уверены, что хотите отозвать это полномочие у пользователя?',
        async function () {
            try {
                await fetch('/api/admin/users/' + userId + '/permissions/' + permId, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                await openUserPermissions(userId);
            } catch (e) { showAlert('Ошибка', 'Ошибка удаления'); }
        }
    );
}

function confirmRevokePositionPermission(positionId, permId) {
    showConfirm(
        'Отзыв полномочия',
        'Вы уверены, что хотите отозвать это полномочие у должности?',
        async function () {
            try {
                await fetch('/api/admin/positions/' + positionId + '/permissions/' + permId, {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                await openPositionPermissions(positionId);
            } catch (e) { showAlert('Ошибка', 'Ошибка удаления'); }
        }
    );
}