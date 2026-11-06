var token = localStorage.getItem('token');
var roleCode = (localStorage.getItem('roleCode') || 'EMPLOYEE').toUpperCase();
var isAdmin = roleCode === 'ADMIN';
var currentUserId = parseInt(localStorage.getItem('userId'));
if (!token) window.location.href = '/login';

// СТАТУСЫ ДОКУМЕНТА
var STATUS_MAP = {
    'DRAFT':       'Черновик',
    'IN_PROGRESS': 'В процессе',
    'REVISION':    'Пересмотр',
    'COMPLETED':   'Выполнен',
    'ARCHIVED':    'В архиве',
    // устаревшие / защитные значения
    'REJECTED':    'Отклонён',
    'PENDING':     'Не прочитано'
};

// СТАТУСЫ ЭТАПА МАРШРУТА
var ROUTE_STATUS_MAP = {
    'PENDING':     'Не прочитано',
    'WAITING':     'Ожидает',
    'IN_PROGRESS': 'В процессе',
    'COMPLETED':   'Выполнен',
    'REJECTED':    'Отклонён',
    'CANCELLED':   'Отменён'
};

var TASKS = [
    { code: 'APPROVAL',     name: 'На согласовании', action: 'APPROVE' },
    { code: 'SIGNING',      name: 'На подписании',   action: 'SIGN' },
    { code: 'REGISTRATION', name: 'На регистрации',  action: 'REGISTER' },
    { code: 'REVIEW',       name: 'На рассмотрении', action: 'REVIEW' },
    { code: 'AWARENESS',    name: 'На ознакомлении', action: 'READ' },
    { code: 'EXECUTION',    name: 'На исполнении',   action: 'EXECUTE' }
];

var CATEGORIES = [];

var ADMIN_SECTIONS = [
    { code: 'REFERENCE',   name: 'Управление справочниками' },
    { code: 'PERMISSIONS', name: 'Управление полномочиями' }
];

var REF_TYPES = [
    { code: 'departments',    name: 'Подразделения' },
    { code: 'sectors',        name: 'Секторы' },
    { code: 'nomenclatures',  name: 'Номенклатуры дел' },
    { code: 'doc-types',      name: 'Категории документов' },
    { code: 'doc-categories', name: 'Типы документов' },
    { code: 'doc-actions',    name: 'Действия' },
    { code: 'route-stages',   name: 'Этапы маршрутов' },
    { code: 'base-roles',     name: 'Базовые роли' },
    { code: 'positions',      name: 'Должности' },
    { code: 'permissions',    name: 'Полномочия' },
    { code: 'users',          name: 'Пользователи' }
];

var ALL_DOC_COLUMNS = [
    { key: 'createdAt',          label: 'Дата',              fmt: v => v ? new Date(v).toLocaleDateString('ru-RU') : '—', defaultVisible: true },
    { key: 'registrationNumber', label: 'Рег. номер',        fmt: v => v || '—', defaultVisible: true },
    { key: 'title',              label: 'Название',          defaultVisible: true },
    { key: 'status',             label: 'Статус',            fmt: v => STATUS_MAP[v] || v || '—', defaultVisible: true },
    { key: 'authorName',         label: 'Автор',             defaultVisible: true },
    { key: 'completedAt',        label: 'Дата завершения',   fmt: v => v ? new Date(v).toLocaleDateString('ru-RU') : '—', defaultVisible: true }
];

var ALL_TASK_COLUMNS = [
    { key: 'title',       label: 'Содержание',     defaultVisible: true },
    { key: 'routeStatus', label: 'Статус задачи',  fmt: v => ROUTE_STATUS_MAP[v] || v || '—', defaultVisible: true },
    { key: 'deadline',    label: 'Срок',           fmt: v => v ? new Date(v).toLocaleDateString('ru-RU') : '—', defaultVisible: true },
    { key: 'authorName',  label: 'Отправитель',    defaultVisible: true }
];

var currentSection = null;
var currentData = [];
var currentRefType = 'departments';

var currentPermissionUserId = null;
var currentPositionId       = null;
var currentPermissionMode   = 'users';   // 'users' | 'positions'

var activeFilters = {};
var sortConfig = { key: null, direction: 'asc' };
var pageSize = 50;
var columnVisibility = {};
var currentFilterKey = null;
var currentDocId = null;
var currentDocAuthor = '';
var currentRouteId = null;
var currentActionCode = null;
var allUsers = [];
var donutChartInstance = null;
var barChartInstance = null;

ALL_DOC_COLUMNS.forEach(c => columnVisibility[c.key] = c.defaultVisible);
ALL_TASK_COLUMNS.forEach(c => columnVisibility[c.key] = c.defaultVisible);

function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (ch) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
}

function formatSize(b) {
    if (b < 1024) return b + ' Б';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' КБ';
    return (b / 1024 / 1024).toFixed(1) + ' МБ';
}

function logout() {
    localStorage.clear();
    window.location.href = '/login';
}

async function loadCategories() {
    try {
        var resp = await fetch('/api/ref/doc-categories', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        var data = await resp.json();
        CATEGORIES = (data || [])
            .filter(c => c.isActive !== false)
            .map(function (c) {
                return { code: c.code, name: c.name, categoryId: c.categoryId };
            });
    } catch (e) {
        console.error('Не удалось загрузить категории документов:', e);
        CATEGORIES = [];
    }
}

document.addEventListener('DOMContentLoaded', async function () {
    var userNameEl = document.getElementById('userName');
    var userRoleEl = document.getElementById('userRole');
    if (userNameEl) userNameEl.textContent = localStorage.getItem('fullName') || 'Пользователь';
    if (userRoleEl) userRoleEl.textContent = isAdmin ? 'Администратор' : 'Сотрудник';

    fetch('/api/ref/users', { headers: { 'Authorization': 'Bearer ' + token } })
        .then(r => r.json())
        .then(data => { allUsers = data.filter(u => u.isActive); })
        .catch(e => console.error(e));

    await loadCategories();
    renderSidebar();

    var defaultSection = isAdmin
        ? { type: 'admin', code: 'REFERENCE' }
        : { type: 'task', code: 'EXECUTION' };

    var el = document.querySelector('.nav-item[data-key="' + defaultSection.type + ':' + defaultSection.code + '"]');
    loadSection(defaultSection).then(function () { if (el) el.classList.add('nav-item--active'); });

    document.addEventListener('click', function (e) {
        var popover = document.getElementById('filterPopover');
        if (popover && popover.style.display === 'flex' && !popover.contains(e.target) && !e.target.classList.contains('filter-icon')) {
            closeFilterPopover();
        }
    });
});

function showAlert(title, message) {
    var titleEl = document.getElementById('alertTitle');
    var msgEl = document.getElementById('alertMessage');
    var modal = document.getElementById('alertModal');
    if (titleEl && msgEl && modal) {
        titleEl.textContent = title;
        msgEl.textContent = message;
        modal.classList.add('open');
    } else {
        console.log(title + ': ' + message);
    }
}

function showConfirm(title, message, onConfirm) {
    var titleEl = document.getElementById('confirmTitle');
    var msgEl = document.getElementById('confirmMessage');
    var btn = document.getElementById('btnConfirmAction');
    var modal = document.getElementById('confirmModal');

    if (titleEl && msgEl && btn && modal) {
        titleEl.textContent = title;
        msgEl.textContent = message;

        btn.onclick = function() {
            modal.classList.remove('open');
            onConfirm();
        };

        modal.classList.add('open');
    }
}