document.querySelectorAll('[data-submit-select]').forEach(select => {
    select.addEventListener('change', () => select.form.requestSubmit());
});
const search = document.getElementById('prediction-search');
const scope = document.getElementById('prediction-scope');
function filterPredictions() {
    if (!search) return;
    const query = search.value.trim().toUpperCase();
    let visible = 0;
    document.querySelectorAll('[data-symbol]').forEach(row => {
        const matchesScope = scope.value === 'all' ||
            (scope.value === 'interest' && row.dataset.interest === 'true') ||
            (scope.value === 'selected' && row.dataset.selected === 'true');
        row.hidden = !matchesScope || !row.dataset.symbol.toUpperCase().includes(query);
        if (!row.hidden) visible++;
    });
    document.getElementById('search-empty').hidden = visible > 0;
    document.getElementById('prediction-count').textContent = `${visible}종목`;
}
if (search) {
    search.addEventListener('input', filterPredictions);
    scope.addEventListener('change', filterPredictions);
    filterPredictions();
}
const probabilityPeriod = document.getElementById('probability-period');
if (probabilityPeriod) {
    const showProbabilityGroup = () => {
        document.querySelectorAll('[data-probability-group]').forEach(group => {
            group.hidden = group.dataset.probabilityGroup !== probabilityPeriod.value;
        });
    };
    probabilityPeriod.addEventListener('change', showProbabilityGroup);
    showProbabilityGroup();
}
