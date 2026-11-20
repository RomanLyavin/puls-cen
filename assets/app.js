(function () {
  "use strict";

  const REPO = "RomanLyavin/puls-cen";
  const RAW_BASE = "https://raw.githubusercontent.com/" + REPO + "/main/";
  const REPO_URL = "https://github.com/" + REPO;
  const FORMAT = "PULS-CEN-1";
  const THEME_KEY = "puls-cen-theme";

  const state = {
    bundle: null,
    query: "",
    filter: "ALL",
    sort: "CHANGE",
    expanded: new Set(),
    theme: "light"
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => Array.from(document.querySelectorAll(selector));

  // ---------- Утилиты ----------

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function safeImageUrl(value) {
    const raw = String(value == null ? "" : value).trim();
    if (!raw) return "";
    if (/^https?:\/\/[^\s"'<>]+$/i.test(raw)) return raw;
    return "";
  }

  function safeAvatarUrl(value) {
    const raw = String(value == null ? "" : value).trim();
    if (!raw) return "";
    if (/^data:image\/(png|jpe?g|webp|gif);base64,[A-Za-z0-9+/=]+$/i.test(raw)) return raw;
    return safeImageUrl(raw);
  }

  function formatMoney(value) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return "—";
    return new Intl.NumberFormat("ru-RU", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    }).format(parsed) + " ₽";
  }

  function formatDelta(value) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return "";
    const sign = parsed > 0 ? "+" : parsed < 0 ? "−" : "";
    return sign + formatMoney(Math.abs(parsed)).replace(" ₽", "") + " ₽";
  }

  function formatPercent(value) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return "";
    const sign = parsed > 0 ? "+" : parsed < 0 ? "−" : "";
    return sign + new Intl.NumberFormat("ru-RU", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    }).format(Math.abs(parsed)) + "%";
  }

  function formatDateTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", {
      day: "numeric", month: "long", year: "numeric",
      hour: "2-digit", minute: "2-digit"
    }).format(date);
  }

  function formatShortDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", {
      day: "numeric", month: "short", year: "2-digit"
    }).format(date).replace(".", "");
  }

  function initials(name) {
    const parts = String(name || "").trim().split(/\s+/).filter(Boolean).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join("") || "Я";
  }

  function plural(count, one, few, many) {
    const value = Math.abs(count) % 100;
    const last = value % 10;
    if (value > 10 && value < 20) return many;
    if (last > 1 && last < 5) return few;
    if (last === 1) return one;
    return many;
  }

  function normalizeEmail(raw) {
    return String(raw == null ? "" : raw).trim().toLowerCase();
  }

  function isEmail(value) {
    return /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/.test(value);
  }

  function showToast(message) {
    const toast = $("#toast");
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add("is-visible");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
      toast.classList.remove("is-visible");
    }, 4200);
  }

  // ---------- Криптография ----------

  function base64ToBytes(value) {
    let text = String(value == null ? "" : value).trim()
      .replace(/-/g, "+")
      .replace(/_/g, "/");
    if (!text) throw new Error("Пустое значение Base64");
    text += "=".repeat((4 - (text.length % 4)) % 4);
    const binary = atob(text);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  }

  async function sha256Hex(text) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return Array.from(new Uint8Array(digest))
      .map((byte) => byte.toString(16).padStart(2, "0"))
      .join("");
  }

  async function decryptBundle(envelopeText, password) {
    let envelope;
    try {
      envelope = JSON.parse(envelopeText);
    } catch (error) {
      throw new Error("Файл аккаунта повреждён: это не JSON-конверт.");
    }
    if (!envelope || envelope.format !== FORMAT) {
      throw new Error("Неизвестный формат файла аккаунта.");
    }
    const iterations = Number(envelope.iterations) || 0;
    if (iterations < 10000 || iterations > 2000000) {
      throw new Error("Некорректные параметры шифрования.");
    }
    if (!envelope.salt || !envelope.iv || !envelope.data) {
      throw new Error("Файл аккаунта повреждён: не хватает полей конверта.");
    }

    const passwordKey = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(password),
      "PBKDF2",
      false,
      ["deriveKey"]
    );
    const aesKey = await crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        salt: base64ToBytes(envelope.salt),
        iterations: iterations,
        hash: "SHA-256"
      },
      passwordKey,
      { name: "AES-GCM", length: 256 },
      false,
      ["decrypt"]
    );
    let plain;
    try {
      plain = await crypto.subtle.decrypt(
        { name: "AES-GCM", iv: base64ToBytes(envelope.iv), tagLength: 128 },
        aesKey,
        base64ToBytes(envelope.data)
      );
    } catch (error) {
      throw new Error("Не удалось расшифровать данные. Проверьте пароль.");
    }
    return new TextDecoder().decode(plain);
  }

  // ---------- Чтение аккаунта ----------

  async function fetchBundle(uid) {
    const url = RAW_BASE + "data/u/" + uid + "/bundle.enc?t=" + Date.now();
    let response;
    try {
      response = await fetch(url, { cache: "no-store" });
    } catch (error) {
      throw new Error("Нет связи с GitHub. Проверьте подключение к интернету.");
    }
    if (response.status === 404) {
      throw new Error("Аккаунт не найден. Зарегистрируйтесь в приложении «Пульс цен».");
    }
    if (!response.ok) {
      throw new Error("GitHub ответил кодом " + response.status + ". Попробуйте позже.");
    }
    return response.text();
  }

  async function loadAccount(email, password) {
    if (!isEmail(email)) throw new Error("Введите корректный email.");
    if (!password) throw new Error("Введите пароль.");

    const uid = await sha256Hex(email);
    const envelope = await fetchBundle(uid);
    const text = await decryptBundle(envelope, password);

    let bundle;
    try {
      bundle = JSON.parse(text);
    } catch (error) {
      throw new Error("Расшифрованные данные повреждены.");
    }
    if (!bundle || typeof bundle !== "object") {
      throw new Error("Расшифрованные данные повреждены.");
    }
    const bundleEmail = normalizeEmail(bundle.email);
    if (bundleEmail && bundleEmail !== email) {
      throw new Error("Файл аккаунта относится к другому email.");
    }
    return normalizeBundle(bundle);
  }

  function normalizeBundle(raw) {
    const deleted = new Set(
      (Array.isArray(raw.deleted) ? raw.deleted : [])
        .map((entry) => String(entry && entry.id || ""))
        .filter(Boolean)
    );
    const products = (Array.isArray(raw.products) ? raw.products : [])
      .filter((product) => product && typeof product === "object")
      .filter((product) => !deleted.has(String(product.id || "")))
      .map((product) => {
        const prices = (Array.isArray(product.prices) ? product.prices : [])
          .map((point) => ({
            at: String(point && point.at || ""),
            price: Number(point && point.price)
          }))
          .filter((point) => Number.isFinite(point.price) && point.price > 0)
          .sort((a, b) => String(a.at).localeCompare(String(b.at)));
        return {
          id: String(product.id || product.url || Math.random().toString(36)),
          url: String(product.url || ""),
          title: String(product.title || "Товар"),
          imageUrl: safeImageUrl(product.imageUrl),
          article: product.article ? String(product.article) : "",
          marketplace: String(product.marketplace || "OTHER").toUpperCase(),
          currency: String(product.currency || "RUB"),
          lastCheckedAt: product.lastCheckedAt ? String(product.lastCheckedAt) : "",
          lastError: product.lastError ? String(product.lastError) : "",
          prices: prices
        };
      });

    return {
      email: normalizeEmail(raw.email),
      profile: {
        name: String(raw.profile && raw.profile.name || "Пользователь"),
        avatarDataUrl: safeAvatarUrl(raw.profile && raw.profile.avatarDataUrl)
      },
      products: products,
      updatedAt: raw.updatedAt ? String(raw.updatedAt) : ""
    };
  }

  // ---------- Метрики товара ----------

  function metricsOf(product) {
    const prices = product.prices;
    const current = prices.length ? prices[prices.length - 1].price : null;
    const previous = prices.length > 1 ? prices[prices.length - 2].price : null;
    const change = current != null && previous != null ? current - previous : null;
    const percent = change != null && previous > 0 ? (change / previous) * 100 : null;
    const min = prices.length ? Math.min.apply(null, prices.map((p) => p.price)) : null;
    const max = prices.length ? Math.max.apply(null, prices.map((p) => p.price)) : null;
    let downs = 0;
    let ups = 0;
    for (let index = 1; index < prices.length; index += 1) {
      const diff = prices[index].price - prices[index - 1].price;
      if (diff < -0.004) downs += 1;
      else if (diff > 0.004) ups += 1;
    }
    return { current, previous, change, percent, min, max, downs, ups };
  }

  function totalStats(products) {
    let points = 0;
    let downs = 0;
    let ups = 0;
    products.forEach((product) => {
      points += product.prices.length;
      const metrics = metricsOf(product);
      downs += metrics.downs;
      ups += metrics.ups;
    });
    return { points, downs, ups };
  }

  // ---------- График ----------

  function chartSvg(prices) {
    if (prices.length < 2) {
      return '<div class="empty" style="padding:18px 10px">' +
        "Недостаточно точек для графика.</div>";
    }
    const width = 600;
    const height = 140;
    const padTop = 10;
    const padBottom = 10;
    const values = prices.map((point) => point.price);
    const min = Math.min.apply(null, values);
    const max = Math.max.apply(null, values);
    const span = Math.max(max - min, max * 0.02, 0.01);
    const left = 4;
    const right = width - 4;
    const usable = height - padTop - padBottom;
    const stepX = (right - left) / (prices.length - 1);
    const yFor = (price) => padTop + (1 - (price - min) / span) * usable * 0.92 + usable * 0.04;

    const points = prices.map((point, index) =>
      (left + index * stepX).toFixed(2) + "," + yFor(point.price).toFixed(2)
    ).join(" ");

    const first = prices[0];
    const last = prices[prices.length - 1];
    const color = last.price < first.price ? "#1f7a34" : last.price > first.price ? "#d32f2f" : "#8a6f6c";

    const grid = [0, 1, 2, 3].map((index) => {
      const y = padTop + (usable / 3) * index;
      return '<line x1="0" y1="' + y.toFixed(2) + '" x2="' + width +
        '" y2="' + y.toFixed(2) + '" stroke="currentColor" stroke-opacity="0.12" stroke-width="1"></line>';
    }).join("");

    const area = "M" + left + "," + yFor(min).toFixed(2) + " L" + points.replace(/ /g, " L") +
      " L" + right + "," + (height - padBottom) + " L" + left + "," + (height - padBottom) + " Z";

    return '<svg class="chart" viewBox="0 0 ' + width + " " + height + '" preserveAspectRatio="none" role="img" ' +
      'aria-label="График изменения цены">' +
      grid +
      '<path d="' + area + '" fill="' + color + '" fill-opacity="0.12"></path>' +
      '<polyline points="' + points + '" fill="none" stroke="' + color +
      '" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" ' +
      'vector-effect="non-scaling-stroke"></polyline>' +
      '<line x1="' + (left + (prices.length - 1) * stepX).toFixed(2) + '" y1="' + padTop +
      '" x2="' + (left + (prices.length - 1) * stepX).toFixed(2) + '" y2="' + (height - padBottom) +
      '" stroke="' + color + '" stroke-opacity="0.35" stroke-width="1.5" ' +
      'vector-effect="non-scaling-stroke"></line>' +
      "</svg>";
  }

  // ---------- Разметка ----------

  function marketplaceTag(product) {
    if (product.marketplace === "WB") return '<span class="tag tag-wb">Wildberries</span>';
    if (product.marketplace === "OZON") return '<span class="tag tag-ozon">Ozon</span>';
    return '<span class="tag">Магазин</span>';
  }

  function productCard(product) {
    const metrics = metricsOf(product);
    const changeClass = metrics.change == null || Math.abs(metrics.change) < 0.005
      ? "same"
      : metrics.change < 0 ? "down" : "up";
    const changeText = metrics.change == null
      ? "нет истории"
      : Math.abs(metrics.change) < 0.005
        ? "без изменений"
        : formatDelta(metrics.change) + " · " + formatPercent(metrics.percent);

    const expanded = state.expanded.has(product.id);
    const history = product.prices.slice().reverse();
    const visible = expanded ? history : history.slice(0, 5);
    const historyRows = visible.map((point, index) => {
      const nextOlder = visible[index + 1];
      const diff = nextOlder ? point.price - nextOlder.price : null;
      const diffText = diff == null || Math.abs(diff) < 0.005
        ? ""
        : '<span class="h-price" style="color:' + (diff < 0 ? "var(--green)" : "var(--red)") + '">' +
          escapeHtml(formatDelta(diff)) + "</span>";
      return '<div class="history-row">' +
        '<span class="h-price">' + escapeHtml(formatMoney(point.price)) + "</span>" +
        '<span class="h-date">' + escapeHtml(formatShortDate(point.at)) + "</span>" +
        (diffText || "<span></span>") +
        "</div>";
    }).join("");

    const more = history.length > 5
      ? '<button class="link-button" type="button" data-history="' + escapeHtml(product.id) + '">' +
        (expanded ? "Свернуть историю" : "Показать всю историю (" + history.length + ")") +
        "</button>"
      : "";

    const image = product.imageUrl
      ? '<img src="' + escapeHtml(product.imageUrl) + '" alt="" loading="lazy" ' +
        'referrerpolicy="no-referrer" onerror="this.remove()">'
      : '<span aria-hidden="true">📦</span>';

    const article = product.article
      ? '<span class="tag">арт. ' + escapeHtml(product.article) + "</span>"
      : "";

    const source = /^https?:\/\//i.test(product.url)
      ? '<a class="link-button" href="' + escapeHtml(product.url) +
        '" target="_blank" rel="noopener nofollow">Открыть товар</a>'
      : "";

    const error = product.lastError
      ? '<div class="product-error">' + escapeHtml(product.lastError) + "</div>"
      : "";

    return '<article class="product-card card" data-id="' + escapeHtml(product.id) + '">' +
      '<div class="product-media">' + image + "</div>" +
      '<div class="product-body">' +
      '<div class="product-top"><h3 class="product-title">' + escapeHtml(product.title) + "</h3>" +
      '<span class="change ' + changeClass + '">' + escapeHtml(changeText) + "</span></div>" +
      '<div class="product-meta">' + marketplaceTag(product) + article +
      (product.lastCheckedAt
        ? '<span>проверено ' + escapeHtml(formatDateTime(product.lastCheckedAt)) + "</span>"
        : "") +
      "</div>" +
      error +
      '<div class="price-row"><span class="price">' + escapeHtml(formatMoney(metrics.current)) + "</span>" +
      (metrics.previous != null && Math.abs(metrics.previous - metrics.current) >= 0.005
        ? '<span class="price-old">было ' + escapeHtml(formatMoney(metrics.previous)) + "</span>"
        : "") +
      "</div>" +
      '<div class="range-line">' +
      "<span>минимум <b>" + escapeHtml(formatMoney(metrics.min)) + "</b></span>" +
      "<span>максимум <b>" + escapeHtml(formatMoney(metrics.max)) + "</b></span>" +
      "<span>точек <b>" + product.prices.length + "</b></span>" +
      "<span>снижений <b>" + metrics.downs + "</b></span>" +
      "<span>подорожаний <b>" + metrics.ups + "</b></span>" +
      "</div>" +
      '<div class="chart-wrap">' + chartSvg(product.prices) +
      '<div class="chart-axis"><span>' +
      escapeHtml(product.prices.length ? formatShortDate(product.prices[0].at) : "—") +
      "</span><span>" +
      escapeHtml(product.prices.length ? formatShortDate(product.prices[product.prices.length - 1].at) : "—") +
      "</span></div></div>" +
      (history.length ? '<div class="history">' + historyRows + "</div>" : "") +
      '<div class="product-foot">' + source + more + "</div>" +
      "</div></article>";
  }

  function emptyState(title, message) {
    return '<div class="empty"><span class="empty-emoji" aria-hidden="true">🛒</span>' +
      "<h3>" + escapeHtml(title) + "</h3><p>" + escapeHtml(message) + "</p></div>";
  }

  function filteredProducts() {
    const bundle = state.bundle;
    if (!bundle) return [];
    const query = state.query.trim().toLowerCase();
    const list = bundle.products.filter((product) => {
      if (query) {
        const haystack = (product.title + " " + product.article + " " + product.url).toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      if (state.filter === "ALL") return true;
      const change = metricsOf(product).change;
      if (state.filter === "DOWN") return change != null && change < -0.004;
      if (state.filter === "UP") return change != null && change > 0.004;
      return change == null || Math.abs(change) <= 0.004;
    });

    const sorted = list.slice();
    if (state.sort === "TITLE") {
      sorted.sort((a, b) => a.title.localeCompare(b.title, "ru"));
    } else if (state.sort === "PRICE") {
      sorted.sort((a, b) => (metricsOf(b).current || 0) - (metricsOf(a).current || 0));
    } else if (state.sort === "CHECKED") {
      sorted.sort((a, b) => String(b.lastCheckedAt).localeCompare(String(a.lastCheckedAt)));
    } else {
      sorted.sort((a, b) => {
        const aChange = Math.abs(metricsOf(a).change || 0);
        const bChange = Math.abs(metricsOf(b).change || 0);
        if (aChange !== bChange) return bChange - aChange;
        return String(b.lastCheckedAt).localeCompare(String(a.lastCheckedAt));
      });
    }
    return sorted;
  }

  function renderProducts() {
    const panel = $("#products-panel");
    if (!panel) return;
    const bundle = state.bundle;
    if (!bundle) return;
    if (!bundle.products.length) {
      panel.innerHTML = emptyState(
        "Пока нет товаров",
        "Добавьте первую ссылку в приложении «Пульс цен» — товар появится здесь после синхронизации."
      );
      return;
    }
    const products = filteredProducts();
    if (!products.length) {
      panel.innerHTML = emptyState("Ничего не найдено", "Измените запрос или фильтр.");
      return;
    }
    panel.innerHTML = products.map(productCard).join("");
  }

  function renderStats() {
    const bundle = state.bundle;
    const products = bundle ? bundle.products : [];
    const totals = totalStats(products);
    $("#stat-products").textContent = String(products.length);
    $("#stat-points").textContent = String(totals.points);
    $("#stat-down").textContent = String(totals.downs);
    $("#stat-up").textContent = String(totals.ups);
  }

  function renderProfile() {
    const bundle = state.bundle;
    const profile = bundle.profile;
    $("#profile-name").textContent = profile.name || "Пользователь";
    $("#profile-email").textContent = bundle.email || "";
    const avatar = $("#profile-avatar");
    if (profile.avatarDataUrl) {
      avatar.innerHTML = '<img alt="" src="' + escapeHtml(profile.avatarDataUrl) + '">';
    } else {
      avatar.textContent = initials(profile.name);
    }
    const count = bundle.products.length;
    const updated = bundle.updatedAt ? formatDateTime(bundle.updatedAt) : "—";
    $("#updated-note").textContent = count + " " + plural(count, "товар", "товара", "товаров") +
      " · обновлено " + updated;
  }

  function renderCabinet() {
    renderProfile();
    renderStats();
    renderProducts();
  }

  function showCabinet(bundle) {
    state.bundle = bundle;
    state.expanded.clear();
    state.query = "";
    state.filter = "ALL";
    state.sort = "CHANGE";
    const search = $("#product-search");
    if (search) search.value = "";
    const filter = $("#product-filter");
    if (filter) filter.value = "ALL";
    const sort = $("#product-sort");
    if (sort) sort.value = "CHANGE";
    $("#auth-view").hidden = true;
    $("#cabinet-view").hidden = false;
    renderCabinet();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function showAuth() {
    state.bundle = null;
    $("#cabinet-view").hidden = true;
    $("#auth-view").hidden = false;
    setBusy(false);
    const password = $("#login-password");
    if (password) password.value = "";
    hideError();
  }

  function setBusy(busy) {
    const button = $("#login-button");
    if (!button) return;
    button.disabled = busy;
    button.textContent = busy ? "Расшифровываем…" : "Войти";
  }

  function showError(message) {
    const box = $("#auth-error");
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
  }

  function hideError() {
    const box = $("#auth-error");
    if (!box) return;
    box.textContent = "";
    box.hidden = true;
  }

  // ---------- Тема ----------

  function applyTheme(theme, persist) {
    state.theme = theme === "dark" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", state.theme);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", state.theme === "dark" ? "#161010" : "#d32f2f");
    if (persist) {
      try {
        localStorage.setItem(THEME_KEY, state.theme);
      } catch (error) {
        // localStorage может быть недоступен — тема просто не запомнится.
      }
    }
  }

  function initTheme() {
    let stored = null;
    try {
      stored = localStorage.getItem(THEME_KEY);
    } catch (error) {
      stored = null;
    }
    if (stored === "dark" || stored === "light") {
      applyTheme(stored, false);
      return;
    }
    const prefersDark = window.matchMedia &&
      window.matchMedia("(prefers-color-scheme: dark)").matches;
    applyTheme(prefersDark ? "dark" : "light", false);
  }

  // ---------- События ----------

  function bindEvents() {
    const form = $("#login-form");
    if (form) {
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        hideError();
        const email = normalizeEmail($("#login-email").value);
        const password = $("#login-password").value;
        setBusy(true);
        try {
          const bundle = await loadAccount(email, password);
          showCabinet(bundle);
          showToast("Данные расшифрованы в браузере");
        } catch (error) {
          showError(error && error.message ? error.message : "Не удалось войти.");
        } finally {
          setBusy(false);
        }
      });
    }

    const toggle = $("#toggle-password");
    if (toggle) {
      toggle.addEventListener("click", () => {
        const input = $("#login-password");
        if (!input) return;
        const show = input.type === "password";
        input.type = show ? "text" : "password";
        toggle.setAttribute("aria-label", show ? "Скрыть пароль" : "Показать пароль");
      });
    }

    const logout = $("#logout-button");
    if (logout) {
      logout.addEventListener("click", () => {
        showAuth();
        showToast("Вы вышли. Данные удалены из памяти браузера.");
      });
    }

    const search = $("#product-search");
    if (search) {
      search.addEventListener("input", () => {
        state.query = search.value || "";
        renderProducts();
      });
    }

    const filter = $("#product-filter");
    if (filter) {
      filter.addEventListener("change", () => {
        state.filter = filter.value || "ALL";
        renderProducts();
      });
    }

    const sort = $("#product-sort");
    if (sort) {
      sort.addEventListener("change", () => {
        state.sort = sort.value || "CHANGE";
        renderProducts();
      });
    }

    const panel = $("#products-panel");
    if (panel) {
      panel.addEventListener("click", (event) => {
        const button = event.target.closest("[data-history]");
        if (!button) return;
        const id = button.getAttribute("data-history");
        if (!id) return;
        if (state.expanded.has(id)) state.expanded.delete(id);
        else state.expanded.add(id);
        renderProducts();
      });
    }

    const themeToggle = $("#theme-toggle");
    if (themeToggle) {
      themeToggle.addEventListener("click", () => {
        applyTheme(state.theme === "dark" ? "light" : "dark", true);
      });
    }

    const emailInput = $("#login-email");
    if (emailInput) {
      emailInput.addEventListener("input", hideError);
    }
    const passwordInput = $("#login-password");
    if (passwordInput) {
      passwordInput.addEventListener("input", hideError);
    }
  }

  function initLinks() {
    const year = $("#footer-year");
    if (year) year.textContent = String(new Date().getFullYear());
    $$("[data-repo]").forEach((link) => { link.href = REPO_URL; });
  }

  function init() {
    initTheme();
    bindEvents();
    initLinks();
    if (!window.crypto || !window.crypto.subtle) {
      showError(
        "Браузер не поддерживает WebCrypto. Откройте сайт по HTTPS в современном браузере."
      );
      const button = $("#login-button");
      if (button) button.disabled = true;
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
