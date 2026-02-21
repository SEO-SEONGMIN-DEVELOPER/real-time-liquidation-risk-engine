document.addEventListener('DOMContentLoaded', () => {
  chrome.storage.sync.get(['riskMonitorLang'], (result) => {
    const lang = result.riskMonitorLang || 'en';
    I18n.setLang(lang);
    applyTranslations();
    setActiveBtn(lang);
  });

  document.getElementById('lang-selector').addEventListener('click', (e) => {
    const btn = e.target.closest('.lang-btn');
    if (!btn) return;
    const lang = btn.dataset.lang;
    I18n.setLang(lang);
    chrome.storage.sync.set({ riskMonitorLang: lang });
    applyTranslations();
    setActiveBtn(lang);

    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.tabs.sendMessage(tabs[0].id, { type: 'CHANGE_LANG', lang }).catch(() => {});
      }
    });
  });

  function setActiveBtn(lang) {
    document.querySelectorAll('.lang-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.lang === lang);
    });
  }

  function setDtLabel(id, text) {
    const el = document.getElementById(id);
    if (!el) return;
    const dot = el.querySelector('.dot');
    if (dot) {
      el.textContent = '';
      el.appendChild(dot);
      el.appendChild(document.createTextNode(' ' + text));
    } else {
      el.textContent = text;
    }
  }

  function applyTranslations() {
    const t = (k) => I18n.get(k);

    document.getElementById('popup-title').textContent = t('popupTitle');
    document.getElementById('popup-intro').innerHTML = t('popupIntro');
    document.getElementById('popup-usage-title').textContent = t('popupUsageTitle');
    document.getElementById('popup-usage-1').innerHTML = t('popupUsage1');
    document.getElementById('popup-usage-2').innerHTML = t('popupUsage2');
    document.getElementById('popup-usage-3').innerHTML = t('popupUsage3');
    document.getElementById('popup-usage-4').innerHTML = t('popupUsage4');

    document.getElementById('popup-mc-title').textContent = t('popupMcTitle');
    document.getElementById('popup-mc-desc').textContent = t('popupMcDesc');
    setDtLabel('label-mc-risk', t('labelMcRisk'));
    document.getElementById('label-horizon').textContent = t('labelHorizon');
    document.getElementById('label-sigma').textContent = t('labelSigma');
    document.getElementById('label-paths').textContent = t('labelPaths');
    document.getElementById('desc-mc-risk').textContent = t('descMcRisk');
    document.getElementById('desc-mc-horizon').textContent = t('descMcHorizon');
    document.getElementById('desc-mc-sigma').textContent = t('descMcSigma');
    document.getElementById('desc-mc-paths').textContent = t('descMcPaths');

    document.getElementById('popup-cascade-title').textContent = t('popupCascadeTitle');
    document.getElementById('popup-cascade-desc').textContent = t('popupCascadeDesc');

    document.getElementById('popup-basic-title').textContent = t('popupBasicTitle');
    setDtLabel('label-risk-level', t('labelRiskLevel'));
    document.getElementById('label-reach-prob').textContent = t('labelReachProb');
    document.getElementById('label-distance').textContent = t('labelDistance');
    document.getElementById('label-direction').textContent = t('labelDirection');
    document.getElementById('desc-risk-level').textContent = t('descRiskLevel');
    document.getElementById('desc-reach-prob').textContent = t('descReachProb');
    document.getElementById('desc-distance').textContent = t('descDistance');
    document.getElementById('desc-direction').textContent = t('descDirection');

    document.getElementById('popup-detail-title').textContent = t('popupDetailTitle');
    document.getElementById('label-density').textContent = t('labelDensity');
    document.getElementById('label-depth-notional').textContent = t('labelDepthNotional');
    document.getElementById('label-levels-ratio').textContent = t('labelLevelsRatio');
    document.getElementById('label-clusters').textContent = t('labelClusters');
    document.getElementById('label-oi').textContent = t('labelOI');
    document.getElementById('label-liq').textContent = t('labelLiq');
    document.getElementById('label-imbal').textContent = t('labelImbal');
    document.getElementById('desc-density').textContent = t('descDensity');
    document.getElementById('desc-depth-notional').textContent = t('descDepthNotional');
    document.getElementById('desc-levels-ratio').textContent = t('descLevelsRatio');
    document.getElementById('desc-clusters').textContent = t('descClusters');
    document.getElementById('desc-oi').textContent = t('descOI');
    document.getElementById('desc-liq').textContent = t('descLiq');
    document.getElementById('desc-imbal').textContent = t('descImbal');
    document.getElementById('popup-footer').textContent = t('popupFooter');
  }
});
