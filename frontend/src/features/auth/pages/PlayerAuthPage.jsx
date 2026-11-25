import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, CheckCircle2, Eye, EyeOff, LockKeyhole, Mail, Phone, UserRound } from 'lucide-react';
import { api } from '../../../shared/api/apiClient';
import { navigate } from '../../../app/router';
import { usePlayerAuth } from '../PlayerAuthContext';
import './PlayerAuthPage.css';

const params = () => new URLSearchParams(location.hash.split('?')[1] || '');

export default function PlayerAuthPage({ mode }) {
  const { user, login } = usePlayerAuth();
  const [form, setForm] = useState({ username: '', fullName: '', phone: '', email: mode === 'verify' ? (sessionStorage.getItem('pendingVerificationEmail') || '') : '', otp: '', password: '', confirm: '' });
  const [recoveryStep, setRecoveryStep] = useState('email');
  const [resetToken, setResetToken] = useState('');
  const [resendSeconds, setResendSeconds] = useState(0);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [verificationDone, setVerificationDone] = useState(false);

  useEffect(() => {
    if (resendSeconds <= 0) return undefined;
    const timer = window.setTimeout(() => setResendSeconds(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendSeconds]);

  useEffect(() => {
    if (mode !== 'verify') return;
    const sentAt = Number(sessionStorage.getItem('verificationSentAt'));
    if (sentAt) setResendSeconds(Math.max(0, Math.ceil((sentAt + 60_000 - Date.now()) / 1000)));
  }, [mode]);

  function change(field, value) {
    setForm(current => ({ ...current, [field]: value }));
    setError('');
  }

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setError(''); setMessage('');
    if ((mode === 'register' || (mode === 'forgot' && recoveryStep === 'password')) && form.password !== form.confirm) {
      setError('Mật khẩu xác nhận không khớp.'); return;
    }
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(form.username.trim(), form.password);
        navigate(params().get('next') === '/elo' ? '/elo' : '/');
      } else if (mode === 'register') {
        await api('/auth/register', { method: 'POST', body: {
          username: form.username.trim(), fullName: form.fullName.trim(), phone: form.phone.trim(),
          email: form.email.trim(), password: form.password,
        } });
        sessionStorage.setItem('pendingVerificationEmail', form.email.trim());
        sessionStorage.setItem('verificationSentAt', String(Date.now()));
        navigate('/verify-email');
      } else if (mode === 'verify') {
        const result = await api('/auth/verify-email', { method: 'POST', body: { email: form.email.trim(), otp: form.otp } });
        sessionStorage.removeItem('pendingVerificationEmail');
        sessionStorage.removeItem('verificationSentAt');
        setVerificationDone(true); setForm(current => ({ ...current, otp: '' })); setMessage(result.message);
      } else if (mode === 'forgot') {
        if (recoveryStep === 'email') {
          const result = await api('/auth/forgot-password', { method: 'POST', body: { email: form.email.trim() } });
          setRecoveryStep('otp'); setResendSeconds(60); setMessage(result.message);
        } else if (recoveryStep === 'otp') {
          const result = await api('/auth/verify-reset-otp', { method: 'POST', body: { email: form.email.trim(), otp: form.otp } });
          setResetToken(result.resetToken); setForm(current => ({ ...current, otp: '' })); setRecoveryStep('password');
        } else if (recoveryStep === 'password') {
          const result = await api('/auth/reset-password', { method: 'POST', body: { resetToken, password: form.password } });
          setMessage(result.message); setResetToken(''); setRecoveryStep('done');
          setForm(current => ({ ...current, password: '', confirm: '' }));
        }
      }
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function resendOtp() {
    if (busy || resendSeconds > 0) return;
    setError(''); setMessage(''); setBusy(true);
    try {
      const result = await api('/auth/forgot-password', { method: 'POST', body: { email: form.email.trim() } });
      setMessage(result.message); setResendSeconds(60); setForm(current => ({ ...current, otp: '' }));
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function resend(event) {
    event.preventDefault(); setError(''); setMessage(''); setBusy(true);
    try {
      const result = await api('/auth/resend-verification', { method: 'POST', body: { email: form.email.trim() } });
      setMessage(result.message); setResendSeconds(60);
      sessionStorage.setItem('pendingVerificationEmail', form.email.trim());
      sessionStorage.setItem('verificationSentAt', String(Date.now()));
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  const title = mode === 'forgot' ? ({ email: 'Quên mật khẩu', otp: 'Nhập mã xác minh', password: 'Đặt lại mật khẩu', done: 'Đổi mật khẩu thành công' })[recoveryStep] : ({ login: 'Đăng nhập người chơi', register: 'Tạo tài khoản người chơi', verify: verificationDone ? 'Xác minh thành công' : 'Xác minh tài khoản' })[mode];
  const intro = {
    login: 'Đăng nhập để tìm đối thủ phù hợp và tiếp tục hành trình trên sân.',
    register: 'Tạo tài khoản của bạn để tham gia cộng đồng người chơi PickleZone.',
    forgot: ({ email: 'Nhập email đã đăng ký để nhận mã xác minh.', otp: `Nhập mã gồm 8 chữ số đã gửi đến ${form.email}. Mã có hiệu lực 10 phút.`, password: 'Chọn mật khẩu mới để tiếp tục sử dụng tài khoản của bạn.', done: 'Bạn có thể đăng nhập bằng mật khẩu mới.' })[recoveryStep],
    verify: verificationDone ? 'Tài khoản của bạn đã sẵn sàng để đăng nhập.' : 'Nhập mã 6 chữ số đã gửi đến email của bạn. Mã có hiệu lực 10 phút.',
  }[mode];

  function passwordField(field, label, autoComplete) {
    return <label className="player-auth-field">{label}
      <span className="player-auth-input"><LockKeyhole size={19} aria-hidden="true"/><input autoComplete={autoComplete} type={showPassword ? 'text' : 'password'} required minLength={mode === 'login' ? undefined : 10} value={form[field]} onChange={event => change(field, event.target.value)} placeholder={field === 'confirm' ? 'Nhập lại mật khẩu' : 'Nhập mật khẩu'}/><button type="button" className="player-auth-password-toggle" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff size={19}/> : <Eye size={19}/>}</button></span>
    </label>;
  }

  return <section className="player-auth-section">
    <div className="container player-auth-shell">
      <aside className="player-auth-story">
        <a className="player-auth-brand" href="#/"><span className="player-auth-brand-mark">◉</span><span>Pickle<strong>Zone</strong><small>PLAYER CLUB</small></span></a>
        <div className="player-auth-story-copy">
          <span className="player-auth-story-kicker">CHƠI HẾT MÌNH · KẾT NỐI ĐÚNG TRÌNH</span>
          <h2>Vào sân.<br/><em>Vào cuộc chơi.</em></h2>
          <p>Mỗi trận đấu hay bắt đầu từ một người chơi phù hợp. Đăng nhập và tìm đối thủ tiếp theo của bạn.</p>
        </div>
        <div className="player-auth-court" aria-hidden="true"><span className="player-auth-court-lines"/><span className="player-auth-ball"/></div>
        <div className="player-auth-story-bottom"><span><strong>01</strong> Tạo hồ sơ</span><span><strong>02</strong> Tìm đối thủ</span><span><strong>03</strong> Vào sân</span></div>
      </aside>
      <div className="player-auth-panel">
        <div className="player-auth-topline"><span>KHÔNG GIAN NGƯỜI CHƠI</span><a href="#/"><ArrowLeft size={16}/> Trang chủ</a></div>
        <div className="player-auth-heading"><span className="player-auth-kicker">PICKLEZONE ACCOUNT</span><h1>{title}</h1><p>{intro}</p></div>
        {user && mode === 'login' && <p className="player-auth-success" role="status"><CheckCircle2 size={19}/> Bạn đang đăng nhập với tài khoản {user.fullName}.</p>}
        {message && <p className="player-auth-success" role="status"><CheckCircle2 size={19}/>{message}</p>}
        {error && <p className="inline-message error" role="alert">{error}</p>}
        {!(mode === 'verify' && verificationDone) && !(mode === 'forgot' && recoveryStep === 'done') && <form className="player-auth-form" onSubmit={submit}>
          {['login', 'register'].includes(mode) && <label className="player-auth-field">Tên đăng nhập<span className="player-auth-input"><UserRound size={19} aria-hidden="true"/><input autoComplete="username" required minLength="3" maxLength="40" value={form.username} onChange={event => change('username', event.target.value)} placeholder="Tên đăng nhập của bạn"/></span></label>}
          {mode === 'register' && <><label className="player-auth-field">Họ và tên<span className="player-auth-input"><UserRound size={19} aria-hidden="true"/><input autoComplete="name" required maxLength="120" value={form.fullName} onChange={event => change('fullName', event.target.value)} placeholder="Nhập họ và tên"/></span></label><label className="player-auth-field">Số điện thoại<span className="player-auth-input"><Phone size={19} aria-hidden="true"/><input autoComplete="tel" type="tel" required value={form.phone} onChange={event => change('phone', event.target.value)} placeholder="Số điện thoại của bạn"/></span></label></>}
          {(mode === 'register' || mode === 'verify' || (mode === 'forgot' && recoveryStep === 'email')) && <label className="player-auth-field">Email<span className="player-auth-input"><Mail size={19} aria-hidden="true"/><input autoComplete="email" type="email" required maxLength="254" value={form.email} onChange={event => change('email', event.target.value)} placeholder="tenban@example.com"/></span></label>}
          {mode === 'verify' && <label className="player-auth-field">Mã xác minh<span className="player-auth-input"><LockKeyhole size={19} aria-hidden="true"/><input autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required value={form.otp} onChange={event => change('otp', event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="6 chữ số"/></span></label>}
          {mode === 'forgot' && recoveryStep === 'otp' && <label className="player-auth-field">Mã OTP<span className="player-auth-input"><LockKeyhole size={19} aria-hidden="true"/><input autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{8}" maxLength="8" required value={form.otp} onChange={event => change('otp', event.target.value.replace(/\D/g, '').slice(0, 8))} placeholder="8 chữ số"/></span></label>}
          {(mode === 'login' || mode === 'register' || (mode === 'forgot' && recoveryStep === 'password')) && passwordField('password', mode === 'forgot' ? 'Mật khẩu mới' : 'Mật khẩu', mode === 'login' ? 'current-password' : 'new-password')}
          {(mode === 'register' || (mode === 'forgot' && recoveryStep === 'password')) && passwordField('confirm', 'Xác nhận mật khẩu', 'new-password')}
          {mode === 'login' && <a className="player-auth-forgot" href="#/forgot-password">Quên mật khẩu?</a>}
          <button className="player-auth-submit" disabled={busy}>{busy ? 'Đang xử lý…' : mode === 'forgot' ? ({ email: 'Gửi mã xác minh', otp: 'Xác minh mã', password: 'Đổi mật khẩu' })[recoveryStep] : ({ login: 'Đăng nhập', register: 'Tạo tài khoản', verify: 'Xác minh tài khoản' })[mode]}<ArrowRight size={19}/></button>
        </form>}
        {mode === 'forgot' && recoveryStep === 'otp' && <div className="player-auth-recovery-actions"><button type="button" className="btn outline" disabled={busy || resendSeconds > 0} onClick={resendOtp}>{resendSeconds > 0 ? `Gửi lại mã sau ${resendSeconds}s` : 'Gửi lại mã'}</button><button type="button" className="btn outline" onClick={() => { setRecoveryStep('email'); setMessage(''); setError(''); setResendSeconds(0); }}>Đổi email</button></div>}
        {mode === 'forgot' && recoveryStep === 'password' && error && <div className="player-auth-recovery-actions"><button type="button" className="btn outline" onClick={() => { setRecoveryStep('email'); setResetToken(''); setForm(current => ({ ...current, password: '', confirm: '' })); setError(''); }}>Yêu cầu mã mới</button></div>}
        {mode === 'verify' && !verificationDone && <div className="player-auth-recovery-actions"><button type="button" className="btn outline" disabled={busy || resendSeconds > 0 || !form.email.trim()} onClick={resend}>{resendSeconds > 0 ? `Gửi lại mã sau ${resendSeconds}s` : 'Gửi mã / gửi lại mã'}</button></div>}
        <div className="player-auth-links">{mode === 'login' ? <span>Chưa có tài khoản? <a href="#/register">Đăng ký ngay <ArrowRight size={16}/></a></span> : <a href="#/login"><ArrowLeft size={16}/> Quay lại đăng nhập</a>}</div>
        {mode === 'login' && <p className="player-auth-guest">Chỉ muốn đặt sân? <a href="#/courts">Tiếp tục mà không cần đăng nhập</a></p>}
      </div>
    </div>
  </section>;
}
