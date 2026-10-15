import { useEffect, useState } from 'react';
import { orderApi } from '../api/orderApi';
import { paymentApi } from '../../payments/api/paymentApi';
import PaymentPanel from '../../payments/PaymentPanel';
import { money, orderTokenKey } from '../../../shared/utils/format';

export default function OrderDetailPage({ code }) {
  const token = sessionStorage.getItem(orderTokenKey(code));
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [message, setMessage] = useState('');
  const [paymentOpen, setPaymentOpen] = useState(false);

  useEffect(() => {
    if (!token) return;
    orderApi.detail(code, token).then(setOrder).catch(error => setMessage(error.message));
    paymentApi.orderList(code, token).then(list => {
      if (list.length) paymentApi.orderDetail(code, token, list[0].paymentId).then(setPayment);
    }).catch(error => setMessage(error.message));
  }, [code, token]);

  useEffect(() => {
    if (!token || !payment || payment.status !== 'PENDING') return undefined;
    let checking = false;
    async function refreshStatus() {
      if (checking || document.visibilityState === 'hidden') return;
      checking = true;
      try {
        const updated = await paymentApi.orderDetail(code, token, payment.paymentId);
        setPayment(updated);
        if (updated.status !== 'PENDING') setOrder(await orderApi.detail(code, token));
      } catch { /* Keep the QR visible and retry the next status check. */ }
      finally { checking = false; }
    }
    const timer = window.setInterval(refreshStatus, 3000);
    window.addEventListener('focus', refreshStatus);
    document.addEventListener('visibilitychange', refreshStatus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', refreshStatus);
      document.removeEventListener('visibilitychange', refreshStatus);
    };
  }, [code, token, payment?.paymentId, payment?.status]);

  async function startPayment() {
    try {
      const created = payment?.status === 'PENDING' ? payment : await paymentApi.order(code, token);
      setPayment(created);
      setPaymentOpen(true);
      setMessage(created.provider === 'PAYOS' ? 'Quét QR để thanh toán đơn hàng.' : 'Đã tạo khoản thanh toán thử.');
    } catch (error) { setMessage(error.message); }
  }

  async function completeSandbox() {
    try {
      setPayment(await paymentApi.sandboxComplete(payment.paymentId, token));
      setOrder(await orderApi.detail(code, token));
      setMessage('Thanh toán thử thành công.');
    } catch (error) { setMessage(error.message); }
  }

  async function cancel() {
    try {
      setOrder(await orderApi.cancel(code, token));
      setPayment(null);
      setPaymentOpen(false);
      setMessage('Đã hủy đơn.');
    } catch (error) { setMessage(error.message); }
  }

  if (!token) return <section className="container section narrow"><h1>Không có quyền truy cập đơn hàng</h1><p>Chỉ trình duyệt đã tạo đơn có token để xem chi tiết.</p></section>;

  return <section className="container section narrow">
    <span className="eyebrow">ĐƠN HÀNG</span><h1>{code}</h1>
    {message && <p className="inline-message">{message}</p>}
    {order && <div className="detail-card">
      <div className="detail-top"><h2>{order.guestName}</h2><span className="badge">{order.status}</span></div>
      <p>{order.fulfillmentMethod === 'COURT_DELIVERY' ? `Giao tới ${order.courtName}` : order.fulfillmentMethod === 'HOME_DELIVERY' ? 'Giao tận nơi' : 'Nhận tại cửa hàng'}</p>
      {order.deliveryAddress && <p><b>{order.fulfillmentMethod === 'COURT_DELIVERY' ? 'Ghi chú điểm giao:' : 'Địa chỉ nhận hàng:'}</b> {order.deliveryAddress}</p>}
      {order.items.map((item, index) => <div key={index} className="detail-row"><span>{item.productName} × {item.quantity}</span><b>{money(item.unitPrice * item.quantity)}</b></div>)}
      <div className="detail-row total"><span>Tổng cộng</span><b>{money(order.totalAmount)}</b></div>
      {order.status === 'NEW' && <div className="button-row">{payment?.status !== 'SUCCEEDED' && <button className="btn primary" onClick={startPayment}>{payment?.status === 'PENDING' ? 'Tiếp tục thanh toán' : 'Thanh toán'}</button>}<button className="btn outline" onClick={cancel}>Hủy đơn</button></div>}
      <PaymentPanel payment={payment} onSandboxComplete={completeSandbox} modal isOpen={paymentOpen} onClose={() => setPaymentOpen(false)}/>
    </div>}
  </section>;
}
