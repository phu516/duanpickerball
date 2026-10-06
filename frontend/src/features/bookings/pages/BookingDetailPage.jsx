import { useEffect, useState } from 'react';
import { guestBookingApi } from '../api/guestBookingApi';
import { paymentApi } from '../../payments/api/paymentApi';
import { bookingTokenKey, localTime, money } from '../../../shared/utils/format';

export default function BookingDetailPage({ code }) {
  const token = sessionStorage.getItem(bookingTokenKey(code));
  const [booking, setBooking] = useState(null);
  const [payment, setPayment] = useState(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { if (token) guestBookingApi.detail(code, token).then(setBooking).catch(e => setMessage(e.message)); }, [code, token]);
  async function startPayment(purpose) {
    try { setPayment(await paymentApi.booking(code, token, purpose)); setMessage('Đã tạo khoản thanh toán sandbox. Xác nhận bên dưới để thử luồng cọc.'); }
    catch (e) { setMessage(e.message); }
  }
  async function completeSandbox() {
    try { await paymentApi.sandboxComplete(payment.paymentId, token); setBooking(await guestBookingApi.detail(code, token)); setPayment(null); setMessage('Thanh toán sandbox thành công.'); }
    catch (e) { setMessage(e.message); }
  }
  async function cancel() {
    try { setBooking(await guestBookingApi.cancelHold(code, token)); setMessage('Đã hủy phiếu giữ sân.'); }
    catch (e) { setMessage(e.message); }
  }
  async function requestCancellation(event) {
    event.preventDefault();
    try { await guestBookingApi.requestCancel(code, token, reason); setMessage('Đã gửi yêu cầu hủy cho quản lý.'); setReason(''); }
    catch (e) { setMessage(e.message); }
  }
  if (!token) return <section className="container section narrow"><h1>Không có quyền truy cập phiếu</h1><p>Phiếu chỉ mở bằng token được cấp trên trình duyệt lúc đặt. Nếu đã mất token, liên hệ quản lý để xác minh.</p></section>;
  return <section className="container section narrow"><span className="eyebrow">PHIẾU ĐẶT SÂN</span><h1>{code}</h1>{message && <p className="inline-message">{message}</p>}{booking && <div className="detail-card"><div className="detail-top"><h2>{booking.courtName}</h2><span className="badge">{booking.status}</span></div><div className="detail-row"><span>Khách đặt</span><b>{booking.guestName}</b></div><div className="detail-row"><span>Giờ chơi</span><b>{localTime(booking.startAt)} – {localTime(booking.endAt)}</b></div><div className="detail-row"><span>Tổng tiền sân</span><b>{money(booking.totalAmount)}</b></div><div className="detail-row"><span>Tiền cọc</span><b>{money(booking.depositRequired)}</b></div>{booking.status === 'HOLD' && <><p>Giữ sân đến {localTime(booking.holdExpiresAt)}.</p><div className="button-row"><button className="btn primary" onClick={() => startPayment('DEPOSIT')}>Thanh toán cọc</button><button className="btn outline" onClick={cancel}>Hủy phiếu chưa cọc</button></div></>}{['CONFIRMED', 'CHECKED_IN'].includes(booking.status) && <><button className="btn outline" onClick={() => startPayment('BALANCE')}>Thanh toán phần còn lại</button><form onSubmit={requestCancellation}><label>Lý do xin hủy<input required value={reason} onChange={e => setReason(e.target.value)}/></label><button className="btn text">Gửi yêu cầu hủy</button></form></>}{payment && <div className="payment-box"><b>Khoản thu #{payment.paymentId} · {money(payment.amount)}</b><p>Chế độ thử nghiệm; chưa kết nối cổng thanh toán thật.</p><button className="btn primary" onClick={completeSandbox}>Xác nhận thanh toán thử</button></div>}</div>}</section>;
}
