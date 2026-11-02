var hideDrafts = false;

function toggleDraftsVisibility() {
    hideDrafts = document.getElementById('hideDraftsToggle').checked;
    applySearch();
}

function renderSidebar() {
    var sb = document.getElementById('sidebar');
    var html = '<p class="sidebar-label">Разделы</p><nav>';
    html += '<div class="nav-group"><button class="nav-group__title" onclick="toggleGroup(this)"><span>Задачи</span><span>−</span></button><div class="nav-group__items">';
    TASKS.forEach(function (t) {
        html += '<button class="nav-item" data-key="task:' + t.code + '" onclick="selectSection(\'task\',\'' + t.code + '\',this)">' + t.name + '</button>';
    });
    html += '</div></div>';

    html += '<div class="nav-group"><button class="nav-group__title" onclick="toggleGroup(this)"><span>Внутренний документооборот</span><span>−</span></button><div class="nav-group__items">';
    DOCS.forEach(function (d) {
        html += '<button class="nav-item" data-key="doc:' + d.code + '" onclick="selectSection(\'doc\',\'' + d.code + '\',this)">' + d.name + '</button>';
    });
    html += '</div></div>';

    if (isAdmin) {
        html += '<div class="nav-group"><button class="nav-group__title" onclick="toggleGroup(this)"><span>Администрирование</span><span>−</span></button><div class="nav-group__items">';
        ADMIN_SECTIONS.forEach(function (a) {
            html += '<button class="nav-item" data-key="admin:' + a.code + '" onclick="selectSection(\'admin\',\'' + a.code + '\',this)">' + a.name + '</button>';
        });
        html += '</div></div>';
    }
    html += '</nav>';
    sb.innerHTML = html;
}

function toggleGroup(btn) {
    var items = btn.nextElementSibling;
    var icon = btn.querySelector('span:last-child');
    items.style.display = items.style.display === 'none' ? 'flex' : 'none';
    icon.textContent = items.style.display === 'none' ? '+' : '−';
}

function selectSection(type, code, btn) {
    document.getElementById('standardView').style.display = 'block';
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('documentView').style.display = 'none';

    document.querySelectorAll('.nav-item').forEach(function (el) { el.classList.remove('nav-item--active'); });
    if (btn) btn.classList.add('nav-item--active');

    document.getElementById('tableSearch').value = '';
    activeFilters = {};
    sortConfig = { key: null, direction: 'asc' };
    currentPermissionUserId = null;
    closeViewSidebar();
    closeFilterPopover();

    loadSection({ type: type, code: code });
}

async function loadSection(section) {
    currentSection = section;
    currentData = [];

    var name = '';
    if (section.type === 'task') name = (TASKS.find(x => x.code === section.code) || {}).name || '';
    else if (section.type === 'doc') name = (DOCS.find(x => x.code === section.code) || {}).name || '';
    else name = (ADMIN_SECTIONS.find(x => x.code === section.code) || {}).name || '';

    document.getElementById('currentSectionTitle').textContent = name;
    document.getElementById('pageTitle').textContent = name;

    var refToolbar = document.getElementById('refToolbar');
    var taskStatusFilter = document.getElementById('taskStatusFilter');

    if (section.type === 'admin' && section.code === 'REFERENCE') {
        refToolbar.style.display = 'flex';
        taskStatusFilter.style.display = 'none';
        renderRefTypeSelector();
    } else if (section.type === 'task') {
        refToolbar.style.display = 'none';
        taskStatusFilter.style.display = 'block';
    } else {
        refToolbar.style.display = 'none';
        taskStatusFilter.style.display = 'none';
    }

    if (section.type === 'task') await loadTasks(section.code);
    else if (section.type === 'doc') await loadDocuments(section.code);
    else if (section.type === 'admin') {
        if (section.code === 'REFERENCE') await loadReferenceManagement();
        else await loadPermissionManagement();
    }
}

async function handleGlobalSearch(e) {
    if (e.key !== 'Enter') return;
    var query = e.target.value.trim();
    if (!query) return;
    document.getElementById('tableSearch').value = '';
    document.getElementById('refToolbar').style.display = 'none';
    document.getElementById('taskStatusFilter').style.display = 'none';

    document.getElementById('currentSectionTitle').textContent = 'Глобальный поиск';
    document.getElementById('pageTitle').textContent = 'Результаты: "' + query + '"';

    var container = document.getElementById('tableContainer');
    container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Поиск...</div>';

    try {
        var resp = await fetch('/api/documents/search?q=' + encodeURIComponent(query), {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json();
        data = data.map(function (d) {
            return Object.assign({}, d, {
                authorName: d.author ? d.author.lastName + ' ' + d.author.firstName.charAt(0) + '.' : '—'
            });
        });
        currentData = data;
        currentSection = { type: 'doc', code: 'SEARCH' };
        applySearch();
    } catch (error) {
        container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">' + error.message + '</div>';
    }
}

function renderTable(containerId, data, columns, isClickable, extraActions) {
    var container = document.getElementById(containerId);
    var visibleCols = columns.filter(function (c) { return columnVisibility[c.key] !== false; });

    if (!data.length) {
        container.innerHTML = '<div class="muted" style="padding:32px;text-align:center;">Нет данных</div>';
        return;
    }

    var html = '<table><thead><tr>';
    visibleCols.forEach(function (col) {
        var isSorted = sortConfig.key === col.key;
        var sortIcon = isSorted ? (sortConfig.direction === 'asc' ? '↑' : '↓') : '⇅';
        var isFiltered = activeFilters[col.key] ? 'active' : '';
        html += '<th data-key="' + col.key + '">' +
            '<div class="th-content">' +
                '<div class="th-title" onclick="handleSort(\'' + col.key + '\')">' +
                    '<span>' + col.label + '</span>' +
                    '<span class="sort-icon ' + (isSorted ? 'active' : '') + '">' + sortIcon + '</span>' +
                '</div>' +
                '<span class="filter-icon ' + isFiltered + '" onclick="openFilterPopover(event, \'' + col.key + '\', \'' + col.label + '\')">⏷</span>' +
            '</div></th>';
    });
    if (extraActions) html += '<th>Действия</th>';
    html += '</tr></thead><tbody>';

    data.slice(0, pageSize).forEach(function (row) {
        var clickAttr = isClickable ? 'onclick="openDocumentView(' + row.documentId + ')"' : '';
        html += '<tr ' + clickAttr + '>';
        visibleCols.forEach(function (col) {
            var val = row;
            col.key.split('.').forEach(function (k) { if (val) val = val[k]; });
            if (col.fmt) val = col.fmt(val, row);
            html += '<td>' + (val == null ? '—' : val) + '</td>';
        });
        if (extraActions) html += '<td onclick="event.stopPropagation()">' + extraActions(row) + '</td>';
        html += '</tr>';
    });
    html += '</tbody></table>';
    container.innerHTML = html;
    renderColumnToggles(columns);
}

function handleSort(key) {
    if (sortConfig.key === key) {
        sortConfig.direction = sortConfig.direction === 'asc' ? 'desc' : 'asc';
    } else {
        sortConfig.key = key;
        sortConfig.direction = 'asc';
    }
    applySearch();
}

function sortData(data) {
    if (!sortConfig.key) return data;
    return data.slice().sort(function (a, b) {
        var valA = a, valB = b;
        sortConfig.key.split('.').forEach(function (k) { if (valA) valA = valA[k]; });
        sortConfig.key.split('.').forEach(function (k) { if (valB) valB = valB[k]; });
        if (valA == null) return 1;
        if (valB == null) return -1;
        var strA = String(valA).toLowerCase();
        var strB = String(valB).toLowerCase();
        if (strA < strB) return sortConfig.direction === 'asc' ? -1 : 1;
        if (strA > strB) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
    });
}

function openFilterPopover(event, key, label) {
    event.stopPropagation();
    currentFilterKey = key;
    var popover = document.getElementById('filterPopover');
    var body = document.getElementById('filterPopoverBody');
    document.getElementById('filterPopoverTitle').textContent = 'Фильтр: ' + label;

    var currentVal = activeFilters[key] || '';
    if (key === 'status') {
        var html = '<select id="filterInput"><option value="">Все</option>';
        Object.keys(STATUS_MAP).forEach(function (code) {
            var name = STATUS_MAP[code];
            html += '<option value="' + name.toLowerCase() + '"' + (currentVal === name.toLowerCase() ? ' selected' : '') + '>' + name + '</option>';
        });
        body.innerHTML = html + '</select>';
    } else {
        body.innerHTML = '<input type="text" id="filterInput" value="' + currentVal + '" placeholder="Введите значение...">';
    }

    var rect = event.currentTarget.getBoundingClientRect();
    popover.style.top = (rect.bottom + 8) + 'px';
    var leftPos = rect.left - 100;
    if (leftPos < 10) leftPos = 10;
    if (leftPos + 260 > window.innerWidth) leftPos = window.innerWidth - 270;
    popover.style.left = leftPos + 'px';
    popover.style.display = 'flex';
    setTimeout(function () { var inp = document.getElementById('filterInput'); if (inp) inp.focus(); }, 50);
}

function applyFilter() {
    if (!currentFilterKey) return;
    var val = document.getElementById('filterInput').value.trim().toLowerCase();
    if (val) activeFilters[currentFilterKey] = val;
    else delete activeFilters[currentFilterKey];
    closeFilterPopover();
    applySearch();
}

function clearFilter() {
    if (currentFilterKey) {
        delete activeFilters[currentFilterKey];
        closeFilterPopover();
        applySearch();
    }
}

function closeFilterPopover() {
    document.getElementById('filterPopover').style.display = 'none';
    currentFilterKey = null;
}

function applySearch() {
    var q = document.getElementById('tableSearch').value.toLowerCase();
    var filtered = currentData.filter(function (row) {
        // Скрытие черновиков (если включено)
        if (hideDrafts && row.status === 'DRAFT') return false;

        if (q && !Object.keys(row).some(function (k) { return String(row[k]).toLowerCase().includes(q); })) return false;
        for (var key in activeFilters) {
            var rowVal = row;
            key.split('.').forEach(function (k) { if (rowVal) rowVal = rowVal[k]; });
            if (key === 'status') rowVal = STATUS_MAP[rowVal] || rowVal;
            if (!String(rowVal || '').toLowerCase().includes(activeFilters[key])) return false;
        }
        return true;
    });
    filtered = sortData(filtered);
    document.getElementById('docCount').textContent = filtered.length;

    if (currentSection.code === 'SEARCH') renderTable('tableContainer', filtered, ALL_DOC_COLUMNS, true, null);
    else if (currentSection.type === 'task') renderTable('tableContainer', filtered, ALL_TASK_COLUMNS, true, null);
    else if (currentSection.type === 'doc') renderTable('tableContainer', filtered, ALL_DOC_COLUMNS, true, null);
    else if (currentSection.type === 'admin' && currentSection.code === 'REFERENCE') renderRefTable(filtered);
    else if (currentSection.type === 'admin' && currentSection.code === 'PERMISSIONS' && !currentPermissionUserId) renderUsersTable(filtered);
}

function toggleViewSidebar() { document.getElementById('viewSidebar').classList.toggle('open'); }
function closeViewSidebar() { document.getElementById('viewSidebar').classList.remove('open'); }

function updatePageSize() {
    pageSize = parseInt(document.getElementById('pageSizeInput').value) || 50;
    applySearch();
}

function renderColumnToggles(columns) {
    var container = document.getElementById('columnToggles');
    container.innerHTML = '<h3 style="margin-top:16px; font-size:16px; color:var(--text-dark);">Отображаемые столбцы</h3>' +
        columns.map(function (c) {
            var checked = columnVisibility[c.key] !== false ? 'checked' : '';
            return '<label><input type="checkbox" ' + checked + ' onchange="toggleColumn(\'' + c.key + '\', this.checked)"> ' + c.label + '</label>';
        }).join('');
}

function toggleColumn(key, checked) {
    columnVisibility[key] = checked;
    applySearch();
}