import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../../shared/api/apiClient';
import DateTimePicker from '../../../shared/components/DateTimePicker';
import HeroBannerEditor from './HeroBannerEditor';
import './TournamentAdmin.css';

const localToday = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const sections = [
  ['management', 'Quản lý giải đấu'],
  ['players', 'Danh sách người chơi'],
];
const statusText = { disabled: 'Đang tắt', scheduled: 'Sắp diễn ra', ongoing: 'Đang diễn ra', completed: 'Đã kết thúc', cancelled: 'Đã hủy' };
const displayDate = date => date ? date.split('-').reverse().join('/') : '—';
const displayDateTime = (date, time) => `${displayDate(date)}${time ? ` - ${time.slice(0, 5)}` : ''}`;
const toPickerValue = (date, time, fallback) => date ? `${date}T${(time || fallback).slice(0, 5)}` : '';
const hasScheduleTimes = schedule => typeof schedule?.startsTime === 'string' && typeof schedule?.endsTime === 'string';
const outdatedServerMessage = 'Máy chủ chưa trả về giờ khai mạc và giờ kết thúc. Vui lòng khởi động lại backend để áp dụng bản cập nhật, rồi lưu lại lịch giải đấu.';

function TournamentSchedule({ onSessionExpired }) {
  const [form, setForm] = useState({ startsOn: '', startsTime: '09:00', endsOn: '', endsTime: '18:00' });
  const [saved, setSaved] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const cancelDialogRef = useRef(null);
  const cancelButtonRef = useRef(null);
  const saveButtonRef = useRef(null);
  const savingRef = useRef(false);
  savingRef.current = saving;

  useEffect(() => {
    api('/admin/tournament').then(data => {
      setSaved(data);
      if (data) {
        setForm({ startsOn: data.startsOn, startsTime: data.startsTime?.slice(0, 5) || '09:00', endsOn: data.endsOn, endsTime: data.endsTime?.slice(0, 5) || '18:00' });
        if (!hasScheduleTimes(data)) setError(outdatedServerMessage);
      }
    }).catch(err => {
      if (err.status === 401) onSessionExpired();
      else setError(err.message);
    });
  }, []);

  useEffect(() => {
    if (!cancelOpen) return undefined;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cancelButtonRef.current?.focus();

    function onDialogKeyDown(event) {
      if (event.key === 'Escape' && !savingRef.current) {
        setCancelOpen(false);
      }
      if (event.key !== 'Tab') return;
      const focusable = [...(cancelDialogRef.current?.querySelectorAll('button:not(:disabled)') ?? [])];
      if (focusable.length === 0) {
        event.preventDefault();
        cancelDialogRef.current?.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!cancelDialogRef.current?.contains(document.activeElement)) {
        event.preventDefault(); first.focus();
        return;
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    }

    document.addEventListener('keydown', onDialogKeyDown);
    return () => {
      document.removeEventListener('keydown', onDialogKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
      else saveButtonRef.current?.focus();
    };
  }, [cancelOpen]);

  async function submit(event) {
    event.preventDefault(); setError(''); setMessage('');
    if (!form.startsOn || !form.endsOn) {
      setError('Vui lòng chọn ngày khai mạc và ngày kết thúc.');
      return;
    }
    if (`${form.startsOn}T${form.startsTime}` < `${localToday()}T${new Date().toTimeString().slice(0, 5)}` || `${form.endsOn}T${form.endsTime}` <= `${form.startsOn}T${form.startsTime}`) {
      setError('Giờ khai mạc phải ở hiện tại hoặc tương lai và giờ kết thúc phải sau giờ khai mạc.');
      return;
    }
    setSaving(true);
    try {
      const result = await api('/admin/tournament', { method: 'PUT', body: form });
      setSaved(result);
      if (!hasScheduleTimes(result)) {
        setError(outdatedServerMessage);
        return;
      }
      setForm({ startsOn: result.startsOn, startsTime: result.startsTime.slice(0, 5), endsOn: result.endsOn, endsTime: result.endsTime.slice(0, 5) });
      setMessage('Đã lưu lịch giải đấu.');
    } catch (err) {
      if (err.status === 401) onSessionExpired();
      else setError(err.message);
    }
    finally { setSaving(false); }
  }

  async function cancelTournament() {
    setCancelError(''); setError(''); setMessage(''); setSaving(true);
    cancelDialogRef.current?.focus();
    try {
      const result = await api('/admin/tournament/cancel', { method: 'POST' });
      setSaved(result);
      setCancelOpen(false);
      setMessage('Đã hủy giải đấu. Đăng ký mới đã đóng và sân đã được mở lại.');
    } catch (err) {
      if (err.status === 401) { setCancelOpen(false); onSessionExpired(); }
      else setCancelError(err.message);
    }
    finally { setSaving(false); }
  }

  return <section className="detail-card tournament-schedule">
    <h2>Lịch tổ chức giải đấu</h2>
    <p>Lưu lịch sẽ khóa toàn bộ sân từ giờ khai mạc đến giờ kết thúc. Sân tự mở lại sau khi giải kết thúc.</p>
    {saved && <p className={`tournament-state ${saved.status}`}>Trạng thái: <b>{statusText[saved.status] || 'Chưa xác định'}</b></p>}
    <form className="tournament-schedule-form" onSubmit={submit}>
      <DateTimePicker id="tournament-starts-on" label="Ngày giờ khai mạc" required minDate={localToday()} value={toPickerValue(form.startsOn, form.startsTime, '09:00')} onChange={value => setForm(current => ({ ...current, startsOn: value.slice(0, 10), startsTime: value.slice(11, 16), endsOn: current.endsOn && current.endsOn < value.slice(0, 10) ? '' : current.endsOn }))}/>
      <DateTimePicker id="tournament-ends-on" label="Ngày giờ kết thúc" required defaultTime="18:00" minDate={form.startsOn || localToday()} value={toPickerValue(form.endsOn, form.endsTime, '18:00')} onChange={value => setForm(current => ({ ...current, endsOn: value.slice(0, 10), endsTime: value.slice(11, 16) }))}/>
      {error && <p className="inline-message error" role="alert">{error}</p>}
      {message && <p className="inline-message" role="status">{message}</p>}
      <button ref={saveButtonRef} className="btn primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu lịch giải đấu'}</button>
      {saved?.enabled && <button className="btn outline tournament-cancel-button" type="button" disabled={saving} onClick={() => { setCancelError(''); setCancelOpen(true); }}>Hủy giải đấu</button>}
    </form>
    {cancelOpen && createPortal(<div className="tournament-cancel-backdrop" onMouseDown={event => {
      if (event.target === event.currentTarget && !saving) setCancelOpen(false);
    }}>
      <section ref={cancelDialogRef} tabIndex={-1} className="tournament-cancel-dialog" role="alertdialog" aria-modal="true" aria-labelledby="tournament-cancel-title" aria-describedby="tournament-cancel-description">
        <div className="tournament-cancel-heading">
          <span className="tournament-cancel-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm5 8 4 4m0-4-4 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg></span>
          <div><span className="eyebrow">XÁC NHẬN THAY ĐỔI</span><h2 id="tournament-cancel-title">Hủy giải đấu này?</h2></div>
          <button type="button" className="tournament-cancel-close" aria-label="Đóng hộp thoại" disabled={saving} onClick={() => setCancelOpen(false)}>×</button>
        </div>
        <p id="tournament-cancel-description" className="tournament-cancel-description">Lịch giải từ <strong>{displayDateTime(saved?.startsOn, saved?.startsTime)}</strong> đến <strong>{displayDateTime(saved?.endsOn, saved?.endsTime)}</strong> sẽ được hủy. Thao tác này có các ảnh hưởng sau:</p>
        <div className="tournament-cancel-effects">
          <div><span>Đăng ký mới</span><strong>Đóng ngay</strong></div>
          <div><span>Lịch sân</span><strong>Mở lại trong thời gian tổ chức</strong></div>
          <div><span>Người đã đăng ký</span><strong>Giữ nguyên danh sách</strong></div>
        </div>
        {cancelError && <p className="inline-message error" role="alert">{cancelError}</p>}
        <div className="tournament-cancel-actions">
          <button ref={cancelButtonRef} type="button" className="btn outline" disabled={saving} onClick={() => setCancelOpen(false)}>Quay lại</button>
          <button type="button" className="btn tournament-cancel-confirm" disabled={saving} onClick={cancelTournament}>{saving ? 'Đang hủy…' : 'Xác nhận hủy giải'}</button>
        </div>
      </section>
    </div>, document.body)}
  </section>;
}

function TournamentPlayers({ onSessionExpired }) {
  const [players, setPlayers] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true); setError('');
    api('/admin/tournament/registrations').then(setPlayers).catch(err => {
      if (err.status === 401) onSessionExpired();
      else setError(err.message);
    }).finally(() => setLoading(false));
  }
  useEffect(load, []);

  return <section className="table-card tournament-players">
    <div className="tournament-players-heading"><div><h2>Người đăng ký tham gia</h2><p>{players.length} người chơi</p></div><button className="btn outline" type="button" onClick={load} disabled={loading}>Làm mới</button></div>
    {loading ? <p role="status">Đang tải danh sách…</p> : error ? <p className="inline-message error" role="alert">{error}</p> : players.length === 0 ? <div className="empty-card">Chưa có người chơi đăng ký.</div> : <div className="table-scroll"><table><thead><tr><th>Họ tên</th><th>Số điện thoại</th><th>Email</th><th>Hạng mục</th><th>Ghi chú</th><th>Ngày đăng ký</th></tr></thead><tbody>{players.map(player => <tr key={player.id}><td>{player.fullName}</td><td>{player.phone}</td><td>{player.email || '—'}</td><td>{player.category || '—'}</td><td>{player.note || '—'}</td><td>{new Date(`${player.createdAt}Z`).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</td></tr>)}</tbody></table></div>}
  </section>;
}

export default function TournamentAdmin({ onSessionExpired = () => {} }) {
  const [section, setSection] = useState('management');
  return <div className="tournament-admin">
    <div className="tabs tournament-subtabs">{sections.map(([key, label]) => <button key={key} type="button" className={section === key ? 'active' : ''} onClick={() => setSection(key)}>{label}</button>)}</div>
    {section === 'management' && <div className="tournament-management">
      <TournamentSchedule onSessionExpired={onSessionExpired}/>
      <HeroBannerEditor onSessionExpired={onSessionExpired}/>
    </div>}
    {section === 'players' && <TournamentPlayers onSessionExpired={onSessionExpired}/>}
  </div>;
}
