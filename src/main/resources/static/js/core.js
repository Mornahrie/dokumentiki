var token = localStorage.getItem('token');
var roleCode = (localStorage.getItem('roleCode') || 'EMPLOYEE').toUpperCase();
var isAdmin = roleCode === 'ADMIN';
var currentUserId = parseInt(localStorage.getItem('userId'));
if (!token) window.location.href = '/login';

var STATUS_MAP = {
    'DRAFT': 'Черновик',
    'IN_PROGRESS': 'В работе',
    'COMPLETED': 'Выполнен',
    'REJECTED': 'Отклонен',
    'REVISION': 'На доработке',
    'PENDING': 'В ожидании',
    'ARCHIVED': 'В архиве'
};

var ROUTE_STATUS_MAP = {
    'PENDING': 'К выполнению',
    'IN_PROGRESS': 'В работе',
    'COMPLETED': 'Выполнено',
    'REJECTED': 'Отклонено',
    'WAITING': 'Ожидает',
    'CANCELLED': 'Отменено'
};

var TASKS = [
    { code: 'APPROVAL',     name: 'На согласовании', action: 'APPROVE' },
    { code: 'SIGNING',      name: 'На подписании',   action: 'SIGN' },
    { code: 'REGISTRATION', name: 'На регистрации',  action: 'REGISTER' },
    { code: 'REVIEW',       name: 'На рассмотрении', action: 'REVIEW' },
    { code: 'AWARENESS',    name: 'На ознакомлении', action: 'READ' },
    { code: 'EXECUTION',    name: 'На исполнении',   action: 'EXECUTE' }
];

// Теперь это КАТЕГОРИИ (в БД это doc_types)
var CATEGORIES = [
    { code: 'ORGANIZATIONAL', name: 'Организационные документы' },
    { code: 'ADMINISTRATIVE', name: 'Распорядительные документы' },
    { code: 'INFO_REFERENCE', name: 'Информационно-справочные документы' },
    { code: 'CONTRACTUAL',    name: 'Договорные документы' },
    { code: 'PROTOCOLS',      name: 'Протоколы' }
];

var ADMIN_SECTIONS = [
    { code: 'REFERENCE',   name: 'Управление справочниками' },
    { code: 'PERMISSIONS', name: 'Управление полномочиями' }
];

// ИСПРАВЛЕНЫ НАЗВАНИЯ: doc-types = Категории, doc-categories = Типы
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
    { key: 'title',      label: 'Содержание',  defaultVisible: true },
    { key: 'deadline',   label: 'Срок',        fmt: v => v ? new Date(v).toLocaleDateString('ru-RU') : '—', defaultVisible: true },
    { key: 'authorName', label: 'Отправитель', defaultVisible: true }
];

var currentSection = null;
var currentData = [];
var currentRefType = 'departments';
var currentPermissionUserId = null;
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

document.addEventListener('DOMContentLoaded', function () {
    var userNameEl = document.getElementById('userName');
    var userRoleEl = document.getElementById('userRole');
    if (userNameEl) userNameEl.textContent = localStorage.getItem('fullName') || 'Пользователь';
    if (userRoleEl) userRoleEl.textContent = isAdmin ? 'Администратор' : 'Сотрудник';

    fetch('/api/ref/users', { headers: { 'Authorization': 'Bearer ' + token } })
        .then(r => r.json())
        .then(data => { allUsers = data.filter(u => u.isActive); })
        .catch(e => console.error(e));

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