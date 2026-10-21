'use strict';
const $ = (selector) => document.querySelector(selector);
const money = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });
const date = new Intl.DateTimeFormat('en-IE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
let products = [];
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

async function api(path, options = {}) {
  const response = await fetch('/api' + path, {
    ...options, headers: { 'Content-Type': 'application/json', ...options.headers }
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.message || 'Something went wrong. Please try again.');
  return data;
}
function notify(message, error = false) {
  const notice = $('#notice');
  notice.textContent = message;
  notice.classList.toggle('error', error);
  notice.hidden = false;
}
async function refresh() {
  const [all, summary, history] = await Promise.all([api('/products'), api('/summary'), api('/movements')]);
  products = all;
  $('#total-products').textContent = summary.products;
  $('#total-units').textContent = summary.units.toLocaleString('en-IE');
  $('#low-stock').textContent = summary.lowStock;
  $('#stock-value').textContent = money.format(summary.stockValue);
  $('#nav-count').textContent = all.length;
  $('#catalogue-count').textContent = all.length;
  const selected = $('#category').value;
  const categories = [...new Set(all.map(p => p.category))].sort();
  $('#category').innerHTML = '<option value="">All categories</option>' + categories.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
  $('#category').value = categories.includes(selected) ? selected : '';
  $('#categories').innerHTML = categories.map(c => `<option value="${escapeHtml(c)}"></option>`).join('');
  renderProducts();
  $('#movements').innerHTML = history.length ? history.map(m => `<tr><td><strong>${escapeHtml(m.productName)}</strong><span class="sku">${escapeHtml(m.sku)}</span></td><td class="movement-${m.direction.toLowerCase()}">${m.direction === 'IN' ? '↙ +' : '↗ −'}${m.quantity} <span>${m.direction === 'IN' ? 'Stock in' : 'Stock out'}</span></td><td class="movement-note">${escapeHtml(m.note)}</td><td class="number">${m.balanceAfter}</td><td class="number">${date.format(new Date(m.recordedAt))}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">No stock movements yet. Add a product, then record its first delivery.</td></tr>';
}
function renderProducts() {
  const query = $('#search').value.trim().toLowerCase();
  const visible = products.filter(p => (!query || `${p.name} ${p.sku}`.toLowerCase().includes(query)) && (!$('#category').value || p.category === $('#category').value) && (!$('#low-only').checked || p.lowStock));
  $('#results-count').textContent = `${visible.length} of ${products.length} products`;
  $('#products').innerHTML = visible.length ? visible.map(p => `<tr><td><div class="product-cell"><span class="product-icon" aria-hidden="true">${escapeHtml(p.name.charAt(0).toUpperCase())}</span><div><div class="product-name" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</div><span class="sku">${escapeHtml(p.sku)}</span></div></div></td><td>${escapeHtml(p.category)}</td><td class="number">${money.format(p.unitPrice)}</td><td class="number"><span class="stock-number">${p.quantity}</span><span class="reorder">Reorder at ${p.reorderLevel}</span></td><td><span class="badge ${p.quantity === 0 ? 'out' : p.lowStock ? 'low' : ''}">${p.quantity === 0 ? 'Out of stock' : p.lowStock ? 'Low stock' : 'In stock'}</span></td><td><div class="row-actions"><button class="small-button" data-action="edit" data-id="${p.id}" aria-label="Edit ${escapeHtml(p.name)}">Edit</button><button class="small-button move" data-action="move" data-id="${p.id}" aria-label="Update stock for ${escapeHtml(p.name)}">↔ Stock</button></div></td></tr>`).join('') : `<tr><td colspan="6" class="empty">${products.length ? 'No products match these filters. Try another search.' : 'Your shelves are ready. Add your first product to get started.'}</td></tr>`;
}
function openProduct(product) {
  const form = $('#product-form');
  form.reset();
  form.querySelector('.form-error').hidden = true;
  form.elements.id.value = '';
  $('#product-title').textContent = product ? 'Edit product' : 'Add a product';
  if (product) for (const field of ['id', 'name', 'sku', 'category', 'unitPrice', 'reorderLevel']) form.elements[field].value = product[field];
  $('#product-dialog').showModal();
}
$('#add-product').addEventListener('click', () => openProduct());
$('#products').addEventListener('click', event => {
  const button = event.target.closest('button[data-id]');
  if (!button) return;
  const product = products.find(p => p.id === Number(button.dataset.id));
  if (!product) return;
  if (button.dataset.action === 'edit') return openProduct(product);
  const form = $('#movement-form');
  form.reset();
  form.querySelector('.form-error').hidden = true;
  form.elements.id.value = product.id;
  $('#movement-product').textContent = `${product.name} · ${product.sku} · ${product.quantity} units on hand`;
  $('#movement-dialog').showModal();
});
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
['#search', '#category', '#low-only'].forEach(selector => $(selector).addEventListener(selector === '#search' ? 'input' : 'change', renderProducts));

async function submit(event, makeRequest) {
  event.preventDefault();
  const form = event.target;
  const error = form.querySelector('.form-error');
  const button = form.querySelector('button[type="submit"]');
  error.hidden = true;
  button.disabled = true;
  try {
    await makeRequest(Object.fromEntries(new FormData(form)));
    form.closest('dialog').close();
    try { await refresh(); notify('Saved. Your inventory is up to date.'); }
    catch (_) { notify('Saved, but the page could not refresh. Reload to see the latest inventory.', true); }
  } catch (failure) {
    error.textContent = failure.message;
    error.hidden = false;
  } finally { button.disabled = false; }
}
$('#product-form').addEventListener('submit', event => submit(event, data => api('/products' + (data.id ? '/' + data.id : ''), {
  method: data.id ? 'PUT' : 'POST', body: JSON.stringify({sku: data.sku, name: data.name, category: data.category, unitPrice: Number(data.unitPrice), reorderLevel: Number(data.reorderLevel)})
})));
$('#movement-form').addEventListener('submit', event => submit(event, data => api(`/products/${data.id}/movements`, {
  method: 'POST', body: JSON.stringify({direction: data.direction, quantity: Number(data.quantity), note: data.note})
})));
refresh().catch(() => {
  $('#products').innerHTML = '<tr><td colspan="6" class="empty">Could not load inventory. Check that the Java server is running, then reload this page.</td></tr>';
  notify('The inventory could not be loaded. Please reload to try again.', true);
});
