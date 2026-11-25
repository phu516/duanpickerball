import { useEffect, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import FaceScan from './FaceScan';
import { api } from '../../shared/api/apiClient';
import EloRankBadge from './EloRankBadge';
import './EloPage.css';

const emptyForm = { fullName: '', phone: '', experience: '', pickleballMonths: '', otherSport: '' };
const validName = /^[\p{L}\p{M}](?:[\p{L}\p{M} .'-]*[\p{L}\p{M}])?$/u;
const validPhone = /^(?:0[0-9]{9}|\+84[0-9]{9})$/;

function validatePlayer(form, players) {
  const errors = {};
  if (!validName.test(form.fullName.trim()) || form.fullName.trim().length > 120) errors.fullName = 'Họ tên chỉ được chứa chữ và dấu cách hợp lệ.';
  if (!validPhone.test(form.phone.trim())) errors.phone = 'Nhập 10 chữ số bắt đầu bằng 0 hoặc +84.';
  else if (players.some(player => player.phone?.replace(/^\+84/, '0') === form.phone.trim().replace(/^\+84/, '0'))) errors.phone = 'Số điện thoại đã có hồ sơ Elo.';
  if (!form.experience) errors.experience = 'Chọn kinh nghiệm chơi.';
  if (form.experience === 'PICKLEBALL' && (form.pickleballMonths === '' || Number(form.pickleballMonths) < 0 || Number(form.pickleballMonths) > 600)) errors.pickleballMonths = 'Nhập số tháng từ 0 đến 600.';
  if (['RACKET', 'SPORT'].includes(form.experience) && !form.otherSport.trim()) errors.otherSport = 'Nhập môn thể thao đã chơi.';
  return errors;
}
const sections = [
  ['registration', 'Đăng ký Elo'],
  ['players', 'Danh sách người chơi'],
];

export default function EloAdmin() {
  const [players, setPlayers] = useState([]);
  const [faceDuplicates, setFaceDuplicates] = useState([]);
  const [message, setMessage] = useState('');
  const [createdPlayer, setCreatedPlayer] = useState('');
  const [section, setSection] = useState('registration');
  const [form, setForm] = useState(emptyForm);
  const [proof, setProof] = useState(null);
  const [duplicateMessage, setDuplicateMessage] = useState('');
  const [facePlayerId, setFacePlayerId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [touched, setTouched] = useState({});
  const [phoneServerError, setPhoneServerError] = useState('');
  const fieldErrors = validatePlayer(form, players);

  function changeField(field, value) {
    setForm(current => ({ ...current, [field]: value }));
    setTouched(current => ({ ...current, [field]: true }));
    if (field === 'phone') setPhoneServerError('');
  }

  function changeSection(next) {
    setCreatedPlayer('');
    setForm(emptyForm);
    setProof(null);
    setDuplicateMessage('');
    setFacePlayerId(null);
    setMessage('');
    setTouched({});
    setPhoneServerError('');
    setSection(next);
  }

  async function savePlayer(event) {
    event.preventDefault(); setMessage('');
    if (!facePlayerId) {
      setTouched({ fullName: true, phone: true, experience: true, pickleballMonths: true, otherSport: true });
      if (Object.keys(fieldErrors).length || phoneServerError) return;
    }
    if (!proof) { setMessage('Hãy quét khuôn mặt trước khi lưu hồ sơ.'); return; }
    setBusy(true);
    try {
      if (facePlayerId) {
        await api(`/admin/elo/players/${facePlayerId}/face`, { method: 'POST', body: { scanToken: proof } });
        setMessage('Đã cập nhật khuôn mặt cho người chơi.');
      } else {
        await api('/admin/elo/players', { method: 'POST', body: {
          ...form, pickleballMonths: form.experience === 'PICKLEBALL' ? Number(form.pickleballMonths) : null,
          otherSport: ['RACKET', 'SPORT'].includes(form.experience) ? form.otherSport.trim() : null, scanToken: proof,
        } });
        setCreatedPlayer(form.fullName.trim());
      }
      setForm(emptyForm); setProof(null); setDuplicateMessage(''); setFacePlayerId(null); setTouched({}); setPhoneServerError('');
      await load();
    } catch (err) {
      if (err.code === 'PHONE_EXISTS') setPhoneServerError('Số điện thoại đã có hồ sơ Elo.');
      else if (err.code === 'FACE_EXISTS') { setDuplicateMessage(err.message); setProof(null); }
      else setMessage(err.message);
    }
    finally { setBusy(false); }
  }

  async function load() {
    try {
      const [playerList, duplicates] = await Promise.all([api('/admin/elo/players'), api('/admin/elo/face-duplicates')]);
      setPlayers(playerList);
      setFaceDuplicates(duplicates);
    } catch (err) { setMessage(err.message); }
  }
  useEffect(() => { load(); }, []);

  return <div className="tournament-admin">
    <nav className="tabs tournament-subtabs elo-subtabs" aria-label="Các phần quản lý Elo">
      {sections.map(([key, label]) => <button key={key} type="button" aria-current={section === key ? 'page' : undefined} className={section === key ? 'active' : ''} onClick={() => changeSection(key)}>{label}</button>)}
    </nav>
    {message && <p className="inline-message" role="status">{message}</p>}
    {section === 'registration' && createdPlayer && <div className="table-card elo-created-card" role="status">
      <CheckCircle2 className="elo-created-icon" size={56} aria-hidden="true"/>
      <h2>Đã tạo hồ sơ thành công!</h2>
      <p>Hồ sơ Elo của <strong>{createdPlayer}</strong> đã được lưu. Người chơi có thể quét khuôn mặt để tìm trận.</p>
      <button type="button" className="btn primary" onClick={() => changeSection('registration')}>Tạo hồ sơ mới</button>
    </div>}
    {section === 'registration' && !createdPlayer && <form className="table-card elo-admin-form" onSubmit={savePlayer} noValidate>
      <h2>{facePlayerId ? 'Quét lại khuôn mặt' : 'Đăng ký Elo cho người chơi mới'}</h2>
      {facePlayerId ? <p>Người chơi: {players.find(player => player.id === facePlayerId)?.fullName}</p> : <>
        <label>Họ tên<input maxLength="120" value={form.fullName} aria-invalid={Boolean(touched.fullName && fieldErrors.fullName)} aria-describedby={touched.fullName && fieldErrors.fullName ? 'elo-name-error' : undefined} onChange={event => changeField('fullName', event.target.value)}/>{touched.fullName && fieldErrors.fullName && <span id="elo-name-error" className="elo-field-error">{fieldErrors.fullName}</span>}</label>
        <label>Số điện thoại<input type="tel" maxLength="16" value={form.phone} aria-invalid={Boolean(touched.phone && (fieldErrors.phone || phoneServerError))} aria-describedby={touched.phone && (fieldErrors.phone || phoneServerError) ? 'elo-phone-error' : undefined} onChange={event => changeField('phone', event.target.value)}/>{touched.phone && (fieldErrors.phone || phoneServerError) && <span id="elo-phone-error" className="elo-field-error">{phoneServerError || fieldErrors.phone}</span>}</label>
        <label>Kinh nghiệm<select value={form.experience} aria-invalid={Boolean(touched.experience && fieldErrors.experience)} onChange={event => changeField('experience', event.target.value)}><option value="">Chọn kinh nghiệm</option><option value="PICKLEBALL">Đã chơi pickleball</option><option value="RACKET">Đã chơi môn dùng vợt (cầu lông, tennis...)</option><option value="SPORT">Đã chơi môn thể thao khác</option><option value="NEW">Chưa chơi thể thao</option></select>{touched.experience && fieldErrors.experience && <span className="elo-field-error">{fieldErrors.experience}</span>}</label>
        {form.experience === 'PICKLEBALL' && <label>Số tháng chơi pickleball<input type="number" min="0" max="600" value={form.pickleballMonths} aria-invalid={Boolean(touched.pickleballMonths && fieldErrors.pickleballMonths)} onChange={event => changeField('pickleballMonths', event.target.value)}/>{touched.pickleballMonths && fieldErrors.pickleballMonths && <span className="elo-field-error">{fieldErrors.pickleballMonths}</span>}</label>}
        {['RACKET', 'SPORT'].includes(form.experience) && <label>Môn đã chơi<input maxLength="120" placeholder="Ví dụ: cầu lông" value={form.otherSport} aria-invalid={Boolean(touched.otherSport && fieldErrors.otherSport)} onChange={event => changeField('otherSport', event.target.value)}/>{touched.otherSport && fieldErrors.otherSport && <span className="elo-field-error">{fieldErrors.otherSport}</span>}</label>}
      </>}
      <div className="button-row"><FaceScan purpose="enroll" disabled={busy} onStart={() => { setProof(null); setDuplicateMessage(''); setMessage(''); }} onVerified={async proofValue => {
        const result = await api('/admin/elo/face-check', { method: 'POST', body: { proof: proofValue, excludePlayerId: facePlayerId } });
        if (result.duplicate) {
          setProof(null);
          const owners = result.matches.slice(0, 5).map(match => `#${match.id} ${match.fullName}`).join(', ');
          const additional = result.matches.length > 5 ? ` và ${result.matches.length - 5} hồ sơ khác` : '';
          setDuplicateMessage(`Khuôn mặt này đã có trong hồ sơ Elo ${owners}${additional}. Không thể đăng ký lại.`);
        } else {
          setDuplicateMessage('');
          setProof(result.scanToken);
        }
      }}/>
        {facePlayerId && <button type="button" className="btn outline" onClick={() => changeSection('players')}>Hủy</button>}
      </div>
      {duplicateMessage && <p className="inline-message error" role="alert">{duplicateMessage}</p>}
      {proof && <p className="elo-scan-success" role="status">✓ Đã quét thành công</p>}
      <button className="btn primary" disabled={busy || !proof}>{facePlayerId ? 'Lưu khuôn mặt' : 'Tạo hồ sơ Elo'}</button>
    </form>}
    {section === 'players' && faceDuplicates.length > 0 && <div className="inline-message error" role="status"><strong>Phát hiện {faceDuplicates.length} cặp hồ sơ có khuôn mặt trùng:</strong> {faceDuplicates.map(pair => `#${pair.firstId} ${pair.firstName} / #${pair.secondId} ${pair.secondName}`).join('; ')}. Vui lòng kiểm tra trước khi xử lý dữ liệu Elo.</div>}
    {section === 'players' && <div className="table-card">
      <h2>Hồ sơ Elo</h2>
      <p>Xếp theo Elo từ cao xuống thấp. Rank: Đồng &lt;900, Bạc 900–999, Vàng 1000–1099, Bạch kim 1100–1199, Kim cương 1200–1299, Cao thủ 1300–1399, Thách đấu ≥1400.</p>
      <div className="table-scroll"><table><thead><tr><th>Tên</th><th>SĐT</th><th>Kinh nghiệm</th><th>Elo</th><th>Rank</th><th>Số trận</th><th>Khuôn mặt</th></tr></thead><tbody>
        {players.map(player => <tr key={player.id}><td>{player.fullName}</td><td>{player.phone}</td><td>{player.experience === 'PICKLEBALL' ? `Pickleball ${player.pickleballMonths} tháng` : player.otherSport || player.experience}</td><td>{player.rating}</td><td><EloRankBadge rank={player.rank}/></td><td>{player.gamesPlayed}</td><td><button type="button" onClick={() => { changeSection('registration'); setFacePlayerId(player.id); setMessage('Mở camera để quét lại khuôn mặt.'); }}>Quét lại</button></td></tr>)}
      </tbody></table></div>
    </div>}
  </div>;
}
