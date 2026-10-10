'use strict';
// Guess Market web client (exercise 4). Plain JavaScript, no framework and no build step: the page
// talks to the very same endpoints the JavaFX client uses, through run.bat's small Node server,
// and pulls what it shows every half second as the JavaFX client does.

const API = '/guess-market';
const POLL_MS = 500;

// ---------- talking to the server ----------

class ServerError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function api(path, params, method) {
  const query = new URLSearchParams(params || {});
  const options = { method, credentials: 'same-origin', headers: {} };
  let url = API + path;
  if (method === 'GET') {
    if (params) url += '?' + query;
  } else {
    options.body = query;
    options.headers['content-type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
  }
  let response;
  try {
    response = await fetch(url, options);
  } catch {
    throw new ServerError(0, 'The page cannot reach the program that serves it. Is run.bat still running?');
  }
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // not JSON: fall through to the status
  }
  if (!response.ok) {
    throw new ServerError(response.status,
      (json && json.error) || `The server refused the request (HTTP ${response.status}).`);
  }
  return json;
}

const get = (path, params) => api(path, params, 'GET');
const post = (path, params) => api(path, params, 'POST');

// ---------- formatting ----------

const money = n => Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const signed = n => (n >= 0 ? '+' : '') + money(n);
const price = n => (n === null || n === undefined ? '—' : Number(n).toFixed(2));
const qty = n => Number(n).toLocaleString('en-US');
const percent = f => (f * 100).toFixed(1) + '%';
const time = ms => new Date(ms).toLocaleTimeString('en-GB');
const STATUS = { NOT_STARTED: 'Not started', ACTIVE: 'Active', CLOSED: 'Closed' };
const METHOD = { LMSR: 'LMSR', ORDER_BOOK: 'Order book' };
const COMMISSION = { ON_PURCHASE: 'On purchase', ON_CLOSE: 'On close' };
const ENTRY = {
  DEPOSIT: 'Loaded funds',
  SUBSIDY_PAID: 'Subsidy for starting an LMSR event',
  INITIAL_STOCK: 'Initial stock for starting an order book',
  SHARES_BOUGHT: 'Bought shares',
  SHARES_SOLD: 'Sold shares',
  COMMISSION_PAID: 'Commission paid',
  COMMISSION_RECEIVED: 'Commission received',
  WINNINGS: 'Winning shares paid out',
  SUBSIDY_RETURNED: 'Unused subsidy returned',
};

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const $ = id => document.getElementById(id);

// ---------- dialogs ----------

function message(title, text, isError) {
  $('message-title').textContent = title;
  $('message-title').className = isError ? 'error' : '';
  $('message-text').textContent = text;
  const dialog = $('message');
  if (dialog.open) dialog.close();
  dialog.showModal();
}

/** Sends one request the user asked for; a refusal is shown, and the screens refresh at once. */
async function act(title, request, onSuccess) {
  try {
    const result = await request();
    if (onSuccess) onSuccess(result);
  } catch (e) {
    message(title, e.message, true);
  } finally {
    pollSoon();
  }
}

// ---------- tables ----------

/**
 * Draws rows into a table, only when something in them changed, so the half second refresh
 * neither flickers nor loses the user's place. columns: [{ title, value(row), num }].
 */
function table(element, columns, rows, { key, selected, onPick, empty } = {}) {
  const signature = JSON.stringify([rows, selected]);
  if (element._signature === signature) return;
  element._signature = signature;
  element._rows = rows;
  element._onPick = onPick;

  const head = '<thead><tr>' + columns.map(c => `<th class="${c.num ? 'num' : ''}">${esc(c.title)}</th>`).join('') + '</tr></thead>';
  const body = rows.length === 0
    ? `<tr><td class="empty" colspan="${columns.length}">${esc(empty || 'Nothing here yet.')}</td></tr>`
    : rows.map((row, i) => {
      const classes = [onPick ? 'pick' : '', key && selected !== undefined && key(row) === selected ? 'selected' : ''];
      return `<tr data-index="${i}" class="${classes.join(' ')}">`
        + columns.map(c => `<td class="${c.num ? 'num' : ''}">${esc(c.value(row))}</td>`).join('') + '</tr>';
    }).join('');
  element.innerHTML = head + '<tbody>' + body + '</tbody>';

  if (!element._wired) {
    element._wired = true;
    element.addEventListener('click', event => {
      const tr = event.target.closest('tr[data-index]');
      if (tr && element._onPick) element._onPick(element._rows[Number(tr.dataset.index)]);
    });
  }
}

// ---------- one event in full ----------

const panes = [];

/**
 * One event and the controls to act on it, used by both screens. It fetches its event every half
 * second and redraws only on change; the trading controls are rebuilt only when the event moves
 * to another stage, so whatever is being typed into them survives the refreshes.
 */
class DetailPane {
  constructor(root) {
    this.root = root;
    this.info = document.createElement('div');
    this.actions = document.createElement('div');
    this.actions.className = 'actions';
    root.append(this.info, this.actions);
    this.requote = () => {};
    this.showNothing();
    panes.push(this);
  }

  showNothing() {
    this.id = null;
    this.signature = null;
    this.actionsKey = null;
    this.requote = () => {};
    this.info.innerHTML = '<p class="hint">Select an event to see its details.</p>';
    this.actions.innerHTML = '';
  }

  show(id) {
    if (this.id === id) return;
    this.showNothing();
    this.id = id;
    this.info.innerHTML = '<p class="hint">Loading the event...</p>';
    pollSoon();
  }

  async fetch() {
    const id = this.id;
    if (id === null) return;
    const state = await get('/event', { event: id });
    if (id === this.id) this.update(state);
  }

  update(state) {
    const signature = JSON.stringify(state);
    if (signature === this.signature) return;
    this.signature = signature;
    this.state = state;
    this.info.innerHTML = renderEvent(state);
    const key = state.summary.id + ':' + state.summary.status;
    if (key !== this.actionsKey) {
      this.actionsKey = key;
      this.buildActions(state);
    } else {
      this.requote();
    }
  }

  buildActions(state) {
    const s = state.summary;
    this.requote = () => {};
    let html = `<hr><h3>Acting as ${esc(me)}</h3>`;
    if (s.marketMakerName === me) {
      html += `<div class="row">
        <button data-do="open" ${s.status !== 'NOT_STARTED' ? 'disabled' : ''}>Start event</button>
        <select data-in="winner" ${s.status !== 'ACTIVE' ? 'disabled' : ''}>
          <option value="">Winning answer</option>
          ${s.outcomeNames.map((name, i) => `<option value="${i + 1}">${esc(name)}</option>`).join('')}
        </select>
        <button data-do="close" ${s.status !== 'ACTIVE' ? 'disabled' : ''}>Close event</button>
      </div>`;
    }
    const answers = s.outcomeNames.map((name, i) => `<option value="${i + 1}">${esc(name)}</option>`).join('');
    if (s.status !== 'ACTIVE') {
      html += `<p class="hint">${s.status === 'NOT_STARTED'
        ? 'The event has not been started by its market maker, so it cannot be traded yet.'
        : 'The event is closed.'}</p>`;
    } else if (s.method === 'LMSR') {
      html += `<div class="row">
        <label>Answer <select data-in="answer">${answers}</select></label>
        <label>Shares <input data-in="quantity" type="number" min="1" step="1" value="10"></label>
        <button data-do="buy">Buy</button>
        <span data-out="quote" class="muted"></span>
      </div>`;
    } else {
      const d = state.orderBook.basePrice;
      html += `<div class="row">
        <label>Answer <select data-in="answer">${answers}</select></label>
        <select data-in="side"><option>BUY</option><option>SELL</option></select>
        <label>Shares <input data-in="quantity" type="number" min="1" step="1" value="10"></label>
        <label>Price <input data-in="price" type="number" min="0.01" max="${d - 0.01}" step="0.01" value="0.50"></label>
        <button data-do="order">Submit order</button>
        <span class="muted">between 0.01 and ${(d - 0.01).toFixed(2)}</span>
      </div>`;
    }
    this.actions.innerHTML = html;
    const field = name => this.actions.querySelector(`[data-in="${name}"]`);
    const id = s.id;

    const open = this.actions.querySelector('[data-do="open"]');
    if (open) open.onclick = () => act('Start event', () => post('/open', { event: id }));
    const close = this.actions.querySelector('[data-do="close"]');
    if (close) {
      close.onclick = () => {
        if (!field('winner').value) {
          message('Close event', 'Choose the winning answer first.', true);
          return;
        }
        act('Close event', () => post('/close', { event: id, outcome: field('winner').value }),
          r => message('Event closed', closeSummary(r)));
      };
    }

    const buy = this.actions.querySelector('[data-do="buy"]');
    if (buy) {
      const quote = this.actions.querySelector('[data-out="quote"]');
      const commission = s.commissionType === 'ON_PURCHASE' ? s.commissionPercent : 0;
      this.requote = async () => {
        const quantity = field('quantity').value;
        try {
          const cost = await get('/quote', { event: id, outcome: field('answer').value, quantity });
          quote.textContent = commission
            ? `Costs ${money(cost)} plus ${money(cost * commission / 100)} commission`
            : `Costs ${money(cost)}`;
        } catch {
          quote.textContent = '';
        }
      };
      field('answer').onchange = this.requote;
      field('quantity').oninput = this.requote;
      this.requote();
      buy.onclick = () => act('Buy shares',
        () => post('/buy', { event: id, outcome: field('answer').value, quantity: field('quantity').value }),
        r => message('Shares bought', `Bought ${qty(r.quantity)} of ${r.outcomeName} for ${money(r.sharesCost)}`
          + (r.commissionCharged ? ` plus ${money(r.commissionPaid)} commission` : '')
          + `.\nTotal paid: ${money(r.totalPaid)}.`));
    }

    const order = this.actions.querySelector('[data-do="order"]');
    if (order) {
      order.onclick = () => act('Submit order',
        () => post('/order', {
          event: id, outcome: field('answer').value, side: field('side').value,
          quantity: field('quantity').value, price: field('price').value,
        }),
        r => message('Order submitted', orderSummary(r)));
    }
  }
}

function renderEvent(state) {
  const s = state.summary;
  let html = `<div class="title">${esc(s.name)}</div><div>${esc(s.description)}</div>
    <dl class="facts">
      <dt>Status</dt><dd>${STATUS[s.status]}</dd>
      <dt>Traded by</dt><dd>${METHOD[s.method]}</dd>
      <dt>Commission</dt><dd>${s.commissionPercent}% ${COMMISSION[s.commissionType]}</dd>
      <dt>Market maker</dt><dd>${esc(s.marketMakerName)}</dd>
      <dt>Event account</dt><dd>${money(s.accountBalance)}</dd>
      ${state.winningOutcomeName ? `<dt>Winning answer</dt><dd>${esc(state.winningOutcomeName)}</dd>` : ''}
    </dl><hr>`;
  html += s.method === 'LMSR' ? renderLmsr(state.lmsr) : renderOrderBook(state.orderBook);
  html += '<hr><h3>Participants</h3>' + staticTable(
    [{ title: 'User', value: p => p.userName },
      ...s.outcomeNames.flatMap((answer, i) => [
        { title: answer + ' held', num: true, value: p => qty(p.holdings[i]?.quantity ?? 0) },
        { title: answer + ' paid', num: true, value: p => money(p.holdings[i]?.amountPaid ?? 0) }]),
      { title: 'Resting orders', num: true, value: p => qty(p.restingOrders) }],
    state.participants, 'Nobody has taken part yet.');
  return html;
}

function renderLmsr(d) {
  return '<h3>Answers</h3>' + staticTable([
    { title: '#', value: o => o.displayNumber },
    { title: 'Answer', value: o => o.name },
    { title: 'Price', num: true, value: o => price(o.price) },
    { title: 'Chance', num: true, value: o => percent(o.price) },
    { title: 'Shares bought', num: true, value: o => qty(o.sharesBought) }], d.outcomes)
    + `<dl class="facts">
      <dt>Liquidity b</dt><dd>${d.liquidity}</dd>
      <dt>Opening subsidy</dt><dd>${money(d.openingSubsidy)}</dd>
      <dt>Maker net result</dt><dd>${signed(d.marketMakerNetResult)}</dd>
      <dt>Commission taken</dt><dd>${money(d.commissionCollected)}</dd>
    </dl><h3>Trade history, newest first</h3>` + staticTable([
    { title: 'User', value: t => t.userName },
    { title: 'Answer', value: t => t.outcomeName },
    { title: 'Shares', num: true, value: t => qty(t.quantity) },
    { title: 'Price paid', num: true, value: t => money(t.sharesCost) },
    { title: 'Commission', num: true, value: t => money(t.commissionPaid) },
    { title: 'Total', num: true, value: t => money(t.totalPaid) }], d.tradeHistory, 'Nothing has been traded yet.');
}

function renderOrderBook(d) {
  const orders = (list, empty) => staticTable([
    { title: 'User', value: o => o.userName },
    { title: 'Shares', num: true, value: o => qty(o.quantity) },
    { title: 'Price', num: true, value: o => price(o.price) }], list, empty);
  return `<dl class="facts">
      <dt>Base value d</dt><dd>${money(d.basePrice)}</dd>
      <dt>Minting</dt><dd>${d.mintAllowed ? 'Allowed' : 'Not allowed'}</dd>
      <dt>Initial investment</dt><dd>${money(d.initialInvestment)}</dd>
      <dt>Commission taken</dt><dd>${money(d.commissionCollected)}</dd>
    </dl><div class="books">` + d.books.map(b => `<div class="book">
      <h3>${esc(b.outcomeName)}</h3>
      <div class="stats">
        <span class="stat">Last ${price(b.lastTradePrice)}</span><span class="stat">Bid ${price(b.bestBid)}</span>
        <span class="stat">Ask ${price(b.bestAsk)}</span><span class="stat">Mid ${price(b.mid)}</span>
        <span class="stat">Spread ${price(b.spread)}</span><span class="stat">Outstanding ${qty(b.sharesOutstanding)}</span>
      </div>
      <div>Bids (buy), best first</div>${orders(b.bids, 'No buyers.')}
      <div>Asks (sell), best first</div>${orders(b.asks, 'No sellers.')}
    </div>`).join('') + '</div>';
}

function staticTable(columns, rows, empty) {
  const head = columns.map(c => `<th class="${c.num ? 'num' : ''}">${esc(c.title)}</th>`).join('');
  const body = rows.length === 0
    ? `<tr><td class="empty" colspan="${columns.length}">${esc(empty || '')}</td></tr>`
    : rows.map(r => '<tr>' + columns.map(c => `<td class="${c.num ? 'num' : ''}">${esc(c.value(r))}</td>`).join('') + '</tr>').join('');
  return `<div class="table-box"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

function closeSummary(r) {
  let text = `${r.winningOutcomeName} wins.\n${qty(r.winningShares)} winning share(s) paid ${money(r.netPaidToWinners)} in total.`
    + `\nCommission taken: ${money(r.commissionCharged)}.`;
  if (r.subsidyReturned > 0) text += `\nSubsidy returned to the market maker: ${money(r.subsidyReturned)}.`;
  return text;
}

function orderSummary(r) {
  if (r.quantityFilled === 0) return `Nothing matched, so all ${qty(r.quantityResting)} share(s) are resting in the book.`;
  let text = `Filled ${qty(r.quantityFilled)} of ${qty(r.quantitySubmitted)} share(s).\n`;
  for (const f of r.fills) {
    text += f.sellerName === null || f.sellerName === undefined
      ? `\n  • minted ${qty(f.quantity)} ${f.outcomeName} for ${f.buyerName} at ${price(f.price)}`
      : `\n  • ${qty(f.quantity)} ${f.outcomeName} from ${f.sellerName} to ${f.buyerName} at ${price(f.price)}`;
  }
  if (r.quantityResting > 0) text += `\n\n${qty(r.quantityResting)} share(s) left resting in the book.`;
  if (r.cashSpent > 0) text += `\nPaid ${money(r.cashSpent)}` + (r.commissionPaid > 0 ? ` plus ${money(r.commissionPaid)} commission` : '') + '.';
  if (r.cashReceived > 0) text += `\nReceived ${money(r.cashReceived)}.`;
  return text;
}

// ---------- the events screen ----------

const filters = { method: new Set(), status: new Set(), commissionType: new Set() };
const FILTER_VALUES = { method: METHOD, status: STATUS, commissionType: COMMISSION };
let allEvents = [];
let selectedEvent = null;
let eventsPane;

function buildFilters() {
  // Nothing pressed in a row means that row lets everything through: that is "all of them".
  for (const box of document.querySelectorAll('.filter')) {
    const name = box.dataset.filter;
    for (const [value, label] of Object.entries(FILTER_VALUES[name])) {
      const button = document.createElement('button');
      button.textContent = label;
      button.onclick = () => {
        if (filters[name].has(value)) filters[name].delete(value); else filters[name].add(value);
        button.classList.toggle('on', filters[name].has(value));
        drawEvents();
      };
      box.append(button);
    }
  }
}

function drawEvents() {
  const shown = allEvents.filter(e => Object.keys(filters).every(f => filters[f].size === 0 || filters[f].has(e[f])));
  if (!shown.some(e => e.id === selectedEvent)) {
    selectedEvent = null;
    eventsPane.showNothing();
  }
  table($('events-table'), [
    { title: 'Event', value: e => e.name },
    { title: 'Status', value: e => STATUS[e.status] },
    { title: 'Method', value: e => METHOD[e.method] },
    { title: 'Commission', value: e => `${e.commissionPercent}% ${COMMISSION[e.commissionType]}` },
    { title: 'Account', num: true, value: e => money(e.accountBalance) },
    { title: 'Market maker', value: e => e.marketMakerName }],
  shown, {
    key: e => e.id,
    selected: selectedEvent,
    empty: 'No events yet. They are uploaded from the JavaFX client.',
    onPick: e => {
      selectedEvent = e.id;
      eventsPane.show(e.id);
      drawEvents();
    },
  });
}

// ---------- the account screen ----------

let selectedParticipation = null;
let accountPane;
let lastMe = null;

function drawUsers(users) {
  table($('users-table'), [
    { title: 'User', value: u => u.name },
    { title: 'Balance', num: true, value: u => money(u.balance) },
    { title: 'Market maker', value: u => (u.marketMaker ? 'Yes' : 'No') }], users, { empty: 'Nobody else yet.' });
}

function drawMe(details) {
  lastMe = details;
  $('balance').textContent = `Balance ${money(details.balance)}` + (details.blocked ? '  (blocked)' : '');
  $('account-balance').textContent = `Balance ${money(details.balance)}`;
  $('blocked').textContent = details.blocked ? 'BLOCKED — your balance went below zero. Load funds to trade again.' : '';

  table($('ledger-table'), [
    { title: '#', num: true, value: e => e.number },
    { title: 'Time', value: e => time(e.timeMillis) },
    { title: 'What', value: e => ENTRY[e.kind] || e.kind },
    { title: 'Event', value: e => e.eventName || '' },
    { title: 'Amount', num: true, value: e => signed(e.amount) },
    { title: 'Balance after', num: true, value: e => money(e.balanceAfter) }],
  [...details.ledger].reverse(), { empty: 'No money has moved yet. Load funds to start.' });

  if (!details.participations.some(p => p.eventId === selectedParticipation)) {
    selectedParticipation = null;
    accountPane.showNothing();
  }
  table($('participations-table'), [
    { title: 'Event', value: p => p.eventName },
    { title: 'Method', value: p => METHOD[p.method] },
    { title: 'Status', value: p => STATUS[p.status] },
    { title: 'Role', value: p => (p.marketMaker ? 'Market maker' : 'Trader') },
    { title: 'Commission paid', num: true, value: p => money(p.commissionPaid) },
    { title: 'Profit / loss', num: true, value: p => (p.status === 'CLOSED' ? signed(p.profitOrLoss) : '—') }],
  details.participations, {
    key: p => p.eventId,
    selected: selectedParticipation,
    empty: 'You are not running or trading in any event yet.',
    onPick: p => {
      selectedParticipation = p.eventId;
      accountPane.show(p.eventId);
      drawMe(lastMe);
    },
  });
  drawInvolvement(details.participations.find(p => p.eventId === selectedParticipation));
}

function drawInvolvement(p) {
  const box = $('involvement');
  const signature = JSON.stringify(p ?? null);
  if (box._signature === signature) return;
  box._signature = signature;
  if (!p) {
    box.innerHTML = '';
    return;
  }
  let html;
  if (p.method === 'LMSR') {
    html = '<h3>Your trades on this event, newest first</h3>' + staticTable([
      { title: 'Answer', value: t => t.outcomeName },
      { title: 'Shares', num: true, value: t => qty(t.quantity) },
      { title: 'Price paid', num: true, value: t => money(t.sharesCost) },
      { title: 'Commission', num: true, value: t => money(t.commissionPaid) }], p.lmsrTrades, 'You have not traded here yet.');
  } else {
    html = '<h3>What you hold here</h3>' + staticTable([
      { title: 'Answer', value: h => h.outcomeName },
      { title: 'Shares', num: true, value: h => qty(h.quantity) },
      { title: 'Paid for them', num: true, value: h => money(h.amountPaid) }], p.holdings, 'You hold nothing.')
      + `<p>Commission paid: ${money(p.commissionPaid)}</p>`;
  }
  if (p.winningOutcomeName) {
    html += `<p>Closed on '${esc(p.winningOutcomeName)}'. Your result: ${signed(p.profitOrLoss)}</p>`;
  }
  box.innerHTML = html;
}

// ---------- pull ----------

let polling = false;
let pollAgain = false;

/** One round: the events, the users, your own details, and every event shown in a detail panel. */
async function pollOnce() {
  if (polling) {
    pollAgain = true;
    return;
  }
  polling = true;
  try {
    const [events, users, details] = await Promise.all([get('/events'), get('/users'), get('/me')]);
    allEvents = events;
    drawEvents();
    drawUsers(users);
    drawMe(details);
    await Promise.all(panes.map(pane => pane.fetch()));
    $('connection').textContent = '';
  } catch (e) {
    $('connection').textContent = e.status === 401
      ? 'The server no longer knows you (was it restarted?). Reload the page to log in again.'
      : e.message;
  } finally {
    polling = false;
    if (pollAgain) {
      pollAgain = false;
      pollSoon();
    }
  }
}

function pollSoon() {
  setTimeout(pollOnce, 0);
}

// ---------- start up ----------

let me = null;

function showMain(name) {
  me = name;
  document.title = `Guess Market — ${name}`;
  $('who').textContent = name;
  $('account-name').textContent = name;
  $('login').hidden = true;
  $('main').hidden = false;
  pollOnce();
  setInterval(pollOnce, POLL_MS);
}

function showLogin() {
  $('main').hidden = true;
  $('login').hidden = false;
  $('login-name').focus();
}

function wire() {
  $('server-url').textContent = location.origin + API;
  eventsPane = new DetailPane($('events-detail'));
  accountPane = new DetailPane($('account-detail'));
  buildFilters();

  $('login-form').onsubmit = async event => {
    event.preventDefault();
    const name = $('login-name').value.trim();
    if (!name) {
      $('login-error').textContent = 'Please enter a name.';
      return;
    }
    try {
      showMain((await post('/login', { name })).name);
    } catch (e) {
      $('login-error').textContent = e.message;
    }
  };
  $('login-name').oninput = () => { $('login-error').textContent = ''; };

  for (const tab of document.querySelectorAll('.tab')) {
    tab.onclick = () => {
      for (const other of document.querySelectorAll('.tab')) other.classList.toggle('active', other === tab);
      $('tab-events').hidden = tab.dataset.tab !== 'events';
      $('tab-account').hidden = tab.dataset.tab !== 'account';
    };
  }

  $('funds-form').onsubmit = event => {
    event.preventDefault();
    const amount = $('funds-amount').value;
    act('Load funds', () => post('/funds', { amount }), details => {
      $('funds-amount').value = '';
      drawMe(details);
    });
  };
}

// A reload keeps the session cookie, so whoever was logged in stays logged in.
wire();
get('/me').then(details => showMain(details.name), showLogin);
