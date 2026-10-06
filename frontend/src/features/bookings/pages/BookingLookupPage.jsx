import { useState } from 'react';
import { navigate } from '../../../app/router';
import { bookingTokenKey } from '../../../shared/utils/format';

export default function BookingLookupPage() {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  function lookup(event) {
    event.preventDefault();
    if (sessionStorage.getItem(bookingTokenKey(code.trim()))) navigate(`/bookings/${code.trim()}`);
    else setError('Không có quyền truy cập phiếu trên trình duyệt này. Vui lòng liên hệ quản lý để xác minh.');
  }
  return <section className="container section narrow"><span className="eyebrow">TRA CỨU</span><h1>Xem lại phiếu đặt sân</h1><p>Mã phiếu chỉ dùng kèm token riêng trên trình duyệt đã đặt. Số điện thoại không đủ để mở phiếu.</p><form className="lookup-form" onSubmit={lookup}><input required value={code} onChange={e => setCode(e.target.value.toUpperCase())} placeholder="Nhập mã phiếu"/><button className="btn primary">Tra cứu</button></form>{error && <p className="inline-message error">{error}</p>}</section>;
}
