import { useEffect, useMemo, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { api } from '../../../shared/api/apiClient';
import { adminBookingApi } from '../../bookings/api/adminBookingApi';
import { localDate, localTime, money } from '../../../shared/utils/format';
import './CourtManagementPanel.css';

const weekdayNames = ['', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy', 'Chủ nhật'];
const mapTimes = ['06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '17:00', '18:00', '19:00', '20:00', '21:00', '23:00'];
const emptyCourtDraft = { code: '', name: '', surface: 'Acrylic', indoor: false, description: '' };
const tomorrow = () => localDate(Date.now() + 86_400_000);

export default function CourtManagementPanel({ courts, onChanged }) {
  const [selectedCourt, setSelectedCourt] = useState(null);
  const [bookings, setBookings] = useState([]);
  const [priceRules, setPriceRules] = useState([]);
  const [availability, setAvailability] = useState([]);
  const [availabilityLoading, setAvailabilityLoading] = useState(true);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [mapDate, setMapDate] = useState(() => localDate(Date.now() + 86_400_000));
  const [mapFrom, setMapFrom] = useState('17:00');
  const [mapTo, setMapTo] = useState('19:00');
  const [bookingFilter, setBookingFilter] = useState('upcoming');
  const [courtDraft, setCourtDraft] = useState(emptyCourtDraft);
  const [newCourtPrice, setNewCourtPrice] = useState(120000);
  const [priceEdit, setPriceEdit] = useState(null);
  const courtDialogRef = useRef(null);

  useEffect(() => {
    let active = true;
    setAvailabilityLoading(true);
    api(`/court-availability?date=${mapDate}`).then(result => {
      if (active) setAvailability(result);
    }).catch(error => {
      if (active) setMessage(error.message);
    }).finally(() => {
      if (active) setAvailabilityLoading(false);
    });
    return () => { active = false; };
  }, [mapDate]);

  useEffect(() => {
    if (!selectedCourt) return undefined;
    const previouslyFocused = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    courtDialogRef.current?.focus();
    function closeOnEscape(event) {
      if (event.key === 'Escape') setSelectedCourt(null);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => {
      window.removeEventListener('keydown', closeOnEscape);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus?.();
    };
  }, [selectedCourt]);

  const selectedBookings = useMemo(() => {
    if (!selectedCourt) return [];
    const result = bookings.filter(item => item.courtId === selectedCourt.id);
    if (bookingFilter === 'all') return result;
    const now = Date.now();
    return result.filter(item => new Date(item.endAt).getTime() >= now
      && ['HOLD', 'CONFIRMED', 'CHECKED_IN'].includes(item.status));
  }, [bookings, bookingFilter, selectedCourt]);

  const mapEndTimes = mapTimes.filter(time => time > mapFrom);

  function changeMapStart(time) {
    setMapFrom(time);
    if (mapTo <= time) setMapTo(mapTimes.find(candidate => candidate > time) || time);
  }

  function courtStatus(court) {
    if (court.locked) return 'LOCKED';
    const from = new Date(`${mapDate}T${mapFrom}:00+07:00`);
    const to = new Date(`${mapDate}T${mapTo}:00+07:00`);
    const overlapping = availability.filter(slot => slot.courtId === court.id
      && new Date(slot.startAt) < to && new Date(slot.endAt) > from);
    if (overlapping.some(slot => slot.status === 'BOOKED')) return 'BOOKED';
    if (overlapping.some(slot => slot.status === 'BLOCKED')) return 'BLOCKED';
    return 'FREE';
  }

  async function reloadPanelData(courtId = selectedCourt?.id) {
    const tasks = [adminBookingApi.list(), api(`/court-availability?date=${mapDate}`)];
    if (courtId) tasks.push(api(`/admin/courts/${courtId}/price-rules`));
    const [freshBookings, freshAvailability, freshRules] = await Promise.all(tasks);
    setBookings(freshBookings);
    setAvailability(freshAvailability);
    if (freshRules) setPriceRules(freshRules);
  }

  async function openCourt(item) {
    setSelectedCourt(item);
    setPriceEdit(null);
    setDetailsLoading(true);
    setMessage('');
    try {
      const [freshBookings, freshRules] = await Promise.all([
        adminBookingApi.list(), api(`/admin/courts/${item.id}/price-rules`),
      ]);
      setBookings(freshBookings);
      setPriceRules(freshRules);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setDetailsLoading(false);
    }
  }

  async function run(action, afterSuccess) {
    setMessage('');
    try {
      await action();
      if (afterSuccess) afterSuccess();
      await reloadPanelData();
      await onChanged();
      setMessage('Đã cập nhật.');
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function addCourt(event) {
    event.preventDefault();
    await run(async () => {
      const court = await api('/admin/courts', { method: 'POST', body: courtDraft });
      for (let day = 1; day <= 7; day++) await api(`/admin/courts/${court.id}/price-rules`, {
        method: 'POST', body: { dayOfWeek: day, startTime: '06:00:00', endTime: '23:00:00',
          hourlyPrice: Number(newCourtPrice), validFrom: localDate(new Date()) },
      });
    }, () => setCourtDraft(emptyCourtDraft));
  }

  function startPriceEdit(rule) {
    const today = localDate(new Date());
    setPriceEdit({
      rule,
      hourlyPrice: rule.hourlyPrice,
      effectiveFrom: rule.validFrom > today ? rule.validFrom : today,
    });
  }

  async function changePrice(event) {
    event.preventDefault();
    if (!selectedCourt || !priceEdit) return;
    await run(() => api(`/admin/courts/${selectedCourt.id}/price-rules/${priceEdit.rule.id}/change`, {
      method: 'POST', body: { hourlyPrice: Number(priceEdit.hourlyPrice), effectiveFrom: priceEdit.effectiveFrom },
    }), () => setPriceEdit(null));
  }

  async function toggleCourtLock() {
    if (!selectedCourt) return;
    const action = selectedCourt.locked ? 'unlock' : 'lock';
    await run(async () => {
      const updatedCourt = await api(`/admin/courts/${selectedCourt.id}/${action}`, { method: 'POST' });
      setSelectedCourt(updatedCourt);
    });
  }

  return <>
    {message && !selectedCourt && <p className="inline-message" role="status">{message}</p>}
    <div className="table-card">
      <div className="section-heading">
        <div><h2>Sơ đồ {courts.length} sân</h2><p>Bấm vào sân để xem người đặt, lịch sử, giá và trạng thái khóa.</p></div>
        <div className="map-controls">
          <label>Ngày<input aria-label="Ngày xem sân" type="date" value={mapDate} onChange={event => setMapDate(event.target.value)}/></label>
          <label>Từ<select aria-label="Giờ bắt đầu" value={mapFrom} onChange={event => changeMapStart(event.target.value)}>{mapTimes.slice(0, -1).map(time => <option key={time}>{time}</option>)}</select></label>
          <label>Đến<select aria-label="Giờ kết thúc" value={mapTo} onChange={event => setMapTo(event.target.value)}>{mapEndTimes.map(time => <option key={time}>{time}</option>)}</select></label>
        </div>
      </div>
      {availabilityLoading && <p className="inline-message" role="status">Đang tải lịch sân...</p>}
      <div className="court-grid">{courts.map(item => {
        const status = courtStatus(item);
        const selected = selectedCourt?.id === item.id;
        const statusLabel = status === 'FREE' ? 'Còn trống'
          : status === 'BOOKED' ? 'Đã đặt'
            : status === 'LOCKED' ? 'Đang khóa' : 'Đã khóa theo giờ';
        return <button type="button" key={item.id} className={`court-card admin-court-picker${selected ? ' selected' : ''}`} onClick={() => openCourt(item)}>
          <div className="court-card-title"><b className="court-code">{item.code}</b><div><h2>{item.name}</h2><p>{item.surface} · {item.indoor ? 'Trong nhà' : 'Ngoài trời'}</p></div><span className={`court-status${status === 'FREE' ? '' : status === 'LOCKED' ? ' locked' : ' busy'}`}>{statusLabel}</span></div>
          <div className={`mini-court-art${status === 'FREE' ? '' : status === 'LOCKED' ? ' busy locked' : ' busy'}`}><div className="mini-court-lines"><i/><i/><i/><i/></div><span>🟡</span></div>
          <div className="court-card-foot"><strong>{money(item.startingPrice)} / giờ</strong><span className="court-select">{selected ? 'Đang xem' : 'Xem chi tiết →'}</span></div>
        </button>;
      })}</div>
    </div>

    {selectedCourt && <div className="court-modal-backdrop" onMouseDown={event => {
      if (event.target === event.currentTarget) setSelectedCourt(null);
    }}>
      <section ref={courtDialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="court-detail-title" className="table-card court-detail-panel">
      <div className="section-heading court-detail-heading"><div><span className="eyebrow">CHI TIẾT SÂN</span><h2 id="court-detail-title">{selectedCourt.code} · {selectedCourt.name}</h2></div><button type="button" className="court-modal-close" aria-label="Đóng cửa sổ nổi" onClick={() => setSelectedCourt(null)}><X size={19} strokeWidth={2.25} aria-hidden="true"/></button></div>
      {message && <p className="inline-message error" role="alert">{message}</p>}
      {detailsLoading ? <p role="status">Đang tải chi tiết sân...</p> : <>
        <div className="court-detail-section">
          <div className="section-heading"><div><h3>Lịch đặt sân</h3><p>Thông tin khách, ngày giờ và trạng thái phiếu.</p></div><div className="court-detail-filters"><button type="button" className={bookingFilter === 'upcoming' ? 'active' : ''} onClick={() => setBookingFilter('upcoming')}>Sắp tới</button><button type="button" className={bookingFilter === 'all' ? 'active' : ''} onClick={() => setBookingFilter('all')}>Tất cả</button></div></div>
          {selectedBookings.length === 0 ? <div className="empty-card">{bookingFilter === 'upcoming' ? 'Sân này chưa có lượt đặt sắp tới.' : 'Sân này chưa có phiếu đặt nào.'}</div> : <div className="table-scroll"><table><thead><tr><th>Mã phiếu</th><th>Khách đặt</th><th>Ngày, giờ bắt đầu</th><th>Giờ kết thúc</th><th>Trạng thái</th></tr></thead><tbody>{selectedBookings.slice().sort((a, b) => new Date(a.startAt) - new Date(b.startAt)).map(item => <tr key={item.id}><td><b>{item.bookingCode}</b></td><td>{item.guestName}<small>{item.guestPhone}</small></td><td>{localTime(item.startAt)}</td><td>{new Date(item.endAt).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' })}</td><td><span className="badge">{item.status}</span></td></tr>)}</tbody></table></div>}
        </div>

        <div className="court-detail-section">
          <div className="section-heading"><div><h3>Giá theo ngày và khung giờ</h3><p>Đổi giá từ ngày hiệu lực; giá của các phiếu đã tạo được giữ nguyên.</p></div></div>
          {priceRules.length === 0 ? <div className="empty-card">Sân này chưa có quy tắc giá.</div> : <div className="table-scroll"><table><thead><tr><th>Ngày</th><th>Khung giờ</th><th>Giá/giờ</th><th>Hiệu lực</th><th></th></tr></thead><tbody>{priceRules.map(rule => <tr key={rule.id}><td>{weekdayNames[rule.dayOfWeek]}</td><td>{String(rule.startTime).slice(0, 5)}–{String(rule.endTime).slice(0, 5)}</td><td>{money(rule.hourlyPrice)}</td><td>{rule.validFrom}{rule.validTo ? ` đến ${rule.validTo}` : ' trở đi'}</td><td><button type="button" className="court-action-button" onClick={() => startPriceEdit(rule)} disabled={rule.validTo && rule.validTo < localDate(new Date())}>Đổi giá</button></td></tr>)}</tbody></table></div>}
          {priceEdit && <form className="court-inline-form" onSubmit={changePrice}><h4>Đổi giá · {weekdayNames[priceEdit.rule.dayOfWeek]} {String(priceEdit.rule.startTime).slice(0, 5)}–{String(priceEdit.rule.endTime).slice(0, 5)}</h4><label>Giá mới/giờ<input required type="number" min="1" value={priceEdit.hourlyPrice} onChange={event => setPriceEdit({ ...priceEdit, hourlyPrice: event.target.value })}/></label><label>Áp dụng từ ngày<input required type="date" min={priceEdit.rule.validFrom} max={priceEdit.rule.validTo || undefined} value={priceEdit.effectiveFrom} onChange={event => setPriceEdit({ ...priceEdit, effectiveFrom: event.target.value })}/></label><div className="button-row"><button className="btn primary">Lưu giá</button><button type="button" className="btn outline" onClick={() => setPriceEdit(null)}>Hủy</button></div></form>}
        </div>

        <div className="court-detail-section">
          <div className="section-heading"><div><h3>Khóa sân</h3><p>{selectedCourt.locked ? 'Sân đang bị khóa và khách không thể đặt sân này.' : 'Khóa sân sẽ tạm ngừng nhận mọi lượt đặt mới cho đến khi admin mở lại.'}</p></div></div>
          <div className={`court-lock-panel${selectedCourt.locked ? ' is-locked' : ''}`}>
            <span className="court-lock-state">{selectedCourt.locked ? 'Đang khóa' : 'Đang mở'}</span>
            <button type="button" className={`btn ${selectedCourt.locked ? 'outline' : 'primary'}`} onClick={toggleCourtLock}>
              {selectedCourt.locked ? 'Mở lại sân' : 'Khóa sân'}
            </button>
          </div>
        </div>
      </>}
      </section>
    </div>}

    <form className="detail-card form-grid" onSubmit={addCourt}>
      <h2>Thêm sân mới</h2>
      <label>Mã sân<input required value={courtDraft.code} onChange={event => setCourtDraft({ ...courtDraft, code: event.target.value })} placeholder="S07"/></label>
      <label>Tên sân<input required value={courtDraft.name} onChange={event => setCourtDraft({ ...courtDraft, name: event.target.value })} placeholder="Sân 7"/></label>
      <label>Giá/giờ<input required type="number" min="1" value={newCourtPrice} onChange={event => setNewCourtPrice(event.target.value)}/></label>
      <button className="btn primary">Tạo sân và giá 7 ngày</button>
    </form>
  </>;
}
