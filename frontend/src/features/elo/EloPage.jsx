import { useEffect, useState } from 'react';
import { api } from '../../shared/api/apiClient';
import { usePlayerAuth } from '../auth/PlayerAuthContext';
import EloRankBadge from './EloRankBadge';
import './EloPage.css';

import FaceScan from './FaceScan';

export default function EloPage() {
  const { user } = usePlayerAuth();
  const [profile, setProfile] = useState(null);
  const [verificationToken, setVerificationToken] = useState('');
  const [ticket, setTicket] = useState(null);
  const [error, setError] = useState('');
  const [showRegistration, setShowRegistration] = useState(false);
  const [registrationMessage, setRegistrationMessage] = useState('');
  const [experience, setExperience] = useState('');
  useEffect(() => {
    setProfile(null);
    setVerificationToken('');
    setTicket(null);
    setError('');
    setShowRegistration(false);
    setRegistrationMessage('');
  }, [user?.username]);
  useEffect(() => {
    if (!ticket?.ticketId || ticket.matchId) return undefined;
    const timer = setInterval(() => api(`/elo/queue/${ticket.ticketId}`).then(setTicket).catch(err => setError(err.message)), 2000);
    return () => clearInterval(timer);
  }, [ticket?.ticketId, ticket?.matchId]);

  async function identify(proof, signal) {
    const found = await api('/elo/identify', { method: 'POST', body: { proof } });
    if (signal.aborted) return;
    setProfile(found.player);
    setVerificationToken(found.token);
  }
  async function selfRegister(proof, signal) {
    let result;
    try {
      result = await api('/elo/self-register', { method: 'POST', body: { experience, proof } });
    } catch (err) {
      if (err.status === 404) throw new Error('Backend đang chạy phiên bản cũ, chưa có chức năng tự đăng ký Elo. Hãy khởi động lại backend rồi quét lại.');
      throw err;
    }
    if (signal.aborted) return;
    setProfile(result.player);
    setVerificationToken(result.token);
    setShowRegistration(false);
    setRegistrationMessage(result.created ? 'Đã tạo hồ sơ Elo thành công!' : 'Đã liên kết hồ sơ Elo hiện có với tài khoản của bạn.');
  }
  async function start() { try { setTicket(await api('/elo/queue', { method: 'POST', body: { verificationToken } })); } catch (err) { if (err.status === 401) setProfile(null); setError(err.message); } }
  async function accept() { try { setTicket(await api(`/elo/queue/${ticket.ticketId}/accept`, { method: 'POST' })); } catch (err) { setError(err.message); } }
  async function cancel() { try { await api(`/elo/queue/${ticket.ticketId}`, { method: 'DELETE' }); setTicket(null); setProfile(null); setVerificationToken(''); } catch (err) { setError(err.message); } }

  if (user === undefined) return <section className="container section elo-page"><h1>Tìm trận đúng trình</h1><p role="status">Đang kiểm tra tài khoản…</p></section>;
  if (!user) return <section className="container section elo-page"><h1>Tìm trận đúng trình</h1><div className="detail-card"><h2>Đăng nhập để tìm trận</h2><p>Bạn cần đăng nhập tài khoản người chơi trước khi quét mặt và tìm đối thủ.</p><a className="btn primary" href="#/login?next=/elo">Đăng nhập</a> <a className="btn outline" href="#/register">Đăng ký</a></div></section>;

  return <section className="container section elo-page"><h1>Tìm trận đúng trình</h1><p>Đã có hồ sơ Elo? Quét khuôn mặt để tìm trận. Nếu chưa có, bạn có thể tự đăng ký ngay tại đây.</p>{error && <p className="inline-message error" role="alert">{error}</p>}
    {!profile && !showRegistration && <div className="detail-card elo-entry-actions"><FaceScan purpose="identify" onStart={() => setError('')} onVerified={identify}/><button type="button" className="btn primary" onClick={() => { setError(''); setShowRegistration(true); }}>Tự đăng ký Elo</button></div>}
    {!profile && showRegistration && <div className="detail-card elo-self-form">
      <h2>Tự đăng ký hồ sơ Elo</h2>
      <p>Chọn kinh nghiệm chơi, sau đó quét khuôn mặt để tạo hồ sơ.</p>
      <label>Kinh nghiệm chơi<select value={experience} onChange={event => setExperience(event.target.value)}><option value="">Chọn kinh nghiệm</option><option value="PICKLEBALL">Đã chơi pickleball</option><option value="RACKET">Đã chơi môn dùng vợt</option><option value="SPORT">Đã chơi môn thể thao khác</option><option value="NEW">Chưa chơi thể thao</option></select></label>
      <div className="elo-entry-actions"><FaceScan purpose="enroll" disabled={!experience} onStart={() => setError('')} onVerified={selfRegister}/><button type="button" className="btn outline" onClick={() => setShowRegistration(false)}>Quay lại</button></div>
    </div>}
    {profile && registrationMessage && <p className="elo-scan-success" role="status">{registrationMessage}</p>}
    {profile && <div className="detail-card"><p className="elo-scan-success" role="status">✓ Đã quét thành công</p><h2>{profile.fullName}</h2><p>Elo {profile.rating} · <EloRankBadge rank={profile.rank}/> · {profile.gamesPlayed} trận</p>{!ticket ? <button className="btn primary" onClick={start}>Bắt đầu tìm đối thủ</button> : ticket.matchId ? <p role="status">Đã ghép trận #{ticket.matchId}. Hãy báo mã trận cho admin.</p> : <><p role="status">{ticket.opponent ? <>Đối thủ {ticket.opponent.fullName} · Elo {ticket.opponent.rating} · <EloRankBadge rank={ticket.opponent.rank}/></> : 'Đang tìm đối thủ trong khoảng 100 Elo…'}</p>{ticket.opponent && <button className="btn primary" disabled={ticket.accepted} onClick={accept}>{ticket.accepted ? 'Đang chờ đối thủ xác nhận' : 'Chấp nhận ghép trận'}</button>} <button className="btn outline" onClick={cancel}>Hủy tìm trận</button></>}</div>}
  </section>;
}

