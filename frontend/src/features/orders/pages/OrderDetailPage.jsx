import { useEffect, useState } from 'react';
import { orderApi } from '../api/orderApi';
import { paymentApi } from '../../payments/api/paymentApi';
import { money, orderTokenKey } from '../../../shared/utils/format';

export default function OrderDetailPage({ code }) {
  const token = sessionStorage.getItem(orderTokenKey(code));
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [message, setMessage] = useState('');
  useEffect(() => { if (token) orderApi.detail(code, token).then(setOrder).catch(e => setMessage(e.message)); }, [code, token]);
  async function startPayment() { try { setPayment(await paymentApi.order(code, token)); } catch (e) { setMessage(e.message); } }
  async function completeSandbox() { try { await paymentApi.sandboxComplete(payment.paymentId, token); setPayment(null); setMessage('Đã thanh toán thử thành công.'); } catch (e) { setMessage(e.message); } }
  async function cancel() { try { setOrder(await orderApi.cancel(code, token)); setMessage('Đã hủy đơn.'); } catch (e) { setMessage(e.message); } }
  if (!token) return <section className="container section narrow"><h1>Không có quyền truy cập đơn hàng</h1><p>Chỉ trình duyệt đã tạo đơn có token để xem chi tiết.</p></section>;
  return <section className="container section narrow"><span className="eyebrow">ĐƠN HÀNG</span><h1>{code}</h1>{message && <p className="inline-message">{message}</p>}{order && <div className="detail-card"><div className="detail-top"><h2>{order.guestName}</h2><span className="badge">{order.status}</span></div><p>{order.fulfillmentMethod === 'COURT_DELIVERY' ? `Giao tới ${order.courtName}` : order.fulfillmentMethod === 'HOME_DELIVERY' ? 'Giao tận nơi' : 'Nhận tại cửa hàng'}</p>{order.items.map((item, index) => <div key={index} className="detail-row"><span>{item.productName} × {item.quantity}</span><b>{money(item.unitPrice * item.quantity)}</b></div>)}<div className="detail-row total"><span>Tổng cộng</span><b>{money(order.totalAmount)}</b></div>{order.status === 'NEW' && <div className="button-row"><button className="btn primary" onClick={startPayment}>Thanh toán thử</button><button className="btn outline" onClick={cancel}>Hủy đơn</button></div>}{payment && <div className="payment-box"><p>Khoản thu sandbox #{payment.paymentId} · {money(payment.amount)}</p><button className="btn primary" onClick={completeSandbox}>Xác nhận thanh toán thử</button></div>}</div>}</section>;
}
