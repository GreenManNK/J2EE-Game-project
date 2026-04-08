(function () {
  const app = document.getElementById("typing-practice-app");
  if (!app) {
    return;
  }

  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : function (value) { return value; };
  const historyApi = window.CaroHistory || {};
  const STATS_STORAGE_KEY = "caroTypingPracticeStats.v1";
  const GAME_STATS_CODE = "typing-practice";

  const state = {
    botEnabled: String(app.dataset.botEnabled || "false").toLowerCase() === "true",
    difficulty: String(app.dataset.botDifficulty || "easy").toLowerCase() === "hard" ? "hard" : "easy",
    texts: [],
    index: 0,
    currentText: "",
    raceStartedAt: 0,
    raceEndsAt: 0,
    timerHandle: null,
    active: false,
    finished: false,
    phase: "loading",
    lastOutcome: "",
    botProgress: 0,
    botWpm: 0,
    historyItems: [],
    matchCode: "",
    historyRecorded: false,
    stats: {
      totalGames: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      bestWpm: 0,
      bestAccuracy: 0,
      completedQuotes: 0
    }
  };

  const els = {
    progress: document.getElementById("typing-practice-progress"),
    wpm: document.getElementById("typing-practice-wpm"),
    accuracy: document.getElementById("typing-practice-accuracy"),
    timer: document.getElementById("typing-practice-timer"),
    text: document.getElementById("typing-practice-text"),
    input: document.getElementById("typing-practice-input"),
    playerBar: document.getElementById("typing-practice-player-bar"),
    playerMeta: document.getElementById("typing-practice-player-meta"),
    botBar: document.getElementById("typing-practice-bot-bar"),
    botMeta: document.getElementById("typing-practice-bot-meta"),
    start: document.getElementById("typing-practice-start"),
    next: document.getElementById("typing-practice-next"),
    restart: document.getElementById("typing-practice-restart"),
    feedback: document.getElementById("typing-practice-feedback"),
    status: document.getElementById("typing-practice-status"),
    result: document.getElementById("typing-practice-result"),
    history: document.getElementById("typing-practice-history"),
    statsTotal: document.getElementById("typing-practice-stats-total"),
    statsRecord: document.getElementById("typing-practice-stats-record"),
    statsBestWpm: document.getElementById("typing-practice-stats-best-wpm"),
    statsBestAccuracy: document.getElementById("typing-practice-stats-best-accuracy"),
    statsCompleted: document.getElementById("typing-practice-stats-completed")
  };

  document.addEventListener("DOMContentLoaded", init);

  async function init() {
    bindActions();
    state.stats = await readStats();
    await loadTexts();
    updateStatsUi();
    prepareText(0);
  }

  function syncMotionState(nextPhase) {
    if (nextPhase) {
      state.phase = nextPhase;
    }
    app.dataset.phase = String(state.phase || "ready");
    app.dataset.urgent = app.dataset.urgent === "true" ? "true" : "false";
    app.dataset.outcome = String(state.lastOutcome || "");
  }

  function bindActions() {
    els.start.addEventListener("click", function () {
      startRace(false);
    });
    els.next.addEventListener("click", function () {
      const nextIndex = state.texts.length ? (state.index + 1) % state.texts.length : 0;
      prepareText(nextIndex);
    });
    els.restart.addEventListener("click", function () {
      startRace(true);
    });
    els.input.addEventListener("input", handleTypingInput);
  }

  async function loadTexts() {
    setFeedback("Dang tai quote practice...", "");
    try {
      const response = await fetch(appPath("/games/typing/practice/texts"), { cache: "no-store" });
      if (!response.ok) {
        throw new Error("Khong tai duoc quote practice");
      }
      const payload = await response.json();
      const texts = Array.isArray(payload.texts) ? payload.texts : [];
      state.texts = texts.map(function (item) {
        return String(item || "").trim();
      }).filter(Boolean);
      if (!state.texts.length) {
        throw new Error("Khong co quote nao san sang");
      }
      els.status.textContent = "Da san sang";
      setFeedback("Da tai xong quote practice.", "success");
    } catch (error) {
      state.texts = [];
      els.status.textContent = "Loi tai du lieu";
      els.text.textContent = "Khong tai duoc quote practice.";
      els.start.disabled = true;
      els.next.disabled = true;
      els.restart.disabled = true;
      setFeedback(error.message || "Khong tai duoc quote practice.", "error");
    }
  }

  function prepareText(index) {
    if (!state.texts.length) {
      return;
    }
    stopRaceTimer();
    state.index = clampIndex(index, state.texts.length);
    state.currentText = state.texts[state.index] || "";
    state.active = false;
    state.finished = false;
    state.lastOutcome = "";
    state.botProgress = 0;
    state.botWpm = 0;
    state.matchCode = newMatchCode();
    state.historyRecorded = false;
    els.input.value = "";
    els.input.disabled = true;
    els.timer.textContent = "60s";
    els.wpm.textContent = "0";
    els.accuracy.textContent = "0%";
    els.playerBar.style.width = "0%";
    els.playerMeta.textContent = "0 ky tu";
    if (els.botBar) {
      els.botBar.style.width = "0%";
    }
    if (els.botMeta) {
      els.botMeta.textContent = state.botEnabled ? "0%" : "-";
    }
    els.progress.textContent = (state.index + 1) + " / " + state.texts.length;
    els.status.textContent = state.botEnabled ? "Cho bat dau race voi bot" : "Cho bat dau practice";
    els.result.textContent = "Chua bat dau";
    renderTargetText(state.currentText, "");
    setFeedback("Nhan Bat dau van de chay quote nay.", "");
    app.dataset.urgent = "false";
    syncMotionState("ready");
  }

  function startRace(restartCurrent) {
    if (!state.currentText) {
      return;
    }
    if (!restartCurrent && state.active) {
      return;
    }
    if (restartCurrent) {
      prepareText(state.index);
    }
    state.active = true;
    state.finished = false;
    state.raceStartedAt = Date.now();
    state.raceEndsAt = state.raceStartedAt + 60000;
    state.botProgress = 0;
    state.botWpm = state.botEnabled
      ? (state.difficulty === "hard" ? randomBetween(58, 74) : randomBetween(28, 40))
      : 0;
    els.input.disabled = false;
    els.input.focus();
    els.status.textContent = state.botEnabled ? "Dang race voi bot" : "Dang practice";
    els.result.textContent = state.botEnabled ? "Chua chot keo" : "Dang luyen tap";
    setFeedback(state.botEnabled ? "Giu accuracy va ve dich truoc bot." : "Giu nhip go va ket thuc quote nhanh nhat co the.", "");
    syncMotionState("racing");
    updateRaceFrame();
    stopRaceTimer();
    state.timerHandle = window.setInterval(updateRaceFrame, 120);
  }

  function handleTypingInput() {
    if (!state.active || state.finished) {
      return;
    }
    updateRaceFrame();
    if (normalizeLineBreaks(els.input.value) === state.currentText) {
      finishRace("player-finish");
    }
  }

  function updateRaceFrame() {
    if (!state.currentText) {
      return;
    }
    const typedText = normalizeLineBreaks(els.input.value);
    renderTargetText(state.currentText, typedText);
    const stats = computePlayerStats(typedText, state.currentText, state.raceStartedAt);
    els.wpm.textContent = String(stats.wpm);
    els.accuracy.textContent = stats.accuracy.toFixed(1) + "%";
    els.playerMeta.textContent = typedText.length + " ky tu";
    els.playerBar.style.width = percentage(typedText.length, state.currentText.length).toFixed(2) + "%";

    if (state.botEnabled) {
      const elapsedMs = Math.max(0, Date.now() - state.raceStartedAt - (state.difficulty === "hard" ? 300 : 800));
      const projectedChars = Math.max(0, Math.floor(((elapsedMs / 60000) * state.botWpm * 5)));
      state.botProgress = Math.min(state.currentText.length, projectedChars);
      if (els.botBar) {
        els.botBar.style.width = percentage(state.botProgress, state.currentText.length).toFixed(2) + "%";
      }
      if (els.botMeta) {
        els.botMeta.textContent = state.botProgress + " ky tu | " + state.botWpm + " WPM";
      }
      if (state.botProgress >= state.currentText.length && normalizeLineBreaks(els.input.value) !== state.currentText) {
        finishRace("bot-finish");
        return;
      }
    }

    const remainingSeconds = Math.max(0, Math.ceil((state.raceEndsAt - Date.now()) / 1000));
    els.timer.textContent = remainingSeconds + "s";
    app.dataset.urgent = remainingSeconds <= 10 ? "true" : "false";
    if (Date.now() >= state.raceEndsAt) {
      finishRace("timeout");
    }
  }

  function finishRace(reason) {
    if (!state.active || state.finished) {
      return;
    }
    state.finished = true;
    state.active = false;
    stopRaceTimer();
    els.input.disabled = true;

    const typedText = normalizeLineBreaks(els.input.value);
    const stats = computePlayerStats(typedText, state.currentText, state.raceStartedAt);
    const playerComplete = typedText === state.currentText;
    const botComplete = state.botEnabled && state.botProgress >= state.currentText.length;
    let result = "Practice xong";
    let outcome = "draw";

    if (state.botEnabled) {
      if ((reason === "player-finish" && !botComplete) || (playerComplete && typedText.length >= state.botProgress)) {
        result = "Ban thang bot";
        outcome = "win";
      } else if (reason === "bot-finish" && !playerComplete) {
        result = "Bot thang";
        outcome = "loss";
      } else if (percentage(typedText.length, state.currentText.length) > percentage(state.botProgress, state.currentText.length)) {
        result = "Ban nhinh hon bot";
        outcome = "win";
      } else if (percentage(typedText.length, state.currentText.length) < percentage(state.botProgress, state.currentText.length)) {
        result = "Bot nhinh hon";
        outcome = "loss";
      } else {
        result = "Hoa bot";
        outcome = "draw";
      }
    } else if (playerComplete) {
      result = "Hoan thanh quote";
    } else {
      result = "Dung quote o muc hien tai";
    }
    state.lastOutcome = outcome;
    app.dataset.urgent = "false";
    syncMotionState("finished");

    els.status.textContent = "Da ket thuc";
    els.result.textContent = result;
    const historyLine = state.botEnabled
      ? ("Quote " + (state.index + 1) + ": " + result + " | " + stats.wpm + " WPM | " + stats.accuracy.toFixed(1) + "%")
      : ("Quote " + (state.index + 1) + ": " + stats.wpm + " WPM | " + stats.accuracy.toFixed(1) + "% accuracy");
    state.historyItems.push(historyLine);
    renderHistory();
    setFeedback(result + ". Ban co the chay lai hoac doi quote moi.", outcome === "loss" ? "error" : "success");
    applyStatsResult(outcome, stats, playerComplete);
    void recordBotHistory(outcome, stats);
  }

  function renderHistory() {
    els.history.innerHTML = "";
    if (!state.historyItems.length) {
      const li = document.createElement("li");
      li.className = "typing-practice-history__item";
      li.textContent = "Chua co quote nao duoc chay.";
      els.history.appendChild(li);
      return;
    }
    state.historyItems.slice().reverse().forEach(function (item, index) {
      const li = document.createElement("li");
      li.className = "typing-practice-history__item" + (index === 0 ? " is-new" : "");
      li.textContent = item;
      els.history.appendChild(li);
    });
  }

  function renderTargetText(sourceText, typedText) {
    let correctUntil = 0;
    const limit = Math.min(sourceText.length, typedText.length);
    for (let i = 0; i < limit; i += 1) {
      if (sourceText[i] !== typedText[i]) {
        break;
      }
      correctUntil += 1;
    }
    const correctPart = escapeHtml(sourceText.slice(0, correctUntil));
    const wrongPart = escapeHtml(sourceText.slice(correctUntil, typedText.length));
    const pendingPart = escapeHtml(sourceText.slice(typedText.length));
    els.text.innerHTML = "<span class=\"typed-correct\">" + correctPart + "</span>"
      + "<span class=\"typed-wrong\">" + wrongPart + "</span>"
      + "<span class=\"typed-pending\">" + pendingPart + "</span>";
  }

  function computePlayerStats(typedText, sourceText, startedAt) {
    let correctChars = 0;
    const limit = Math.min(typedText.length, sourceText.length);
    for (let i = 0; i < limit; i += 1) {
      if (typedText[i] === sourceText[i]) {
        correctChars += 1;
      }
    }
    const accuracy = typedText.length === 0 ? 0 : ((correctChars / typedText.length) * 100);
    const elapsedMinutes = startedAt ? Math.max((Date.now() - startedAt) / 60000, 1 / 60) : (1 / 60);
    const wpm = Math.max(0, Math.round((typedText.length / 5) / elapsedMinutes));
    return { accuracy: accuracy, wpm: wpm };
  }

  async function recordBotHistory(outcome, stats) {
    if (!state.botEnabled || state.historyRecorded || typeof historyApi.recordBotMatch !== "function") {
      return;
    }
    state.historyRecorded = true;
    try {
      await historyApi.recordBotMatch({
        gameCode: "typing",
        difficulty: state.difficulty,
        outcome: outcome,
        totalMoves: Math.max(state.currentText.length, Math.round(stats.wpm)),
        firstPlayerRole: "player",
        matchCode: state.matchCode || newMatchCode()
      });
    } catch (_) {
      state.historyRecorded = false;
    }
  }

  function defaultStats() {
    return {
      totalGames: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      bestWpm: 0,
      bestAccuracy: 0,
      completedQuotes: 0
    };
  }

  function normalizeStats(raw) {
    const source = raw && typeof raw === "object" ? raw : {};
    const totalGames = Math.max(0, Number.parseInt(String(source.totalGames || 0), 10) || 0);
    const wins = Math.max(0, Number.parseInt(String(source.wins || 0), 10) || 0);
    const losses = Math.max(0, Number.parseInt(String(source.losses || 0), 10) || 0);
    const draws = Math.max(0, Number.parseInt(String(source.draws || 0), 10) || 0);
    const bestAccuracy = clampAccuracy(source.bestAccuracy);
    return {
      totalGames: Math.max(totalGames, wins + losses + draws),
      wins: wins,
      losses: losses,
      draws: draws,
      bestWpm: Math.max(0, Number.parseInt(String(source.bestWpm || 0), 10) || 0),
      bestAccuracy: bestAccuracy,
      completedQuotes: Math.max(0, Number.parseInt(String(source.completedQuotes || 0), 10) || 0)
    };
  }

  function hasAnyStats(stats) {
    const safe = normalizeStats(stats);
    return safe.totalGames > 0
      || safe.wins > 0
      || safe.losses > 0
      || safe.draws > 0
      || safe.bestWpm > 0
      || safe.bestAccuracy > 0
      || safe.completedQuotes > 0;
  }

  function mergeStats(primary, secondary) {
    const first = normalizeStats(primary);
    const second = normalizeStats(secondary);
    return {
      totalGames: Math.max(first.totalGames, second.totalGames),
      wins: Math.max(first.wins, second.wins),
      losses: Math.max(first.losses, second.losses),
      draws: Math.max(first.draws, second.draws),
      bestWpm: Math.max(first.bestWpm, second.bestWpm),
      bestAccuracy: Math.max(first.bestAccuracy, second.bestAccuracy),
      completedQuotes: Math.max(first.completedQuotes, second.completedQuotes)
    };
  }

  function currentSessionUserId() {
    const current = window.CaroUser?.get?.();
    const userId = current && current.userId ? String(current.userId).trim() : "";
    return userId || null;
  }

  function readStatsFromStorage() {
    try {
      const raw = window.localStorage.getItem(STATS_STORAGE_KEY);
      if (!raw) {
        return defaultStats();
      }
      return normalizeStats(JSON.parse(raw));
    } catch (_) {
      return defaultStats();
    }
  }

  function writeStatsToStorage(stats) {
    try {
      window.localStorage.setItem(STATS_STORAGE_KEY, JSON.stringify(normalizeStats(stats)));
    } catch (_) {
    }
  }

  async function readStats() {
    const localStats = readStatsFromStorage();
    const userId = currentSessionUserId();
    const accountStats = window.CaroAccountStats;
    if (!userId || !accountStats || typeof accountStats.get !== "function") {
      return localStats;
    }

    try {
      const remoteRaw = await accountStats.get(GAME_STATS_CODE);
      const remoteStats = normalizeStats(remoteRaw);
      const merged = mergeStats(remoteStats, localStats);
      if (hasAnyStats(localStats) && typeof accountStats.save === "function") {
        const saved = await accountStats.save(GAME_STATS_CODE, merged, true);
        if (saved) {
          window.localStorage.removeItem(STATS_STORAGE_KEY);
        }
      }
      return merged;
    } catch (_) {
      return localStats;
    }
  }

  function writeStats() {
    const normalized = normalizeStats(state.stats);
    state.stats = normalized;

    const userId = currentSessionUserId();
    const accountStats = window.CaroAccountStats;
    if (userId && accountStats && typeof accountStats.save === "function") {
      accountStats.save(GAME_STATS_CODE, normalized, true)
        .then(function (saved) {
          if (saved) {
            window.localStorage.removeItem(STATS_STORAGE_KEY);
            return;
          }
          writeStatsToStorage(normalized);
        })
        .catch(function () {
          writeStatsToStorage(normalized);
        });
      return;
    }

    writeStatsToStorage(normalized);
  }

  function updateStatsUi() {
    const stats = normalizeStats(state.stats);
    state.stats = stats;
    if (els.statsTotal) {
      els.statsTotal.textContent = String(stats.totalGames);
    }
    if (els.statsRecord) {
      els.statsRecord.textContent = String(stats.wins) + "/" + String(stats.losses) + "/" + String(stats.draws);
    }
    if (els.statsBestWpm) {
      els.statsBestWpm.textContent = String(stats.bestWpm);
    }
    if (els.statsBestAccuracy) {
      els.statsBestAccuracy.textContent = stats.bestAccuracy.toFixed(1) + "%";
    }
    if (els.statsCompleted) {
      els.statsCompleted.textContent = String(stats.completedQuotes);
    }
  }

  function applyStatsResult(outcome, stats, playerComplete) {
    state.stats.totalGames += 1;
    state.stats.bestWpm = Math.max(state.stats.bestWpm, Math.max(0, Number(stats && stats.wpm || 0)));
    state.stats.bestAccuracy = Math.max(state.stats.bestAccuracy, clampAccuracy(stats && stats.accuracy));
    if (playerComplete) {
      state.stats.completedQuotes += 1;
    }

    if (state.botEnabled) {
      if (outcome === "win") {
        state.stats.wins += 1;
      } else if (outcome === "loss") {
        state.stats.losses += 1;
      } else {
        state.stats.draws += 1;
      }
    }

    writeStats();
    updateStatsUi();
  }

  function setFeedback(message, type) {
    els.feedback.textContent = String(message || "");
    els.feedback.classList.remove("error", "success");
    if (type) {
      els.feedback.classList.add(type);
    }
    app.dataset.feedback = String(type || "neutral");
  }

  function stopRaceTimer() {
    if (state.timerHandle) {
      window.clearInterval(state.timerHandle);
      state.timerHandle = null;
    }
  }

  function clampIndex(index, total) {
    if (!total) {
      return 0;
    }
    return Math.max(0, Math.min(total - 1, Number(index) || 0));
  }

  function percentage(current, total) {
    if (!total) {
      return 0;
    }
    return Math.min(100, (Math.max(0, current) / total) * 100);
  }

  function newMatchCode() {
    return typeof historyApi.newMatchCode === "function"
      ? historyApi.newMatchCode("typing-bot-" + state.difficulty)
      : ("BOT-TYPING-" + Date.now());
  }

  function normalizeLineBreaks(value) {
    return String(value || "").replace(/\r\n/g, "\n");
  }

  function randomBetween(min, max) {
    return Math.floor(Math.random() * ((max - min) + 1)) + min;
  }

  function clampAccuracy(value) {
    const parsed = Number.parseFloat(String(value == null ? 0 : value));
    const safe = Number.isFinite(parsed) ? parsed : 0;
    return Math.max(0, Math.min(100, Math.round(safe * 10) / 10));
  }

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
