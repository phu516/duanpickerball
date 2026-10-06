import { useEffect, useState } from 'react';
import { api, resetCsrf } from '../../../shared/api/apiClient';
import { money } from '../../../shared/utils/format';

export default function StaffPage() {
  const [loggedIn, setLoggedIn] = useState(false);
  const [credentials, setCredentials] = useState({ username: '', password: '' });
  const [orders, setOrders] = useState([]);
  const [message, setMessage] = useState('');
  useEffect(() => { load(); }, []);
  async function load() {
    try { setOrders(await api('/staff/fulfillments')); setLoggedIn(true); }
    catch { setLoggedIn(false); }
  }
  async function login(event) {
    event.preventDefault();
    try { await api('/auth/login', { method: 'POST', body: new URLSearchParams(credentials), form: true }); resetCsrf(); load(); }
    catch (error) { setMessage(error.message); }
  }
  async function action(id, name) {
    try { await api(`/staff/orders/${id}/${name}`, { method: 'POST' }); setMessage('Đã cập nhật đơn giao sân.'); load(); }
    catch (error) { setMessage(error.message); }
  }
  if (!loggedIn) return <section className="container section narrow"><span className="eyebrow">NHÂN VIÊN</span><h1>Đăng nhập giao đến sân</h1>{message && <p className="inline-message error">{message}</p>}<form className="detail-card form-stack" onSubmit={login}><label>Tài khoản<input value={credentials.username} onChange={e => setCredentials({ ...credentials, username: e.target.value })}/></label><label>Mật khẩu<input type="password" value={credentials.password} onChange={e => setCredentials({ ...credentials, password: e.target.value })}/></label><button className="btn primary">Đăng nhập</button></form></section>;
  return <section className="container section"><span className="eyebrow">NHÂN VIÊN</span><h1>Đơn giao đến sân được phân công</h1>{message && <p className="inline-message">{message}</p>}<div className="product-grid">{orders.map(order => <div key={order.id} className="detail-card"><h2>{order.orderCode}</h2><p>{order.courtName} · {order.guestName}</p><p>{order.items.map(item => `${item.productName} × ${item.quantity}`).join(', ')}</p><strong>{money(order.totalAmount)}</strong><p><span className="badge">{order.status}</span></p>{order.status === 'CONFIRMED' && <button className="btn primary" onClick={() => action(order.id, 'start-preparation')}>Bắt đầu chuẩn bị</button>}{order.status === 'PREPARING' && <button className="btn primary" onClick={() => action(order.id, 'dispatch-to-court')}>Mang ra sân</button>}{order.status === 'OUT_FOR_COURT_DELIVERY' && <button className="btn primary" onClick={() => action(order.id, 'mark-delivered')}>Đã giao khách</button>}</div>)}</div>{!orders.length && <div className="empty-card">Chưa có đơn nào được giao cho bạn.</div>}</section>;
}
