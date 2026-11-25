import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api, resetCsrf } from '../../../shared/api/apiClient';
import { adminBookingApi } from '../../bookings/api/adminBookingApi';
import { productApi } from '../../products/api/productApi';
import { localTime, money } from '../../../shared/utils/format';
import CourtManagementPanel from './CourtManagementPanel';
import ProductImageEditor from './ProductImageEditor';
import TournamentAdmin from './TournamentAdmin';
import EloAdmin from '../../elo/EloAdmin';
import './AdminPage.css';

const checkInAvailableAt = booking => new Date(booking.startAt).getTime() - 30 * 60_000;
const completionAvailableAt = booking => new Date(booking.endAt).getTime();
const noShowAvailableAt = booking => new Date(booking.startAt).getTime()
  + (booking.lateArrivalGraceMinutes ?? 30) * 60_000;
const paidBookings = (bookings, payments) => {
  const paidCodes = new Set(payments
    .filter(item => item.purpose === 'DEPOSIT' && item.status === 'SUCCEEDED')
    .map(item => item.bookingCode));
  return bookings.filter(item => item.status === 'CANCELLED'
    || (item.status !== 'HOLD' && paidCodes.has(item.bookingCode)));
};
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
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelError, setCancelError] = useState('');
  const [cancelSaving, setCancelSaving] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [actionNoticeKey, setActionNoticeKey] = useState(null);
  const [productDraft, setProductDraft] = useState({ categoryId: '', name: '', brand: 'PickleZone', description: '', emoji: '🏓', imageUrl: '', sku: '', price: 100000, stock: 10 });
  const [message, setMessage] = useState('');
  const cancelReasonRef = useRef(null);
  const noShowNoticeTimerRef = useRef(null);
  useEffect(() => { api('/admin/me').then(() => { setAdmin(true); refresh(); }).catch(() => {}); }, []);
  useEffect(() => {
    if (!admin) return undefined;
    const verifySession = () => api('/admin/me').catch(error => {
      if (error.status === 401) {
        setAdmin(false);
        setMessage('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      }
    });
    window.addEventListener('focus', verifySession);
    return () => window.removeEventListener('focus', verifySession);
  }, [admin]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!admin || tab !== 'payments') return undefined;
    let active = true;
    const loadPayments = () => api('/admin/payments')
      .then(items => { if (active) setPayments(items); })
      .catch(error => { if (active) setMessage(error.message); });
    loadPayments();
    const timer = window.setInterval(loadPayments, 10_000);
    return () => { active = false; window.clearInterval(timer); };
  }, [admin, tab]);
  useEffect(() => {
    if (!admin || tab !== 'bookings') return undefined;
    let active = true;
    const loadBookings = () => Promise.all([adminBookingApi.list(), api('/admin/payments')])
      .then(([items, transactions]) => {
        if (active) {
          setBookings(paidBookings(items, transactions));
          setPayments(transactions);
        }
      }).catch(error => { if (active) setMessage(error.message); });
    loadBookings();
    const timer = window.setInterval(loadBookings, 5000);
    window.addEventListener('focus', loadBookings);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.removeEventListener('focus', loadBookings);
    };
  }, [admin, tab]);
  useEffect(() => () => window.clearTimeout(noShowNoticeTimerRef.current), []);
  async function refresh() {
    try {
      const [c, b, o, p, cats, pay] = await Promise.all([
        api('/admin/courts'), adminBookingApi.list(), api('/admin/orders'),
        api('/admin/products'), productApi.categories(), api('/admin/payments'),
      ]);
      setCourts(c); setBookings(paidBookings(b, pay)); setOrders(o); setProducts(p); setCategories(cats); setPayments(pay);
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
  function showActionNotice(key) {
    setActionNoticeKey(key);
    window.clearTimeout(noShowNoticeTimerRef.current);
    noShowNoticeTimerRef.current = window.setTimeout(() => setActionNoticeKey(null), 2200);
  }
  function openCancelDialog(item) {
    setCancelTarget(item);
    setCancelReason('');
    setCancelError('');
  }
  async function submitCancelBooking(event) {
    event.preventDefault();
    if (!cancelTarget) return;
    const reason = cancelReason.trim();
    if (!reason) {
      setCancelError('Vui lòng nhập lý do hủy phiếu.');
      return;
    }
    setCancelSaving(true);
    setCancelError('');
    try {
      await adminBookingApi.cancel(cancelTarget.id, reason);
      setCancelTarget(null);
      setCancelReason('');
      setMessage('Đã cập nhật.');
      await refresh();
    } catch (error) {
      setCancelError(error.message);
    } finally {
      setCancelSaving(false);
    }
  }
  useEffect(() => {
    if (!cancelTarget) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cancelReasonRef.current?.focus();
    function closeOnEscape(event) {
      if (event.key === 'Escape' && !cancelSaving) setCancelTarget(null);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => {
      window.removeEventListener('keydown', closeOnEscape);
      document.body.style.overflow = previousOverflow;
    };
  }, [cancelTarget, cancelSaving]);
  async function addProduct(event) {
    event.preventDefault();
    await run(() => productApi.create({ ...productDraft, categoryId: Number(productDraft.categoryId),
      price: Number(productDraft.price), stock: Number(productDraft.stock),
      fulfillmentOptions: ['STORE_PICKUP', 'COURT_DELIVERY'] }));
  }
  if (!admin) return <section className="container section narrow"><span className="eyebrow">KHU VỰC QUẢN LÝ</span><h1>Đăng nhập admin</h1><p>Đăng nhập bằng tài khoản quản trị để quản lý sân và đơn hàng.</p>{message && <p className="inline-message error">{message}</p>}<form className="detail-card form-stack" onSubmit={login}><label>Tài khoản<input autoComplete="username" value={credentials.username} onChange={e => setCredentials({ ...credentials, username: e.target.value })}/></label><label>Mật khẩu<input type="password" autoComplete="current-password" value={credentials.password} onChange={e => setCredentials({ ...credentials, password: e.target.value })}/></label><button className="btn primary">Đăng nhập</button></form></section>;
  return <section className="container section admin"><div className="section-heading"><div><span className="eyebrow">PICKLEZONE MANAGER</span><h1>Quản lý cửa hàng & sân</h1></div><button className="btn outline" onClick={() => run(async () => { await api('/auth/logout', { method: 'POST' }); resetCsrf(); setAdmin(false); })}>Đăng xuất</button></div>{message && <p className="inline-message">{message}</p>}
    <div className="stats"><div><b>{courts.length}</b><span>Sân</span></div><div><b>{bookings.length}</b><span>Phiếu</span></div><div><b>{orders.length}</b><span>Đơn</span></div><div><b>{products.length}</b><span>Sản phẩm</span></div></div>
    <div className="tabs">{[['bookings','Phiếu sân'],['courts','Sân & giá'],['orders','Đơn hàng'],['products','Sản phẩm'],['payments','Thanh toán'],['hero','Giải đấu'],['elo','Elo']].map(([key, label]) => <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{label}</button>)}</div>
    {tab === 'elo' && <EloAdmin/>}
    {tab === 'bookings' && <div className="table-card"><h2>Phiếu đặt sân</h2><div className="table-scroll"><table className="bookings-table"><thead><tr><th>Mã</th><th>Khách</th><th>Sân</th><th>Ngày</th><th>Giờ bắt đầu</th><th>Giờ kết thúc</th><th>Cọc</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{bookings.map(item => <tr key={item.id}><td><b>{item.bookingCode}</b></td><td>{item.guestName}<small>{item.guestPhone}</small></td><td>{item.courtName}</td><td>{new Date(item.startAt).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' })}</td><td>{localTime(item.startAt).split(' ')[0]}</td><td>{new Date(item.endAt).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' })}</td><td>{money(item.depositRequired)}</td><td className="status-cell"><span className="badge" title={item.status === 'NO_SHOW' ? 'Khách không đến nhận sân trong 30 phút sau giờ bắt đầu' : undefined}>{item.status}</span></td><td className="actions-cell">{item.status === 'CONFIRMED' && <><span className="action-notice-anchor"><button className="booking-action booking-action-check-in" title={now < checkInAvailableAt(item) ? `Có thể nhận sân từ ${localTime(checkInAvailableAt(item))}` : 'Nhận sân cho khách đã đến'} onClick={() => Date.now() < checkInAvailableAt(item) ? showActionNotice(`${item.id}-check-in`) : run(() => adminBookingApi.checkIn(item.id))}>Nhận sân</button>{actionNoticeKey === `${item.id}-check-in` && <small className="action-notice" role="status">Nhận sân từ {localTime(checkInAvailableAt(item))}</small>}</span><span className="action-notice-anchor"><button className="booking-action booking-action-no-show" title={now < noShowAvailableAt(item) ? `Có thể đánh dấu sau ${localTime(noShowAvailableAt(item))}` : 'Đánh dấu khách không đến'} onClick={() => Date.now() < noShowAvailableAt(item) ? showActionNotice(`${item.id}-no-show`) : run(() => adminBookingApi.noShow(item.id))}>Không đến</button>{actionNoticeKey === `${item.id}-no-show` && <small className="action-notice" role="status">Chưa đến giờ</small>}</span></>}{item.status === 'CHECKED_IN' && <span className="action-notice-anchor"><button className="booking-action booking-action-complete" title={now < completionAvailableAt(item) ? `Có thể hoàn tất từ ${localTime(completionAvailableAt(item))}` : 'Đánh dấu phiếu đã hoàn tất'} onClick={() => Date.now() < completionAvailableAt(item) ? showActionNotice(`${item.id}-complete`) : run(() => adminBookingApi.complete(item.id))}>Hoàn tất</button>{actionNoticeKey === `${item.id}-complete` && <small className="action-notice" role="status">Hoàn tất từ {localTime(completionAvailableAt(item))}</small>}</span>}{['HOLD','CONFIRMED'].includes(item.status) && <button className="booking-action booking-action-cancel" title="Mở xác nhận hủy phiếu" onClick={() => openCancelDialog(item)}>Hủy phiếu</button>}</td></tr>)}</tbody></table></div></div>}
    {tab === 'courts' && <CourtManagementPanel courts={courts} onChanged={refresh}/>}
    {tab === 'hero' && <TournamentAdmin onSessionExpired={() => { setAdmin(false); setMessage('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'); }}/>}
    {tab === 'orders' && <div className="table-card"><h2>Đơn hàng</h2><div className="table-scroll"><table><thead><tr><th>Mã</th><th>Khách</th><th>Cách nhận</th><th>Tổng</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{orders.map(item => <tr key={item.id}><td>{item.orderCode}</td><td>{item.guestName}</td><td>{item.fulfillmentMethod}{item.courtName && ` · ${item.courtName}`}</td><td>{money(item.totalAmount)}</td><td><span className="badge">{item.status}</span></td><td className="actions-cell">{item.status === 'NEW' && <button onClick={() => run(() => api(`/admin/orders/${item.id}/confirm`, { method: 'POST' }))}>Xác nhận</button>}{item.status === 'CONFIRMED' && <button onClick={() => run(() => api(`/admin/orders/${item.id}/prepare`, { method: 'POST' }))}>Chuẩn bị</button>}{item.fulfillmentMethod === 'COURT_DELIVERY' && <button onClick={() => { const username = prompt('Tài khoản nhân viên:'); if (username) run(() => api(`/admin/orders/${item.id}/assign`, { method: 'POST', body: { username } })); }}>Giao nhân viên</button>}</td></tr>)}</tbody></table></div></div>}
    {tab === 'products' && <>
      <div className="table-card"><h2>Sản phẩm</h2><div className="table-scroll"><table><thead><tr><th>Ảnh</th><th>Tên</th><th>Danh mục</th><th>SKU</th><th>Giá</th><th>Còn</th></tr></thead><tbody>{products.map(item => <tr key={item.id}><td>{item.imageUrl ? <img src={item.imageUrl} alt="" style={{ width: 54, height: 54, objectFit: 'cover', borderRadius: 8 }}/> : item.emoji}</td><td>{item.name}</td><td>{item.category}</td><td>{item.sku}</td><td>{money(item.price)}</td><td>{item.stock}</td></tr>)}</tbody></table></div></div>
      <ProductImageEditor products={products} onChanged={refresh}/>
      <form className="detail-card form-grid" onSubmit={addProduct}><h2>Thêm sản phẩm</h2><label>Danh mục<select required value={productDraft.categoryId} onChange={e => setProductDraft({ ...productDraft, categoryId: e.target.value })}><option value="">Chọn danh mục</option>{categories.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{['name','brand','emoji','sku'].map(key => <label key={key}>{key}<input required value={productDraft[key]} onChange={e => setProductDraft({ ...productDraft, [key]: e.target.value })}/></label>)}<label>URL ảnh<input required type="url" maxLength="500" value={productDraft.imageUrl} onChange={e => setProductDraft({ ...productDraft, imageUrl: e.target.value })} placeholder="https://images.example.com/san-pham.jpg"/></label><label>Giá<input required type="number" min="1" value={productDraft.price} onChange={e => setProductDraft({ ...productDraft, price: e.target.value })}/></label><label>Tồn<input required type="number" min="0" value={productDraft.stock} onChange={e => setProductDraft({ ...productDraft, stock: e.target.value })}/></label><button className="btn primary">Lưu sản phẩm</button></form>
    </>}
    {tab === 'payments' && <div className="table-card"><h2>Giao dịch</h2><div className="table-scroll"><table><thead><tr><th>ID</th><th>Phiếu / đơn</th><th>Mục đích</th><th>Kênh</th><th>Số tiền</th><th>Trạng thái</th><th>Nhận tiền</th><th>Tham chiếu</th></tr></thead><tbody>{payments.map(item => <tr key={item.paymentId}><td>#{item.paymentId}</td><td>{item.bookingCode || item.orderCode || '—'}</td><td>{item.purpose}</td><td>{item.provider}</td><td>{money(item.amount)}</td><td><span className="badge">{item.status === 'REVIEW_REQUIRED' ? 'Cần đối soát' : item.status}</span></td><td>{item.paidAt ? localTime(item.paidAt) : '—'}</td><td>{item.providerReference || '—'}</td></tr>)}</tbody></table></div></div>}
    {cancelTarget && createPortal(<div className="admin-cancel-backdrop" onMouseDown={event => {
      if (event.target === event.currentTarget && !cancelSaving) setCancelTarget(null);
    }}>
      <section className="admin-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-cancel-title" aria-describedby="admin-cancel-description">
        <div className="admin-cancel-heading"><div><span className="eyebrow">XÁC NHẬN HỦY</span><h2 id="admin-cancel-title">Hủy phiếu đặt sân</h2></div><button type="button" className="admin-cancel-close" aria-label="Đóng biểu mẫu" disabled={cancelSaving} onClick={() => setCancelTarget(null)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button></div>
        <div className="admin-cancel-booking"><b>{cancelTarget.bookingCode}</b><span>{cancelTarget.guestName} · {cancelTarget.courtName}</span><small>{localTime(cancelTarget.startAt)} – {localTime(cancelTarget.endAt)}</small></div>
        <p className="admin-cancel-description" id="admin-cancel-description">Vui lòng ghi rõ lý do hủy. Thao tác này sẽ cập nhật trạng thái phiếu đặt.</p>
        <form onSubmit={submitCancelBooking}>
          <label htmlFor="admin-cancel-reason">Lý do hủy phiếu</label>
          <textarea ref={cancelReasonRef} id="admin-cancel-reason" rows="4" required maxLength="500" placeholder="Nhập lý do hủy phiếu..." value={cancelReason} onChange={event => { setCancelReason(event.target.value); setCancelError(''); }} disabled={cancelSaving}/>
          <div className="admin-cancel-meta"><small>{cancelReason.length}/500 ký tự</small>{cancelError && <span role="alert">{cancelError}</span>}</div>
          <div className="admin-cancel-actions"><button type="button" className="btn outline" disabled={cancelSaving} onClick={() => setCancelTarget(null)}>Quay lại</button><button type="submit" className="btn danger" disabled={cancelSaving || !cancelReason.trim()}>{cancelSaving ? 'Đang xử lý…' : 'Xác nhận hủy phiếu'}</button></div>
        </form>
      </section>
    </div>, document.body)}
  </section>;
}
