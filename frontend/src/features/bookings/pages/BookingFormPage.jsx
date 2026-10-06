import { useRef, useState } from 'react';
import { guestBookingApi } from '../api/guestBookingApi';
import { bookingTimes } from '../bookingTimes';
import { bookingTokenKey, localDate } from '../../../shared/utils/format';
import { navigate } from '../../../app/router';
import './BookingFormPage.css';

const offsetDate = (date, time) => `${date}T${time}:00+07:00`;

export default function BookingFormPage({ court, initialDate, initialFrom = '17:00', initialTo = '19:00' }) {
  const [date, setDate] = useState(() => initialDate ?? localDate(Date.now() + 86_400_000));
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [form, setForm] = useState({ name: '', phone: '', email: '', note: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const inputRefs = useRef({});
  const requestKey = useRef(crypto.randomUUID());

  function clearErrors(...fields) {
    setFieldErrors(current => {
      const next = { ...current };
      fields.forEach(field => delete next[field]);
      return next;
    });
    setError('');
  }

  function updateForm(field, value) {
    setForm(current => ({ ...current, [field]: value }));
    clearErrors(field);
  }

  function validate() {
    const errors = {};
    if (!date) errors.date = 'Vui lòng chọn ngày chơi.';
    if (!from) errors.from = 'Vui lòng chọn giờ bắt đầu.';
    if (!to) errors.to = 'Vui lòng chọn giờ kết thúc.';
    if (date && from && to) {
      const start = new Date(offsetDate(date, from)).getTime();
      const end = new Date(offsetDate(date, to)).getTime();
      if (!Number.isFinite(start) || !Number.isFinite(end)) errors.date = 'Ngày hoặc giờ chơi không hợp lệ.';
      else if (start <= Date.now()) errors.from = 'Giờ bắt đầu phải ở tương lai.';
      else if (end - start < 120 * 60 * 1000) errors.to = 'Thời lượng đặt sân tối thiểu là 2 giờ.';
    }
    if (form.name.trim().length < 2) errors.name = 'Vui lòng nhập họ tên ít nhất 2 ký tự.';
    if (!/^[0-9+() .-]{9,16}$/.test(form.phone.trim())) errors.phone = 'Vui lòng nhập số điện thoại hợp lệ (9–16 ký tự).';
    if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Email không hợp lệ.';
    return errors;
  }

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length) {
      setError('Vui lòng kiểm tra các trường được đánh dấu.');
      inputRefs.current[Object.keys(errors)[0]]?.focus();
      return;
    }
    setBusy(true);
    setError('');
    try {
      const booking = await guestBookingApi.create({ courtId: court.id,
        startAt: offsetDate(date, from), endAt: offsetDate(date, to),
        name: form.name.trim(), phone: form.phone.trim(), email: form.email.trim() || null, note: form.note.trim() || null }, requestKey.current);
      sessionStorage.setItem(bookingTokenKey(booking.bookingCode), booking.guestAccessToken);
      navigate(`/bookings/${booking.bookingCode}`);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <section className="booking-panel"><div className="panel-heading"><span className="eyebrow">ĐẶT {court.name.toUpperCase()}</span><h2 id="booking-dialog-title">Chọn lịch và điền thông tin</h2><p>Giữ sân 30 phút để thanh toán cọc. Thời lượng tối thiểu 2 giờ.</p></div>
    <form className="form-grid booking-form" onSubmit={submit} noValidate>
      <div className="schedule-fields">
        <label>Ngày chơi<input required type="date" min={localDate(new Date())} value={date} aria-invalid={!!fieldErrors.date} aria-describedby={fieldErrors.date ? 'booking-date-error' : undefined} ref={node => { inputRefs.current.date = node; }} onChange={e => { setDate(e.target.value); clearErrors('date', 'from', 'to'); }}/>{fieldErrors.date && <small className="booking-field-error" id="booking-date-error">{fieldErrors.date}</small>}</label>
        <label>Bắt đầu<select required value={from} aria-invalid={!!fieldErrors.from} aria-describedby={fieldErrors.from ? 'booking-from-error' : undefined} ref={node => { inputRefs.current.from = node; }} onChange={e => { setFrom(e.target.value); clearErrors('from', 'to'); }}><option value="">Chọn giờ</option>{bookingTimes.slice(0, -4).map(time => <option key={time}>{time}</option>)}</select>{fieldErrors.from && <small className="booking-field-error" id="booking-from-error">{fieldErrors.from}</small>}</label>
        <label>Kết thúc<select required value={to} aria-invalid={!!fieldErrors.to} aria-describedby={fieldErrors.to ? 'booking-to-error' : undefined} ref={node => { inputRefs.current.to = node; }} onChange={e => { setTo(e.target.value); clearErrors('to'); }}><option value="">Chọn giờ</option>{bookingTimes.map(time => <option key={time}>{time}</option>)}</select>{fieldErrors.to && <small className="booking-field-error" id="booking-to-error">{fieldErrors.to}</small>}</label>
      </div>
      <label>Họ và tên *<input required minLength="2" value={form.name} aria-invalid={!!fieldErrors.name} aria-describedby={fieldErrors.name ? 'booking-name-error' : undefined} ref={node => { inputRefs.current.name = node; }} onChange={e => updateForm('name', e.target.value)}/>{fieldErrors.name && <small className="booking-field-error" id="booking-name-error">{fieldErrors.name}</small>}</label>
      <label>Số điện thoại *<input required pattern="[0-9+() .-]{9,16}" value={form.phone} aria-invalid={!!fieldErrors.phone} aria-describedby={fieldErrors.phone ? 'booking-phone-error' : undefined} ref={node => { inputRefs.current.phone = node; }} onChange={e => updateForm('phone', e.target.value)}/>{fieldErrors.phone && <small className="booking-field-error" id="booking-phone-error">{fieldErrors.phone}</small>}</label>
      <label>Email (không bắt buộc)<input type="email" value={form.email} aria-invalid={!!fieldErrors.email} aria-describedby={fieldErrors.email ? 'booking-email-error' : undefined} ref={node => { inputRefs.current.email = node; }} onChange={e => updateForm('email', e.target.value)}/>{fieldErrors.email && <small className="booking-field-error" id="booking-email-error">{fieldErrors.email}</small>}</label>
      <label>Ghi chú<textarea maxLength="1000" value={form.note} onChange={e => updateForm('note', e.target.value)}/></label>
      {error && <p className="inline-message error" role="alert">{error}</p>}
      <div className="form-actions"><p>Khách đến muộn quá 30 phút sau giờ bắt đầu có thể bị ghi nhận không đến và mất cọc.</p><button className="btn primary" disabled={busy}>{busy ? 'Đang tạo phiếu…' : 'Tạo phiếu giữ sân'}</button></div>
    </form>
  </section>;
}
