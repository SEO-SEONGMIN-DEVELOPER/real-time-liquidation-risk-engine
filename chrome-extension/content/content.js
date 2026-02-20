(() => {
  'use strict';

  const BACKEND_BASE_URL = 'http://localhost:8080';
  const WS_URL = 'ws://localhost:8080/ws';

  let riskSubscription = null;
  let registeredSymbol = null;
  let lastRegisteredKey = null;

  function bootstrap() {
    console.log('[RiskEngine] 확장 프로그램 로드됨');
    initRiskWidget();
    listenForLangChange();
  }

  function initRiskWidget() {
    chrome.storage?.sync?.get(['liqRiskWidgetPosition', 'riskMonitorLang'], (result) => {
      if (result.riskMonitorLang) I18n.setLang(result.riskMonitorLang);
      RiskWidgetRenderer.create(result.liqRiskWidgetPosition || null, {
        onRegister: (pos) => {
          registerPositionToBackend(pos);
        },
        onUnregister: (symbol) => {
          unregisterPosition(symbol);
        }
      });
      connectRiskWebSocket();
    });
  }

  function listenForLangChange() {
    chrome.runtime?.onMessage?.addListener((msg) => {
      if (msg.type === 'CHANGE_LANG' && msg.lang) {
        I18n.setLang(msg.lang);
        RiskWidgetRenderer.applyI18n();
      }
    });
  }

  function connectRiskWebSocket() {
    StompClient.connect(WS_URL, () => {
      console.log('[RiskEngine] STOMP 연결 완료');
      if (registeredSymbol) {
        subscribeToSymbol(registeredSymbol);
      }
    }, () => {
      console.log('[RiskEngine] STOMP 연결 끊김, 자동 재연결 대기...');
    });
  }

  function subscribeToSymbol(symbol) {
    if (riskSubscription) {
      riskSubscription.unsubscribe();
      riskSubscription = null;
    }

    const destination = '/topic/risk/' + symbol.toUpperCase();
    riskSubscription = StompClient.subscribe(destination, (report) => {
      if (report) {
        console.log('[RiskEngine] Risk update via STOMP:', report.riskLevel, report.cascadeReachProbability);
        RiskWidgetRenderer.update(report);
      }
    });

    console.log('[RiskEngine] STOMP 구독 시작:', destination);
  }

  function unregisterPosition(symbol) {
    if (!symbol) return;
    const normalized = symbol.toUpperCase();

    fetch(`${BACKEND_BASE_URL}/api/position/unregister?symbol=${normalized}`, { method: 'DELETE' })
      .then(res => res.json())
      .then(data => {
        console.log('[RiskEngine] 포지션 해제:', data.message);
      })
      .catch(err => {
        console.debug('[RiskEngine] 포지션 해제 실패:', err.message);
      });

    if (riskSubscription) {
      riskSubscription.unsubscribe();
      riskSubscription = null;
    }
    registeredSymbol = null;
    lastRegisteredKey = null;
  }

  function registerPositionToBackend(pos) {
    const regKey = `${pos.symbol}|${pos.liquidationPrice}|${pos.side}|${pos.leverage}`;
    if (regKey === lastRegisteredKey) return;

    const body = {
      symbol: pos.symbol,
      liquidationPrice: pos.liquidationPrice,
      positionSide: pos.side || 'LONG',
      leverage: pos.leverage || 1
    };

    fetch(`${BACKEND_BASE_URL}/api/position/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          lastRegisteredKey = regKey;
          const sym = pos.symbol.toUpperCase();

          if (registeredSymbol !== sym) {
            registeredSymbol = sym;
            if (StompClient.isConnected()) {
              subscribeToSymbol(sym);
            }
          }

          console.log('[RiskEngine] 포지션 등록 완료:', body);
        }
      })
      .catch(err => {
        console.debug('[RiskEngine] 포지션 등록 실패:', err.message);
      });
  }

  bootstrap();
})();
