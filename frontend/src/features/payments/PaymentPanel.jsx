import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import QRCode from 'qrcode';
import { localTime, money } from '../../shared/utils/format';
import './PaymentPanel.css';

export default function PaymentPanel({ payment, onSandboxComplete, modal = false, isOpen = false, onClose }) {
  const [qrImage, setQrImage] = useState('');
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    let active = true;
    setQrImage('');
    if (payment?.provider === 'PAYOS' && payment.qrCode) {
      QRCode.toDataURL(payment.qrCode, { width: 280, margin: 2, errorCorrectionLevel: 'M' })
        .then(image => { if (active) setQrImage(image); })
        .catch(() => { if (active) setQrImage(''); });
    }
    return () => { active = false; };
  }, [payment?.paymentId, payment?.qrCode, payment?.provider]);

  useEffect(() => {
    if (payment?.status !== 'PENDING') return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [payment?.status]);

  useEffect(() => {
    if (!modal || !isOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    function closeOnEscape(event) {
      if (event.key === 'Escape') onClose?.();
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', closeOnEscape);
    };
  }, [modal, isOpen, onClose]);

  if (!payment || modal && !isOpen) return null;
  const secondsLeft = payment.expiresAt
    ? Math.max(0, Math.ceil((new Date(payment.expiresAt).getTime() - now) / 1000))
    : null;
  const expired = secondsLeft !== null && secondsLeft === 0;

  const panel = <section className="payment-box payment-checkout" aria-live="polite">
    <div className="payment-checkout-heading"><strong>Khoản thanh toán #{payment.paymentId}</strong><b>{money(payment.amount)}</b></div>
    {payment.status === 'SUCCEEDED' && <p className="payment-success">Đã nhận thanh toán thành công.</p>}
    {payment.status === 'REVIEW_REQUIRED' && <p className="inline-message error">Đã nhận tiền nhưng giao dịch cần quản lý đối soát. Vui lòng liên hệ quản lý và cung cấp mã khoản thanh toán ở trên.</p>}
    {payment.status === 'FAILED' && <p className="inline-message error">Link thanh toán đã đóng. Nếu phiếu hoặc đơn còn hiệu lực, bạn có thể tạo khoản thanh toán mới.</p>}
    {payment.status === 'PENDING' && payment.provider === 'SANDBOX' && <>
      <p>Chế độ thử nghiệm; chưa thu tiền thật.</p>
      <button type="button" className="btn primary" onClick={onSandboxComplete}>Xác nhận thanh toán thử</button>
    </>}
    {payment.status === 'PENDING' && payment.provider === 'PAYOS' && <>
      {expired ? <p className="inline-message error">Đã hết thời gian thanh toán. Hệ thống đang kiểm tra trạng thái giao dịch.</p>
        : <p>Quét mã QR bằng ứng dụng ngân hàng. Còn {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')} để thanh toán.</p>}
      {!expired && <div className="payment-checkout-body">
        {qrImage && <img className="payment-qr" src={qrImage} alt={`Mã QR thanh toán ${money(payment.amount)}`}/>}
        <div className="payment-checkout-details">
          {payment.accountName && <p><span>Người nhận</span><b>{payment.accountName}</b></p>}
          {payment.accountNumber && <p><span>Số tài khoản</span><b>{payment.accountNumber}</b></p>}
          {payment.description && <p><span>Nội dung</span><b>{payment.description}</b></p>}
          <p><span>Số tiền</span><b>{money(payment.amount)}</b></p>
          {payment.expiresAt && <p><span>Hạn thanh toán</span><b>{localTime(payment.expiresAt)}</b></p>}
        </div>
      </div>}
      <small>Trạng thái sẽ tự cập nhật sau khi ngân hàng xử lý giao dịch.</small>
    </>}
  </section>;

  if (!modal) return panel;
  return createPortal(<div className="payment-modal-backdrop" onMouseDown={event => {
    if (event.target === event.currentTarget) onClose?.();
  }}>
    <div className={`payment-modal-dialog${payment.status === 'SUCCEEDED' ? ' is-success' : ''}`} role="dialog" aria-modal="true" aria-label={payment.status === 'SUCCEEDED' ? 'Thanh toán thành công' : 'Thanh toán tiền cọc'}>
      <button type="button" className="payment-modal-close" aria-label="Đóng cửa sổ thanh toán" onClick={onClose}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
      </button>
      {payment.status === 'SUCCEEDED' ? <section className="payment-success-view" aria-live="polite">
        <div className="payment-success-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none"><path d="m6.5 12.5 3.7 3.7 7.6-8" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.7"/></svg>
        </div>
        <span className="payment-success-eyebrow">THANH TOÁN HOÀN TẤT</span>
        <h2>Thanh toán thành công!</h2>
        <p className="payment-success-description">Khoản thanh toán của bạn đã được ghi nhận.</p>
        <div className="payment-success-amount">{money(payment.amount)}</div>
        <div className="payment-success-reference"><span>Mã giao dịch</span><strong>#{payment.paymentId}</strong></div>
        <button type="button" className="btn primary payment-success-done" onClick={onClose}>Hoàn tất</button>
      </section> : panel}
    </div>
  </div>, document.body);
}
