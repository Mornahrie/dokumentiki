// Исполнительская дисциплина
async function showDashboard() {
    document.getElementById('standardView').style.display = 'none';
    document.getElementById('dashboardView').style.display = 'block';
    document.getElementById('documentView').style.display = 'none';

    document.querySelectorAll('.nav-item').forEach(function (el) { el.classList.remove('nav-item--active'); });
    closeViewSidebar();
    closeFilterPopover();

    try {
        var resp = await fetch('/api/analytics/discipline', { headers: { 'Authorization': 'Bearer ' + token } });
        var data = await resp.json();

        document.getElementById('dashboardScope').textContent =
            data.isAdmin ? 'Аналитика по всей организации'
                         : (data.isManager ? 'Аналитика по подразделению' : 'Ваша личная аналитика');
        document.getElementById('statActive').textContent = data.totalActive;
        document.getElementById('statOnTimePercent').textContent = data.onTimePercent + '%';
        document.getElementById('statOnTimeCount').textContent = data.completedOnTime;
        document.getElementById('statOverduePercent').textContent = data.overduePercent + '%';
        document.getElementById('statOverdueCount').textContent = data.overdue;

        renderCharts(data.onTimePercent, data.overduePercent, data.monthlyStats);

        if (data.employeeStats && data.employeeStats.length) {
            document.getElementById('employeesTableContainer').style.display = 'block';
            document.querySelector('#employeesTable tbody').innerHTML = data.employeeStats.map(function (s) {
                return '<tr>' +
                    '<td><strong>' + s.name + '</strong></td>' +
                    '<td>' + s.total + '</td>' +
                    '<td>' + s.onTime + '</td>' +
                    '<td style="color:#b00020;">' + s.overdue + '</td>' +
                    '<td><strong>' + s.efficiency + '%</strong></td>' +
                '</tr>';
            }).join('');
        } else {
            document.getElementById('employeesTableContainer').style.display = 'none';
        }
    } catch (e) { alert('Ошибка: ' + e.message); }
}

function renderCharts(onTimePercent, overduePercent, monthlyStats) {
    if (donutChartInstance) donutChartInstance.destroy();
    if (barChartInstance) barChartInstance.destroy();

    donutChartInstance = new Chart(document.getElementById('donutChart').getContext('2d'), {
        type: 'doughnut',
        data: {
            labels: ['Выполнено в срок', 'Просрочено'],
            datasets: [{ data: [onTimePercent, overduePercent], backgroundColor: ['#d91169', '#fbcfe8'], borderWidth: 0, cutout: '75%' }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'right' } } }
    });

    barChartInstance = new Chart(document.getElementById('barChart').getContext('2d'), {
        type: 'bar',
        data: {
            labels: monthlyStats.map(function (m) { return m.month; }),
            datasets: [
                { label: 'Поступило', data: monthlyStats.map(function (m) { return m.received; }), backgroundColor: '#d91169', borderRadius: 4, barPercentage: 0.6 },
                { label: 'Выполнено', data: monthlyStats.map(function (m) { return m.completed; }), backgroundColor: '#fbcfe8', borderRadius: 4, barPercentage: 0.6 }
            ]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom' } },
            scales: { y: { beginAtZero: true, grid: { color: '#fbcfe8' } }, x: { grid: { display: false } } }
        }
    });
}