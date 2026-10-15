import { useEffect, useState } from 'react';
import { api, apiUrl } from '../../../shared/api/apiClient';
import './HeroBannerEditor.css';

const empty = {
  title: '', summary: '', eventStartAt: '', visibleFrom: '', visibleUntil: '',
  linkUrl: '', enabled: true,
};
const localInput = value => value?.slice(0, 16) || '';

export default function HeroBannerEditor() {
  const [draft, setDraft] = useState(empty);
  const [saved, setSaved] = useState(null);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api('/admin/hero/banner').then(data => {
      setSaved(data);
      if (data) setDraft({
        title: data.title, summary: data.summary,
        eventStartAt: localInput(data.eventStartAt),
        visibleFrom: localInput(data.visibleFrom),
        visibleUntil: localInput(data.visibleUntil),
        linkUrl: data.linkUrl || '', enabled: data.enabled,
      });
    }).catch(err => setError(err.message));
  }, []);

  useEffect(() => {
    if (!file) { setPreview(''); return undefined; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function update(key, value) { setDraft(current => ({ ...current, [key]: value })); }

  async function submit(event) {
    event.preventDefault();
    setError(''); setMessage('');
    if (!saved && !file) { setError('Vui lòng chọn ảnh banner.'); return; }
    if (file && file.size > 5 * 1024 * 1024) { setError('Ảnh phải nhỏ hơn 5 MB.'); return; }
    if (draft.visibleUntil <= draft.visibleFrom) {
      setError('Thời gian kết thúc hiển thị phải sau thời gian bắt đầu.'); return;
    }
    setSaving(true);
    try {
      const body = new FormData();
      body.append('details', JSON.stringify(draft));
      if (file) body.append('image', file);
      const result = await api('/admin/hero/banner', { method: 'POST', body, form: true });
      setSaved(result);
      setFile(null);
      setMessage('Đã lưu banner trang chủ.');
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }

  const image = preview || (saved && apiUrl(`/admin/hero/banner/image?v=${encodeURIComponent(saved.updatedAt)}`));
  return <section className="detail-card hero-banner-editor">
    <h2>Banner giải đấu trên trang chủ</h2>
    <p>Ảnh sẽ thay hình sân ở bên phải hero trong khoảng thời gian bạn chọn. Khi tắt hoặc hết hạn, hình sân mặc định sẽ hiện lại.</p>
    <form onSubmit={submit} className="hero-banner-form">
      <div className="hero-banner-fields">
        <label>Tên giải đấu<input required maxLength="120" value={draft.title} onChange={e => update('title', e.target.value)} placeholder="Ví dụ: Giải PickleZone Open 2026"/></label>
        <label>Mô tả ngắn<textarea required rows="3" maxLength="300" value={draft.summary} onChange={e => update('summary', e.target.value)} placeholder="Thông tin nổi bật dành cho người xem"/></label>
        <label>Ngày giờ diễn ra<input required type="datetime-local" value={draft.eventStartAt} onChange={e => update('eventStartAt', e.target.value)}/></label>
        <div className="hero-banner-dates">
          <label>Hiển thị từ<input required type="datetime-local" value={draft.visibleFrom} onChange={e => update('visibleFrom', e.target.value)}/></label>
          <label>Hiển thị đến<input required type="datetime-local" value={draft.visibleUntil} onChange={e => update('visibleUntil', e.target.value)}/></label>
        </div>
        <label>Liên kết xem chi tiết (không bắt buộc)<input type="url" maxLength="500" value={draft.linkUrl} onChange={e => update('linkUrl', e.target.value)} placeholder="https://example.com/giai-dau"/></label>
        <label>Ảnh giải đấu (PNG hoặc JPEG, tối đa 5 MB)<input type="file" accept="image/png,image/jpeg" onChange={e => setFile(e.target.files?.[0] || null)}/></label>
        <small>Khuyến nghị ảnh ngang khoảng 1200 × 800 px. Bản xem trước thể hiện vùng hiển thị trên trang chủ.</small>
        <label className="hero-banner-toggle"><input type="checkbox" checked={draft.enabled} onChange={e => update('enabled', e.target.checked)}/> Bật banner</label>
        {error && <p className="inline-message error" role="alert">{error}</p>}
        {message && <p className="inline-message" role="status">{message}</p>}
        <button className="btn primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu banner'}</button>
      </div>
      <div className="hero-banner-preview">
        <b>Xem trước ảnh bên phải hero</b>
        <div className="hero-banner-preview-frame">{image ? <img src={image} alt="Bản xem trước banner giải đấu"/> : <span>Chưa chọn ảnh</span>}</div>
        <strong>{draft.title || 'Tên giải đấu'}</strong>
        <span>{draft.summary || 'Mô tả ngắn của giải đấu'}</span>
      </div>
    </form>
  </section>;
}
