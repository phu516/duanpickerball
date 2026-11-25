import { useEffect, useState } from 'react';
import { UserRound, Trophy } from 'lucide-react';
import { api } from '../../../shared/api/apiClient';
import EloRankBadge from '../../elo/EloRankBadge';
import './PlayerProfilePage.css';

export default function PlayerProfilePage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api('/auth/me')
      .then(data => { if (active) setProfile(data); })
      .catch(err => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return <section className="container section player-profile-page">
    <span className="eyebrow">TÀI KHOẢN CỦA BẠN</span>
    <h1>Hồ sơ khách hàng</h1>
    {loading && <p role="status">Đang tải hồ sơ…</p>}
    {error && <p className="inline-message error" role="alert">{error}</p>}
    {profile && <>
      <article className="player-profile-card">
        <div className="player-profile-identity"><UserRound size={28}/><div><h2>{profile.fullName || profile.username}</h2><p>{profile.username}</p></div></div>
        {profile.phone && <p className="player-profile-phone">Số điện thoại: <strong>{profile.phone}</strong></p>}
      </article>
      {profile.elo ? <article className="player-profile-rank">
        <div className="player-profile-rank-heading"><Trophy size={24}/><div><span>HẠNG HIỆN TẠI</span><h2><EloRankBadge rank={profile.elo.rank}/></h2></div></div>
        <div className="player-profile-stats"><p><strong>{profile.elo.rating}</strong><span>Điểm Elo</span></p><p><strong>{profile.elo.gamesPlayed}</strong><span>Số trận đã chơi</span></p></div>
        <p className="player-profile-note">Hồ sơ Elo: {profile.elo.fullName} · #{profile.elo.id}</p>
      </article> : <article className="player-profile-card player-profile-no-elo">
        <h2>Chưa có hạng Elo</h2>
        <p>Tài khoản này chưa liên kết với hồ sơ Elo. Hãy quét khuôn mặt để tìm hoặc tạo hồ sơ người chơi.</p>
        <a className="btn primary" href="#/elo">Đi tới Tìm trận</a>
      </article>}
    </>}
  </section>;
}
