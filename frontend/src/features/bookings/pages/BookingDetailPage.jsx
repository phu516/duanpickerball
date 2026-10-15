import { useEffect, useState } from 'react';
import { navigate } from '../../../app/router';
import { guestBookingApi } from '../api/guestBookingApi';
import { paymentApi } from '../../payments/api/paymentApi';
import PaymentPanel from '../../payments/PaymentPanel';
import { bookingPaymentSuccessKey, bookingTokenKey, localTime, money } from '../../../shared/utils/format';

export default function BookingDetailPage({ code }) {
  const token = sessionStorage.getItem(bookingTokenKey(code));
  const [booking, setBooking] = useState(null);
  const [payment, setPayment] = useState(null);
  const [depositModalOpen, setDepositModalOpen] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) return undefined;
    let active = true;
    async function loadBooking() {
      try {
        const payments = await paymentApi.bookingList(code, token);
        const deposit = payments.find(item => item.purpose === 'DEPOSIT' && item.status === 'SUCCEEDED')
          ?? payments.find(item => item.purpose === 'DEPOSIT');
        const currentPayment = deposit
          ? await paymentApi.bookingDetail(code, token, deposit.paymentId)
          : null;
        const currentBooking = await guestBookingApi.detail(code, token);
        if (!active) return;
        if (currentBooking.status === 'HOLD') {
          navigate(`/booking-payments/${code}`);
          return;
        }
        setPayment(currentPayment);
        setBooking(currentBooking);
        if (currentPayment?.status === 'SUCCEEDED'
            && sessionStorage.getItem(bookingPaymentSuccessKey(code)) === String(currentPayment.paymentId)) {
          sessionStorage.removeItem(bookingPaymentSuccessKey(code));
          setDepositModalOpen(true);
        }
      } catch (error) {
        if (active) setMessage(error.message);
      }
    }
    loadBooking();
    return () => { active = false; };
  }, [code, token]);

  if (!token) return <section className="container section narrow"><h1>Không có quyền truy cập phiếu</h1><p>Phiếu chỉ mở bằng token được cấp trên trình duyệt lúc đặt. Nếu đã mất token, liên hệ quản lý để xác minh.</p></section>;

  const confirmed = booking && (payment?.status === 'SUCCEEDED'
    || ['CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW'].includes(booking.status));

  if (!booking) return <section className="container section narrow">
    <span className="eyebrow">THANH TOÁN CỌC</span>
    <h1>Đang kiểm tra trạng thái đặt sân</h1>
    {message ? <p className="inline-message error" role="alert">{message}</p>
      : <p className="inline-message" role="status">Vui lòng chờ trong giây lát...</p>}
  </section>;

  if (!confirmed) return <section className="container section narrow">
    <span className="eyebrow">THANH TOÁN CỌC</span>
    <h1>Chưa xác nhận đặt sân</h1>
    <p className="inline-message error">Phiếu chưa được xác nhận thanh toán cọc. Vui lòng liên hệ quản lý nếu bạn đã chuyển tiền.</p>
  </section>;

  return <section className="container section narrow">
    <span className="eyebrow">PHIẾU ĐẶT SÂN</span><h1>{code}</h1>
    <div className="detail-card">
      <div className="detail-top"><h2>{booking.courtName}</h2><span className="badge">{booking.status}</span></div>
      <div className="detail-row"><span>Khách đặt</span><b>{booking.guestName}</b></div>
      <div className="detail-row"><span>Giờ chơi</span><b>{localTime(booking.startAt)} – {localTime(booking.endAt)}</b></div>
      <div className="detail-row"><span>Tổng tiền sân</span><b>{money(booking.totalAmount)}</b></div>
      <div className="detail-row"><span>Tiền cọc</span><b>{money(booking.depositRequired)}</b></div>
      {booking.status === 'CONFIRMED' && <p className="booking-cancellation-contact">
        Nếu muốn hủy phiếu, vui lòng liên hệ trực tiếp admin để trao đổi.
      </p>}
      <PaymentPanel payment={payment} modal isOpen={depositModalOpen} onClose={() => setDepositModalOpen(false)}/>
    </div>
  </section>;
}
