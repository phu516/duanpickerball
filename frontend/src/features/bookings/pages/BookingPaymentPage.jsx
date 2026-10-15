import { useCallback, useEffect, useState } from 'react';
import { navigate } from '../../../app/router';
import { bookingPaymentSuccessKey, bookingTokenKey, money } from '../../../shared/utils/format';
import { paymentApi } from '../../payments/api/paymentApi';
import PaymentPanel from '../../payments/PaymentPanel';
import { guestBookingApi } from '../api/guestBookingApi';

export default function BookingPaymentPage({ code }) {
  const token = sessionStorage.getItem(bookingTokenKey(code));
  const [booking, setBooking] = useState(null);
  const [payment, setPayment] = useState(null);
  const [modalOpen, setModalOpen] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const finishPayment = useCallback(async paidPayment => {
    const updatedBooking = await guestBookingApi.detail(code, token);
    setBooking(updatedBooking);
    if (updatedBooking.status !== 'CONFIRMED') {
      setMessage('Đã nhận giao dịch nhưng phiếu chưa được xác nhận. Vui lòng liên hệ quản lý.');
      return;
    }
    sessionStorage.setItem(bookingPaymentSuccessKey(code), String(paidPayment.paymentId));
    navigate(`/bookings/${code}`);
  }, [code, token]);

  useEffect(() => {
    if (!token) return undefined;
    let active = true;
    async function loadPayment() {
      try {
        const currentBooking = await guestBookingApi.detail(code, token);
        if (!active) return;
        setBooking(currentBooking);
        if (currentBooking.status !== 'HOLD') {
          if (currentBooking.status === 'CONFIRMED' || currentBooking.status === 'CHECKED_IN') {
            navigate(`/bookings/${code}`);
          } else {
            setMessage('Phiếu giữ sân không còn hiệu lực. Vui lòng chọn lại khung giờ.');
          }
          return;
        }
        if (new Date(currentBooking.holdExpiresAt).getTime() <= Date.now()) {
          setMessage('Đã hết thời gian giữ sân. Vui lòng chọn lại khung giờ.');
          return;
        }
        const list = await paymentApi.bookingList(code, token);
        if (!active) return;
        const existing = list.find(item => item.purpose === 'DEPOSIT' && item.status === 'PENDING')
          ?? list.find(item => item.purpose === 'DEPOSIT' && item.status === 'REVIEW_REQUIRED');
        const currentPayment = existing
          ? await paymentApi.bookingDetail(code, token, existing.paymentId)
          : await paymentApi.booking(code, token, 'DEPOSIT');
        if (!active) return;
        setPayment(currentPayment);
        setModalOpen(true);
        if (currentPayment.status === 'SUCCEEDED') await finishPayment(currentPayment);
      } catch (error) {
        if (active) setMessage(error.message);
      } finally {
        if (active) setLoading(false);
      }
    }
    loadPayment();
    return () => { active = false; };
  }, [code, token, finishPayment]);

  useEffect(() => {
    if (!token || payment?.status !== 'PENDING') return undefined;
    let active = true;
    let checking = false;
    async function refreshStatus() {
      if (checking || document.visibilityState === 'hidden') return;
      checking = true;
      try {
        const updated = await paymentApi.bookingDetail(code, token, payment.paymentId);
        if (!active) return;
        setPayment(updated);
        if (updated.status === 'SUCCEEDED') await finishPayment(updated);
        else if (updated.status === 'FAILED') setMessage('Khoản thanh toán đã đóng. Bạn có thể tạo mã QR mới nếu thời gian giữ sân còn hiệu lực.');
      } catch { /* Retry on the next status check. */ }
      finally { checking = false; }
    }
    const timer = window.setInterval(refreshStatus, 3000);
    window.addEventListener('focus', refreshStatus);
    document.addEventListener('visibilitychange', refreshStatus);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.removeEventListener('focus', refreshStatus);
      document.removeEventListener('visibilitychange', refreshStatus);
    };
  }, [code, token, payment?.paymentId, payment?.status, finishPayment]);

  async function retryPayment() {
    if (busy) return;
    setBusy(true);
    setMessage('');
    try {
      const created = await paymentApi.booking(code, token, 'DEPOSIT');
      setPayment(created);
      setModalOpen(true);
      if (created.status === 'SUCCEEDED') await finishPayment(created);
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }

  async function completeSandbox() {
    try {
      const updated = await paymentApi.sandboxComplete(payment.paymentId, token);
      setPayment(updated);
      if (updated.status === 'SUCCEEDED') await finishPayment(updated);
    } catch (error) { setMessage(error.message); }
  }

  if (!token) return <section className="container section narrow"><h1>Không có quyền truy cập thanh toán</h1><p>Vui lòng mở lại trang trên trình duyệt đã đặt sân.</p></section>;

  return <section className="container section narrow">
    <span className="eyebrow">THANH TOÁN CỌC</span>
    <h1>Hoàn tất thanh toán để đặt sân</h1>
    <p>Phiếu đặt sân sẽ được xác nhận sau khi hệ thống nhận tiền cọc.</p>
    {message && <p className="inline-message error" role="alert">{message}</p>}
    {loading && <p className="inline-message" role="status">Đang chuẩn bị mã QR thanh toán...</p>}
    {booking?.status === 'HOLD' && <div className="detail-card">
      <div className="detail-row"><span>Sân</span><b>{booking.courtName}</b></div>
      <div className="detail-row"><span>Tiền cọc cần thanh toán</span><b>{money(booking.depositRequired)}</b></div>
      {payment?.status === 'PENDING' && !modalOpen && <button type="button" className="btn primary" onClick={() => setModalOpen(true)}>Mở lại mã QR cọc</button>}
      {payment?.status === 'FAILED' && <button type="button" className="btn primary" disabled={busy} onClick={retryPayment}>{busy ? 'Đang tạo mã QR…' : 'Tạo mã QR mới'}</button>}
      {!payment && !loading && new Date(booking.holdExpiresAt).getTime() > Date.now() && <button type="button" className="btn primary" disabled={busy} onClick={retryPayment}>{busy ? 'Đang tạo mã QR…' : 'Thử tạo mã QR'}</button>}
    </div>}
    <PaymentPanel payment={payment} onSandboxComplete={completeSandbox} modal isOpen={modalOpen} onClose={() => setModalOpen(false)}/>
  </section>;
}
