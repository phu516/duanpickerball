import { useEffect, useRef, useState } from 'react';
import './DateTimePicker.css';

const pad = value => String(value).padStart(2, '0');
const dateKey = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const toLocalDate = value => value ? new Date(`${value.slice(0, 10)}T12:00:00`) : null;
const formatDate = value => {
  if (!value) return '';
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
};

export default function DateTimePicker({ id, label, value, onChange, required = false, dateOnly = false, minDate, defaultTime = '09:00', inputRef, invalid = false, errorId }) {
  const [open, setOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const date = toLocalDate(value) || new Date();
    return new Date(date.getFullYear(), date.getMonth(), 1);
  });
  const wrapperRef = useRef(null);
  const restoreFocusRef = useRef(false);
  const valueDate = value?.slice(0, 10) || '';
  const valueTime = value?.slice(11, 16) || defaultTime;
  const today = new Date();

  useEffect(() => {
    if (!open) return undefined;
    const previousFocus = document.activeElement;
    function closeOnOutsideClick(event) {
      if (!wrapperRef.current?.contains(event.target)) setOpen(false);
    }
    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        restoreFocusRef.current = true;
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
      if (restoreFocusRef.current && previousFocus?.isConnected) previousFocus.focus();
      restoreFocusRef.current = false;
    };
  }, [open]);

  function showPicker() {
    const date = toLocalDate(valueDate) || new Date();
    setVisibleMonth(new Date(date.getFullYear(), date.getMonth(), 1));
    restoreFocusRef.current = false;
    setOpen(true);
  }

  function updateDate(date) {
    const nextDate = dateKey(date);
    onChange(dateOnly ? nextDate : `${nextDate}T${valueTime}`);
    if (dateOnly) {
      restoreFocusRef.current = true;
      setOpen(false);
    }
  }

  function updateTime(part, nextValue) {
    if (!valueDate) return;
    const [hour, minute] = valueTime.split(':');
    const time = part === 'hour' ? `${nextValue}:${minute}` : `${hour}:${nextValue}`;
    onChange(`${valueDate}T${time}`);
  }

  const monthStart = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth(), 1);
  const leadingDays = (monthStart.getDay() + 6) % 7;
  const daysInMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 0).getDate();
  const calendarCells = Array.from({ length: Math.ceil((leadingDays + daysInMonth) / 7) * 7 }, (_, index) => {
    const date = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth(), index - leadingDays + 1);
    return { date, currentMonth: date.getMonth() === visibleMonth.getMonth() };
  });

  return <div className="date-time-picker" ref={wrapperRef}>
    <label htmlFor={id}>{label}</label>
    <div className="date-time-control">
      <input
        ref={inputRef}
        id={id}
        type="text"
        className="date-time-trigger"
        readOnly
        value={valueDate ? `${formatDate(valueDate)}${dateOnly ? '' : ` - ${valueTime}`}` : ''}
        placeholder={dateOnly ? 'DD/MM/YYYY' : 'DD/MM/YYYY - HH:mm'}
        title={value ? (dateOnly ? formatDate(valueDate) : `${formatDate(valueDate)} - ${valueTime}`) : (dateOnly ? 'Chọn ngày' : 'Chọn ngày và giờ')}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-required={required}
        aria-invalid={invalid}
        aria-describedby={errorId}
        onClick={open ? () => setOpen(false) : showPicker}
        onKeyDown={event => {
          if (!open && ['Enter', ' ', 'ArrowDown'].includes(event.key)) {
            event.preventDefault(); showPicker();
          }
        }}
      />
      <svg className="date-time-trigger-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="16" rx="2.5"/><path d="M8 3v4M16 3v4M4 10h16"/></svg>
    </div>
    {open && <div className="date-time-popover" role="dialog" aria-label={`Chọn ${label.toLowerCase()}`}>
      <div className="date-time-month">
        <button type="button" aria-label="Tháng trước" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() - 1, 1))}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></button>
        <strong>{new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(visibleMonth)}</strong>
        <button type="button" aria-label="Tháng sau" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 1))}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></button>
      </div>
      <div className="date-time-calendar" role="group" aria-label="Lịch">
        {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map(day => <span key={day} className="date-time-weekday">{day}</span>)}
        {calendarCells.map(({ date, currentMonth }) => {
          const key = dateKey(date);
          const selected = key === valueDate;
          const isToday = key === dateKey(today);
          const disabled = Boolean(minDate && key < minDate);
          return <button key={key} type="button" aria-pressed={selected} aria-current={isToday ? 'date' : undefined}
            aria-label={formatDate(key)} title={formatDate(key)}
            className={`date-time-day${selected ? ' selected' : ''}${isToday ? ' today' : ''}${currentMonth ? '' : ' outside-month'}`}
            disabled={disabled}
            onClick={() => updateDate(date)}>{date.getDate()}</button>;
        })}
      </div>
      {!dateOnly && <div className="date-time-clock">
        <span>Giờ {label.toLowerCase().includes('kết thúc') ? 'kết thúc' : 'bắt đầu'}</span>
        <div>
          <label><span className="sr-only">Giờ</span><select aria-label="Giờ" value={valueTime.slice(0, 2)} disabled={!valueDate} onChange={event => updateTime('hour', event.target.value)}>
            {Array.from({ length: 24 }, (_, hour) => <option key={hour} value={pad(hour)}>{pad(hour)}</option>)}
          </select></label>
          <b>:</b>
          <label><span className="sr-only">Phút</span><select aria-label="Phút" value={valueTime.slice(3, 5)} disabled={!valueDate} onChange={event => updateTime('minute', event.target.value)}>
            {Array.from({ length: 60 }, (_, minute) => <option key={minute} value={pad(minute)}>{pad(minute)}</option>)}
          </select></label>
        </div>
      </div>}
      {!dateOnly && !valueDate && <p className="date-time-hint">Chọn ngày trước, sau đó chọn giờ.</p>}
      <div className="date-time-footer"><span>{valueDate ? (dateOnly ? formatDate(valueDate) : `${formatDate(valueDate)} - ${valueTime}`) : 'Chưa chọn ngày'}</span><button type="button" className="btn primary" onClick={() => { restoreFocusRef.current = true; setOpen(false); }}>Xong</button></div>
    </div>}
  </div>;
}
