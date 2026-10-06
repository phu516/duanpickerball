import { useEffect, useState } from 'react';
import { api, resetCsrf } from '../../../shared/api/apiClient';
import { adminBookingApi } from '../../bookings/api/adminBookingApi';
import { productApi } from '../../products/api/productApi';
import { localTime, money } from '../../../shared/utils/format';
import CourtManagementPanel from './CourtManagementPanel';

export default function AdminPage() {
  const [admin, setAdmin] = useState(false);
  const [credentials, setCredentials] = useState({ username: '', password: '' });
  const [tab, setTab] = useState('bookings');
  const [courts, setCourts] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [payments, setPayments] = useState([]);
  const [cancellations, setCancellations] = useState([]);
  const [productDraft, setProductDraft] = useState({ categoryId: '', name: '', brand: 'PickleZone', description: '', emoji: '🏓', sku: '', price: 100000, stock: 10 });
  const [message, setMessage] = useState('');
  useEffect(() => { api('/admin/me').then(() => { setAdmin(true); refresh(); }).catch(() => {}); }, []);
  async function refresh() {
    try {
      const [c, b, o, p, cats, pay, requests] = await Promise.all([
        api('/admin/courts'), adminBookingApi.list(), api('/admin/orders'),
        api('/admin/products'), productApi.categories(), api('/admin/payments'),
        api('/admin/booking-cancellation-requests'),
      ]);
      setCourts(c); setBookings(b); setOrders(o); setProducts(p); setCategories(cats); setPayments(pay); setCancellations(requests);
    } catch (e) { setMessage(e.message); }
  }
  async function login(event) {
    event.preventDefault(); setMessage('');
    try {
      await api('/auth/login', { method: 'POST', body: new URLSearchParams(credentials), form: true });
      resetCsrf(); setAdmin(true); refresh();
    } catch (e) { setMessage(e.message); }
  }
  async function run(action) { try { await action(); await refresh(); setMessage('Đã cập nhật.'); } catch (e) { setMessage(e.message); } }
  async function addProduct(event) {
    event.preventDefault();
    await run(() => productApi.create({ ...productDraft, categoryId: Number(productDraft.categoryId),
      price: Number(productDraft.price), stock: Number(productDraft.stock),
      fulfillmentOptions: ['STORE_PICKUP', 'COURT_DELIVERY'] }));
  }
  if (!admin) return <section className="container section narrow"><span className="eyebrow">KHU VỰC QUẢN LÝ</span><h1>Đăng nhập admin</h1><p>Đăng nhập bằng tài khoản quản trị để quản lý sân và đơn hàng.</p>{message && <p className="inline-message error">{message}</p>}<form className="detail-card form-stack" onSubmit={login}><label>Tài khoản<input autoComplete="username" value={credentials.username} onChange={e => setCredentials({ ...credentials, username: e.target.value })}/></label><label>Mật khẩu<input type="password" autoComplete="current-password" value={credentials.password} onChange={e => setCredentials({ ...credentials, password: e.target.value })}/></label><button className="btn primary">Đăng nhập</button></form></section>;
  return <section className="container section admin"><div className="section-heading"><div><span className="eyebrow">PICKLEZONE MANAGER</span><h1>Quản lý cửa hàng & sân</h1></div><button className="btn outline" onClick={() => run(async () => { await api('/auth/logout', { method: 'POST' }); resetCsrf(); setAdmin(false); })}>Đăng xuất</button></div>{message && <p className="inline-message">{message}</p>}
    <div className="stats"><div><b>{courts.length}</b><span>Sân</span></div><div><b>{bookings.length}</b><span>Phiếu</span></div><div><b>{orders.length}</b><span>Đơn</span></div><div><b>{products.length}</b><span>Sản phẩm</span></div></div>
    <div className="tabs">{[['bookings','Phiếu sân'],['cancellations','Yêu cầu hủy'],['courts','Sân & giá'],['orders','Đơn hàng'],['products','Sản phẩm'],['payments','Thanh toán']].map(([key, label]) => <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{label}</button>)}</div>
    {tab === 'cancellations' && <div className="table-card"><h2>Yêu cầu hủy sân</h2><div className="table-scroll"><table><thead><tr><th>Phiếu ID</th><th>Lý do khách</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{cancellations.map(item => <tr key={item.id}><td>#{item.bookingId}</td><td>{item.reason}</td><td>{item.status}</td><td className="actions-cell">{item.status === 'PENDING' && <><button onClick={() => { const reason = prompt('Lý do duyệt:'); if (reason?.trim()) run(() => api(`/admin/booking-cancellation-requests/${item.id}/approve`, { method: 'POST', body: { reason } })); }}>Duyệt hủy</button><button onClick={() => { const reason = prompt('Lý do từ chối:'); if (reason?.trim()) run(() => api(`/admin/booking-cancellation-requests/${item.id}/reject`, { method: 'POST', body: { reason } })); }}>Từ chối</button></>}</td></tr>)}</tbody></table></div></div>}
    {tab === 'bookings' && <div className="table-card"><h2>Phiếu đặt sân</h2><div className="table-scroll"><table><thead><tr><th>Mã</th><th>Khách</th><th>Sân / giờ</th><th>Cọc</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{bookings.map(item => <tr key={item.id}><td><b>{item.bookingCode}</b></td><td>{item.guestName}<small>{item.guestPhone}</small></td><td>{item.courtName}<small>{localTime(item.startAt)}</small></td><td>{money(item.depositRequired)}</td><td><span className="badge">{item.status}</span></td><td className="actions-cell">{item.status === 'CONFIRMED' && <><button onClick={() => run(() => adminBookingApi.checkIn(item.id))}>Nhận sân</button><button onClick={() => run(() => adminBookingApi.noShow(item.id))}>Không đến</button></>}{item.status === 'CHECKED_IN' && <button onClick={() => run(() => adminBookingApi.complete(item.id))}>Hoàn tất</button>}{['HOLD','CONFIRMED'].includes(item.status) && <button onClick={() => { const reason = prompt('Lý do hủy phiếu:'); if (reason?.trim()) run(() => adminBookingApi.cancel(item.id, reason)); }}>Hủy phiếu</button>}</td></tr>)}</tbody></table></div></div>}
    {tab === 'courts' && <CourtManagementPanel courts={courts} onChanged={refresh}/>}
    {tab === 'orders' && <div className="table-card"><h2>Đơn hàng</h2><div className="table-scroll"><table><thead><tr><th>Mã</th><th>Khách</th><th>Cách nhận</th><th>Tổng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{orders.map(item => <tr key={item.id}><td>{item.orderCode}</td><td>{item.guestName}</td><td>{item.fulfillmentMethod}{item.courtName && ` · ${item.courtName}`}</td><td>{money(item.totalAmount)}</td><td><span className="badge">{item.status}</span></td><td className="actions-cell">{item.status === 'NEW' && <button onClick={() => run(() => api(`/admin/orders/${item.id}/confirm`, { method: 'POST' }))}>Xác nhận</button>}{item.status === 'CONFIRMED' && <button onClick={() => run(() => api(`/admin/orders/${item.id}/prepare`, { method: 'POST' }))}>Chuẩn bị</button>}{item.fulfillmentMethod === 'COURT_DELIVERY' && <button onClick={() => { const username = prompt('Tài khoản nhân viên:'); if (username) run(() => api(`/admin/orders/${item.id}/assign`, { method: 'POST', body: { username } })); }}>Giao nhân viên</button>}</td></tr>)}</tbody></table></div></div>}
    {tab === 'products' && <><div className="table-card"><h2>Sản phẩm</h2><div className="table-scroll"><table><thead><tr><th>Tên</th><th>Danh mục</th><th>SKU</th><th>Giá</th><th>Còn</th></tr></thead><tbody>{products.map(item => <tr key={item.id}><td>{item.emoji} {item.name}</td><td>{item.category}</td><td>{item.sku}</td><td>{money(item.price)}</td><td>{item.stock}</td></tr>)}</tbody></table></div></div><form className="detail-card form-grid" onSubmit={addProduct}><h2>Thêm sản phẩm</h2><label>Danh mục<select required value={productDraft.categoryId} onChange={e => setProductDraft({ ...productDraft, categoryId: e.target.value })}><option value="">Chọn danh mục</option>{categories.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{['name','brand','emoji','sku'].map(key => <label key={key}>{key}<input required value={productDraft[key]} onChange={e => setProductDraft({ ...productDraft, [key]: e.target.value })}/></label>)}<label>Giá<input required type="number" min="1" value={productDraft.price} onChange={e => setProductDraft({ ...productDraft, price: e.target.value })}/></label><label>Tồn<input required type="number" min="0" value={productDraft.stock} onChange={e => setProductDraft({ ...productDraft, stock: e.target.value })}/></label><button className="btn primary">Lưu sản phẩm</button></form></>}
    {tab === 'payments' && <div className="table-card"><h2>Giao dịch</h2><div className="table-scroll"><table><thead><tr><th>ID</th><th>Mục đích</th><th>Phương thức</th><th>Số tiền</th><th>Trạng thái</th></tr></thead><tbody>{payments.map(item => <tr key={item.paymentId}><td>#{item.paymentId}</td><td>{item.purpose}</td><td>{item.method}</td><td>{money(item.amount)}</td><td><span className="badge">{item.status}</span></td></tr>)}</tbody></table></div></div>}
  </section>;
}
