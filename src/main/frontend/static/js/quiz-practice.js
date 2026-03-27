(function () {
  const app = document.getElementById("quiz-practice-app");
  if (!app) {
    return;
  }

  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : function (value) { return value; };
  const historyApi = window.CaroHistory || {};
  const STATS_STORAGE_KEY = "caroQuizPracticeStats.v1";
  const GAME_STATS_CODE = "quiz-practice";

  const state = {
    botEnabled: String(app.dataset.botEnabled || "false").toLowerCase() === "true",
    difficulty: String(app.dataset.botDifficulty || "easy").toLowerCase() === "hard" ? "hard" : "easy",
    questions: [],
    index: 0,
    playerScore: 0,
    botScore: 0,
    timerSeconds: 15,
    deadlineAt: 0,
    timerHandle: null,
    awaitingTransition: false,
    historyItems: [],
    matchCode: "",
    historyRecorded: false,
    playerCorrectAnswers: 0,
    stats: {
      totalGames: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      bestScore: 0,
      perfectRounds: 0
    }
  };

  const els = {
    progress: document.getElementById("quiz-practice-progress"),
    playerScore: document.getElementById("quiz-practice-player-score"),
    botScore: document.getElementById("quiz-practice-bot-score"),
    timer: document.getElementById("quiz-practice-timer"),
    question: document.getElementById("quiz-practice-question"),
    options: document.getElementById("quiz-practice-options"),
    feedback: document.getElementById("quiz-practice-feedback"),
    submit: document.getElementById("quiz-practice-submit"),
    skip: document.getElementById("quiz-practice-skip"),
    restart: document.getElementById("quiz-practice-restart"),
    summaryStatus: document.getElementById("quiz-practice-status"),
    summaryResult: document.getElementById("quiz-practice-result"),
    history: document.getElementById("quiz-practice-history"),
    statsTotal: document.getElementById("quiz-practice-stats-total"),
    statsRecord: document.getElementById("quiz-practice-stats-record"),
    statsBest: document.getElementById("quiz-practice-stats-best"),
    statsPerfect: document.getElementById("quiz-practice-stats-perfect")
  };

  document.addEventListener("DOMContentLoaded", init);

  async function init() {
    bindActions();
    state.stats = await readStats();
    await loadQuestions();
    updateStatsUi();
    resetSession();
  }

  function bindActions() {
    els.submit.addEventListener("click", function () {
      resolveCurrentQuestion(false);
    });
    els.skip.addEventListener("click", function () {
      resolveCurrentQuestion(true);
    });
    els.restart.addEventListener("click", function () {
      resetSession();
    });
    document.addEventListener("keydown", function (event) {
      if (state.awaitingTransition || !state.questions.length) {
        return;
      }
      if (event.key >= "1" && event.key <= "4") {
        const index = Number.parseInt(event.key, 10) - 1;
        const inputs = Array.from(els.options.querySelectorAll("input[data-option-index]"));
        const input = inputs.find(function (item) {
          return Number(item.dataset.optionIndex) === index;
        });
        if (input) {
          input.checked = !input.checked || input.type !== "checkbox";
          input.dispatchEvent(new Event("change", { bubbles: true }));
        }
      }
      if (event.key === "Enter") {
        const typedInput = document.getElementById("quiz-practice-typed-input");
        if (typedInput && typedInput === document.activeElement) {
          event.preventDefault();
        }
        resolveCurrentQuestion(false);
      }
    });
  }

  async function loadQuestions() {
    setFeedback("Dang tai bo cau hoi...", "");
    try {
      const response = await fetch(appPath("/games/quiz/practice/questions"), { cache: "no-store" });
      if (!response.ok) {
        throw new Error("Khong tai duoc bo cau hoi");
      }
      const payload = await response.json();
      const questions = Array.isArray(payload.questions) ? payload.questions : [];
      state.questions = shuffle(questions).map(normalizeQuestion);
      if (!state.questions.length) {
        throw new Error("Bo cau hoi dang rong");
      }
      els.summaryStatus.textContent = "Da san sang";
      setFeedback("Bo cau hoi da san sang.", "success");
    } catch (error) {
      state.questions = [];
      els.summaryStatus.textContent = "Loi tai du lieu";
      setFeedback(error.message || "Khong tai duoc bo cau hoi.", "error");
      els.question.textContent = "Khong tai duoc bo cau hoi.";
      els.options.innerHTML = "";
      els.submit.disabled = true;
      els.skip.disabled = true;
    }
  }

  function resetSession() {
    if (!state.questions.length) {
      return;
    }
    stopTimer();
    state.index = 0;
    state.playerScore = 0;
    state.botScore = 0;
    state.historyItems = [];
    state.awaitingTransition = false;
    state.matchCode = newMatchCode();
    state.historyRecorded = false;
    state.playerCorrectAnswers = 0;
    renderSummary("Dang choi", state.botEnabled ? "Chua chot keo voi bot" : "Dang luyen tap");
    renderHistory();
    renderScoreboard();
    showQuestion();
  }

  function showQuestion() {
    const question = state.questions[state.index];
    if (!question) {
      finishSession();
      return;
    }
    state.awaitingTransition = false;
    els.question.textContent = question.question;
    els.options.className = "quiz-practice-options";
    els.options.innerHTML = "";
    if (question.type === "typedAnswer") {
      els.options.classList.add("is-typed");
      const input = document.createElement("input");
      input.id = "quiz-practice-typed-input";
      input.className = "quiz-practice-typed";
      input.type = "text";
      input.placeholder = "Nhap dap an";
      els.options.appendChild(input);
      window.setTimeout(function () { input.focus(); }, 40);
    } else {
      els.options.classList.add("is-grid");
      question.options.forEach(function (option, index) {
        const wrapper = document.createElement("label");
        wrapper.className = "quiz-practice-option";

        const input = document.createElement("input");
        input.type = question.type === "multipleCorrect" ? "checkbox" : "radio";
        input.name = "quiz-practice-option";
        input.dataset.optionIndex = String(index);
        input.value = String(index);

        const body = document.createElement("span");
        body.className = "quiz-practice-option__body";

        const key = document.createElement("span");
        key.className = "quiz-practice-option__key";
        key.textContent = optionKey(index);

        const text = document.createElement("span");
        text.textContent = String(option || "");

        body.appendChild(key);
        body.appendChild(text);
        wrapper.appendChild(input);
        wrapper.appendChild(body);
        els.options.appendChild(wrapper);
      });
    }

    renderScoreboard();
    els.summaryStatus.textContent = state.botEnabled ? "Dang dau bot" : "Dang luyen tap";
    setFeedback("Tra loi truoc khi het gio de giu nhip van dau.", "");
    state.deadlineAt = Date.now() + (state.timerSeconds * 1000);
    startTimer();
  }

  function resolveCurrentQuestion(skipped) {
    if (state.awaitingTransition) {
      return;
    }
    const question = state.questions[state.index];
    if (!question) {
      finishSession();
      return;
    }

    const playerAnswer = readPlayerAnswer(question);
    if (!skipped && playerAnswer.invalid) {
      setFeedback(playerAnswer.message, "error");
      return;
    }

    state.awaitingTransition = true;
    stopTimer();

    const playerResult = skipped
      ? { answered: false, correct: false, delta: 0, label: "Bo qua" }
      : evaluatePlayerAnswer(question, playerAnswer.value);
    state.playerScore += playerResult.delta;
    if (playerResult.correct) {
      state.playerCorrectAnswers += 1;
    }

    let botResult = null;
    if (state.botEnabled) {
      botResult = simulateBotResult(question);
      state.botScore += botResult.delta;
    }

    renderScoreboard();
    const feedbackParts = [
      "Ban: " + playerResult.label,
      "Dap an dung: " + correctAnswerLabel(question)
    ];
    if (botResult) {
      feedbackParts.push("Bot: " + botResult.label);
    }
    const feedbackType = playerResult.correct ? "success" : (skipped ? "" : "error");
    setFeedback(feedbackParts.join(" | "), feedbackType);

    state.historyItems.push(buildHistoryEntry(question, playerResult, botResult));
    renderHistory();

    window.setTimeout(function () {
      state.index += 1;
      showQuestion();
    }, 1300);
  }

  function finishSession() {
    stopTimer();
    state.awaitingTransition = true;
    const player = state.playerScore;
    const bot = state.botScore;
    let resultLabel = "Hoan thanh bo cau hoi";
    if (state.botEnabled) {
      if (player > bot) {
        resultLabel = "Ban thang bot";
      } else if (player < bot) {
        resultLabel = "Bot thang";
      } else {
        resultLabel = "Hoa bot";
      }
    }
    renderSummary("Da ket thuc", resultLabel);
    els.question.textContent = state.botEnabled
      ? "Da chot keo voi bot. Bam Choi lai de vao van moi."
      : "Da ket thuc bo cau hoi local. Bam Choi lai de lam bo moi.";
    els.options.innerHTML = "";
    setFeedback("Tong diem da duoc cap nhat o bang tong ket.", "success");
    applyStatsResult();
    void recordBotHistory();
  }

  function renderScoreboard() {
    els.progress.textContent = state.questions.length
      ? (Math.min(state.index + 1, state.questions.length) + " / " + state.questions.length)
      : "0 / 0";
    els.playerScore.textContent = String(state.playerScore);
    if (els.botScore) {
      els.botScore.textContent = String(state.botScore);
    }
  }

  function renderSummary(status, result) {
    els.summaryStatus.textContent = status;
    els.summaryResult.textContent = result;
  }

  function renderHistory() {
    els.history.innerHTML = "";
    if (!state.historyItems.length) {
      const li = document.createElement("li");
      li.textContent = "Chua co cau hoi nao duoc cham.";
      els.history.appendChild(li);
      return;
    }
    state.historyItems.forEach(function (item) {
      const li = document.createElement("li");
      li.textContent = item;
      els.history.appendChild(li);
    });
  }

  function startTimer() {
    stopTimer();
    updateTimer();
    state.timerHandle = window.setInterval(function () {
      updateTimer();
      if (Date.now() >= state.deadlineAt) {
        resolveCurrentQuestion(true);
      }
    }, 200);
  }

  function stopTimer() {
    if (state.timerHandle) {
      window.clearInterval(state.timerHandle);
      state.timerHandle = null;
    }
  }

  function updateTimer() {
    const remaining = Math.max(0, Math.ceil((state.deadlineAt - Date.now()) / 1000));
    els.timer.textContent = remaining + "s";
  }

  function setFeedback(message, type) {
    els.feedback.textContent = String(message || "");
    els.feedback.classList.remove("error", "success");
    if (type) {
      els.feedback.classList.add(type);
    }
  }

  function readPlayerAnswer(question) {
    if (question.type === "typedAnswer") {
      const input = document.getElementById("quiz-practice-typed-input");
      const value = input ? String(input.value || "").trim() : "";
      return value ? { invalid: false, value: value } : { invalid: true, message: "Hay nhap dap an truoc." };
    }
    const selected = Array.from(els.options.querySelectorAll("input:checked"));
    if (!selected.length) {
      return { invalid: true, message: "Hay chon dap an truoc." };
    }
    if (question.type === "multipleCorrect") {
      return {
        invalid: false,
        value: selected.map(function (item) {
          return Number.parseInt(item.value, 10);
        }).sort(compareNumbers)
      };
    }
    return { invalid: false, value: Number.parseInt(selected[0].value, 10) };
  }

  function evaluatePlayerAnswer(question, value) {
    const correct = isCorrectAnswer(question, value);
    return {
      answered: true,
      correct: correct,
      delta: correct ? 2 : -1,
      label: correct ? "+2 dung" : "-1 sai"
    };
  }

  function simulateBotResult(question) {
    const difficulty = state.difficulty === "hard"
      ? { accuracy: 0.82, hesitation: 0.18 }
      : { accuracy: 0.58, hesitation: 0.32 };
    const roll = Math.random();
    const correct = roll < difficulty.accuracy;
    const noAnswer = roll > (1 - difficulty.hesitation);
    if (noAnswer) {
      return { correct: false, delta: 0, label: "Bo qua" };
    }
    return {
      correct: correct,
      delta: correct ? 2 : -1,
      label: correct ? "+2 dung" : "-1 sai"
    };
  }

  function isCorrectAnswer(question, value) {
    if (question.type === "singleCorrect") {
      return Number(value) === Number(question.correctAnswer);
    }
    if (question.type === "multipleCorrect") {
      const expected = Array.isArray(question.correctAnswers) ? question.correctAnswers.slice().sort(compareNumbers) : [];
      const actual = Array.isArray(value) ? value.slice().sort(compareNumbers) : [];
      return expected.length === actual.length && expected.every(function (item, index) {
        return Number(item) === Number(actual[index]);
      });
    }
    return normalizeText(value) === normalizeText(question.correctText);
  }

  function correctAnswerLabel(question) {
    if (question.type === "singleCorrect") {
      return String(question.options[question.correctAnswer] || "");
    }
    if (question.type === "multipleCorrect") {
      const labels = (question.correctAnswers || []).map(function (index) {
        return question.options[index];
      }).filter(Boolean);
      return labels.join(", ");
    }
    return String(question.correctText || "");
  }

  function buildHistoryEntry(question, playerResult, botResult) {
    const fragments = [
      "Cau " + (state.index + 1) + ": " + playerResult.label
    ];
    if (botResult) {
      fragments.push("Bot " + botResult.label);
    }
    fragments.push(question.type === "typedAnswer" ? "Typed" : "Choice");
    return fragments.join(" | ");
  }

  async function recordBotHistory() {
    if (!state.botEnabled || state.historyRecorded || typeof historyApi.recordBotMatch !== "function") {
      return;
    }
    state.historyRecorded = true;
    const outcome = state.playerScore > state.botScore
      ? "win"
      : (state.playerScore < state.botScore ? "loss" : "draw");
    try {
      await historyApi.recordBotMatch({
        gameCode: "quiz",
        difficulty: state.difficulty,
        outcome: outcome,
        totalMoves: state.questions.length,
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
      bestScore: 0,
      perfectRounds: 0
    };
  }

  function normalizeStats(raw) {
    const source = raw && typeof raw === "object" ? raw : {};
    const totalGames = Math.max(0, Number.parseInt(String(source.totalGames || 0), 10) || 0);
    const wins = Math.max(0, Number.parseInt(String(source.wins || 0), 10) || 0);
    const losses = Math.max(0, Number.parseInt(String(source.losses || 0), 10) || 0);
    const draws = Math.max(0, Number.parseInt(String(source.draws || 0), 10) || 0);
    return {
      totalGames: Math.max(totalGames, wins + losses + draws),
      wins: wins,
      losses: losses,
      draws: draws,
      bestScore: Math.max(0, Number.parseInt(String(source.bestScore || 0), 10) || 0),
      perfectRounds: Math.max(0, Number.parseInt(String(source.perfectRounds || 0), 10) || 0)
    };
  }

  function hasAnyStats(stats) {
    const safe = normalizeStats(stats);
    return safe.totalGames > 0
      || safe.wins > 0
      || safe.losses > 0
      || safe.draws > 0
      || safe.bestScore > 0
      || safe.perfectRounds > 0;
  }

  function mergeStats(primary, secondary) {
    const first = normalizeStats(primary);
    const second = normalizeStats(secondary);
    return {
      totalGames: Math.max(first.totalGames, second.totalGames),
      wins: Math.max(first.wins, second.wins),
      losses: Math.max(first.losses, second.losses),
      draws: Math.max(first.draws, second.draws),
      bestScore: Math.max(first.bestScore, second.bestScore),
      perfectRounds: Math.max(first.perfectRounds, second.perfectRounds)
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
    if (els.statsBest) {
      els.statsBest.textContent = String(stats.bestScore);
    }
    if (els.statsPerfect) {
      els.statsPerfect.textContent = String(stats.perfectRounds);
    }
  }

  function applyStatsResult() {
    const totalQuestions = state.questions.length;
    const clampedScore = Math.max(0, state.playerScore);
    state.stats.totalGames += 1;
    state.stats.bestScore = Math.max(state.stats.bestScore, clampedScore);
    if (totalQuestions > 0 && state.playerCorrectAnswers >= totalQuestions) {
      state.stats.perfectRounds += 1;
    }

    if (state.botEnabled) {
      if (state.playerScore > state.botScore) {
        state.stats.wins += 1;
      } else if (state.playerScore < state.botScore) {
        state.stats.losses += 1;
      } else {
        state.stats.draws += 1;
      }
    }

    writeStats();
    updateStatsUi();
  }

  function normalizeQuestion(question) {
    return {
      type: String(question && question.type || "singleCorrect"),
      question: String(question && question.question || ""),
      options: Array.isArray(question && question.options) ? question.options.map(function (item) { return String(item || ""); }) : [],
      correctAnswer: Number.isInteger(question && question.correctAnswer) ? question.correctAnswer : Number(question && question.correctAnswer),
      correctAnswers: Array.isArray(question && question.correctAnswers) ? question.correctAnswers.map(function (item) { return Number(item); }) : [],
      correctText: String(question && question.correctText || "")
    };
  }

  function optionKey(index) {
    return String.fromCharCode(65 + index);
  }

  function normalizeText(value) {
    return String(value || "").trim().replace(/\s+/g, " ").toLowerCase();
  }

  function newMatchCode() {
    return typeof historyApi.newMatchCode === "function"
      ? historyApi.newMatchCode("quiz-bot-" + state.difficulty)
      : ("BOT-QUIZ-" + Date.now());
  }

  function shuffle(list) {
    const copy = Array.isArray(list) ? list.slice() : [];
    for (let i = copy.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = copy[i];
      copy[i] = copy[j];
      copy[j] = tmp;
    }
    return copy;
  }

  function compareNumbers(a, b) {
    return Number(a) - Number(b);
  }
})();
