import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { MapPin, X } from 'lucide-react';
import { api } from '../../../shared/api/apiClient';
import { localDate, money } from '../../../shared/utils/format';
import { guestBookingApi } from '../../bookings/api/guestBookingApi';
import { bookingTimes } from '../../bookings/bookingTimes';
import BookingFormPage from '../../bookings/pages/BookingFormPage';
import './CourtsPage.css';

function getDefaultBookingTimes() {
  const timeParts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(new Date());
  const hour = Number(timeParts.find(part => part.type === 'hour')?.value ?? 6);
  const minute = Number(timeParts.find(part => part.type === 'minute')?.value ?? 0);
  const roundedMinutes = Math.ceil((hour * 60 + minute) / 30) * 30;
  const openingMinutes = 6 * 60;
  const latestStartMinutes = 21 * 60;
  const startMinutes = roundedMinutes < openingMinutes || roundedMinutes > latestStartMinutes
    ? openingMinutes
    : roundedMinutes;
  const startIndex = (startMinutes - openingMinutes) / 30;

  return {
    from: bookingTimes[startIndex],
    to: bookingTimes[startIndex + 4]
  };
}

function BookingDialog({ court, date, from, to, onClose }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = dialogRef.current?.querySelectorAll(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
      );
      if (!focusable?.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      } else if (!dialogRef.current?.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, [onClose]);

  return createPortal(
    <div className="booking-overlay" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="booking-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-dialog-title" ref={dialogRef}>
        <div className="booking-dialog-top">
          <span>Đặt sân · {court.name}</span>
          <button type="button" className="booking-dialog-close" aria-label="Đóng biểu mẫu đặt sân" onClick={onClose} ref={closeRef}><X size={20}/></button>
        </div>
        <div className="booking-dialog-scroll"><BookingFormPage key={court.id} court={court} initialDate={date} initialFrom={from} initialTo={to}/></div>
      </div>
    </div>,
    document.body
  );
}

export default function CourtsPage() {
  const [courts, setCourts] = useState([]);
  const [court, setCourt] = useState(null);
  const [error, setError] = useState('');
  const [date, setDate] = useState(() => localDate(Date.now() + 86_400_000));
  const [defaultBookingTimes] = useState(getDefaultBookingTimes);
  const [from, setFrom] = useState(defaultBookingTimes.from);
  const [to, setTo] = useState(defaultBookingTimes.to);
  const [availability, setAvailability] = useState([]);
  const [availabilityLoading, setAvailabilityLoading] = useState(true);
  const [availabilityError, setAvailabilityError] = useState('');
  const [availabilityRefresh, setAvailabilityRefresh] = useState(0);
  const closeDialog = useCallback(() => {
    setCourt(null);
    setAvailabilityRefresh(value => value + 1);
  }, []);

  useEffect(() => { api('/courts').then(setCourts).catch(e => setError(e.message)); }, []);

  useEffect(() => {
    let active = true;
    setAvailability([]);
    setAvailabilityLoading(true);
    setAvailabilityError('');
    guestBookingApi.availability(date).then(slots => {
      if (active) setAvailability(slots);
    }).catch(e => {
      if (active) setAvailabilityError(e.message);
    }).finally(() => {
      if (active) setAvailabilityLoading(false);
    });
    return () => { active = false; };
  }, [date, availabilityRefresh]);

  useEffect(() => {
    function refreshWhenFocused() {
      api('/courts').then(setCourts).catch(e => setError(e.message));
      setAvailabilityRefresh(value => value + 1);
    }
    window.addEventListener('focus', refreshWhenFocused);
    return () => window.removeEventListener('focus', refreshWhenFocused);
  }, []);

  function changeFrom(value) {
    setFrom(value);
    const earliestEnd = bookingTimes.indexOf(value) + 4;
    if (bookingTimes.indexOf(to) < earliestEnd) setTo(bookingTimes[earliestEnd]);
  }

  const startAt = new Date(`${date}T${from}:00+07:00`).getTime();
  const endAt = new Date(`${date}T${to}:00+07:00`).getTime();

  return <div className="container section"><div className="eyebrow">PICKLEZONE COURTS</div><h1>Chọn sân cho trận tiếp theo</h1><p>Mỗi phiếu đặt một sân; bạn có thể tạo bao nhiêu phiếu tùy thích.</p>
    {error && <p className="inline-message error">{error}</p>}
    <div className="court-availability-controls">
      <div><h2>Kiểm tra sân còn trống</h2><p>Chọn ngày và khung giờ muốn chơi trước khi chọn sân.</p></div>
      <div className="court-availability-fields">
        <label>Ngày chơi<input type="date" min={localDate(new Date())} value={date} onChange={event => setDate(event.target.value)}/></label>
        <label>Bắt đầu<select value={from} onChange={event => changeFrom(event.target.value)}>{bookingTimes.slice(0, -4).map(time => <option key={time}>{time}</option>)}</select></label>
        <label>Kết thúc<select value={to} onChange={event => setTo(event.target.value)}>{bookingTimes.slice(bookingTimes.indexOf(from) + 4).map(time => <option key={time}>{time}</option>)}</select></label>
      </div>
    </div>
    {availabilityLoading && <p className="inline-message" role="status">Đang kiểm tra lịch sân...</p>}
    {availabilityError && <p className="inline-message error" role="alert">Không tải được lịch sân: {availabilityError} <button type="button" className="btn text" onClick={() => setAvailabilityRefresh(value => value + 1)}>Thử lại</button></p>}
    <div className="court-grid">{courts.map(item => {
      const overlaps = availability.filter(slot => slot.courtId === item.id && new Date(slot.startAt).getTime() < endAt && new Date(slot.endAt).getTime() > startAt);
      const status = overlaps.some(slot => slot.status === 'BOOKED') ? 'BOOKED' : overlaps.some(slot => slot.status === 'BLOCKED') ? 'BLOCKED' : null;
      return <button key={item.id} type="button" className={`court-card${status ? ' court-card--occupied' : ''}`} disabled={availabilityLoading || !!availabilityError || !!status} onClick={() => setCourt(item)}>
        <div className="court-card-title"><b className="court-code">{item.code}</b><div><h2>{item.name}</h2><p><MapPin size={16}/> {item.indoor ? 'Trong nhà' : 'Ngoài trời'} · {item.surface}</p></div></div>
        <div className="mini-court-art"><div className="mini-court-lines"><i/><i/><i/><i/></div><span>🟡</span></div>
        <div className="court-card-foot"><strong>Từ {money(item.startingPrice)} / giờ</strong><span className="court-select">{status === 'BOOKED' ? 'Đã đặt' : status === 'BLOCKED' ? 'Tạm khóa' : 'Chọn sân →'}</span></div>
        {status && <p className="court-occupied-message">{status === 'BOOKED' ? 'Khung giờ này đã có phiếu đặt.' : 'Sân tạm khóa trong khung giờ đã chọn.'}</p>}
      </button>;
    })}</div>
    {court && <BookingDialog court={court} date={date} from={from} to={to} onClose={closeDialog}/>}
  </div>;
}
