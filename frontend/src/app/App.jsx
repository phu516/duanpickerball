import { useEffect, useState } from 'react';
import MainLayout from '../layouts/MainLayout';
import { api, apiUrl } from '../shared/api/apiClient';
import { useRoute } from './router';
import ProductsPage from '../features/products/pages/ProductsPage';
import ProductDetailPage from '../features/products/pages/ProductDetailPage';
import CourtsPage from '../features/courts/pages/CourtsPage';
import CartPage from '../features/cart/pages/CartPage';
import CheckoutPage from '../features/orders/pages/CheckoutPage';
import OrderDetailPage from '../features/orders/pages/OrderDetailPage';
import BookingDetailPage from '../features/bookings/pages/BookingDetailPage';
import BookingPaymentPage from '../features/bookings/pages/BookingPaymentPage';
import BookingLookupPage from '../features/bookings/pages/BookingLookupPage';
import AdminPage from '../features/auth/pages/AdminPage';
import StaffPage from '../features/orders/pages/StaffPage';
import EloPage from '../features/elo/EloPage';
import PlayerAuthPage from '../features/auth/pages/PlayerAuthPage';
import PlayerProfilePage from '../features/auth/pages/PlayerProfilePage';
import './hero-banner.css';

function HomePage() {
  const [banner, setBanner] = useState(null);
  const [registrationOpen, setRegistrationOpen] = useState(false);
  const [registrationDialogOpen, setRegistrationDialogOpen] = useState(false);
  useEffect(() => {
    let active = true;
    const loadBanner = () => api('/hero/banner')
      .then(data => { if (active) setBanner(data); })
      .catch(() => { if (active) setBanner(null); });
    const loadRegistrationStatus = () => api('/tournament/registration-status')
      .then(data => { if (active) setRegistrationOpen(data.open); })
      .catch(() => { if (active) setRegistrationOpen(false); });
    const refreshVisibleBanner = () => {
      if (document.visibilityState === 'visible') { loadBanner(); loadRegistrationStatus(); }
    };
    loadBanner(); loadRegistrationStatus();
    const timer = window.setInterval(refreshVisibleBanner, 15_000);
    window.addEventListener('focus', refreshVisibleBanner);
    document.addEventListener('visibilitychange', refreshVisibleBanner);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.removeEventListener('focus', refreshVisibleBanner);
      document.removeEventListener('visibilitychange', refreshVisibleBanner);
    };
  }, []);
  const eventTime = banner && new Date(`${banner.eventStartAt}+07:00`).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
  return <>
    <section className="hero"><div className="container hero-grid">
      <div><span className="eyebrow">PICKLEBALL · SHOP · COURTS</span><h1>Sẵn sàng cho<br/><em>mọi trận đấu.</em></h1><p>Chọn sân, chọn giờ, mua vợt hay gọi nước uống đến tận sân — tất cả trong một nơi.</p><div className="button-row"><a className="btn primary" href="#/courts">Đặt sân ngay →</a><a className="btn outline" href="#/products">Khám phá cửa hàng</a></div><div className="hero-facts"><span><b>06 sân</b> ban đầu</span><span><b>30 phút</b> giữ sân chờ cọc</span><span><b>02 giờ</b> tối thiểu</span></div></div>
      {banner ? <article className="hero-event">
        <div className="hero-event-image"><img src={apiUrl(`/hero/banner/image?v=${encodeURIComponent(banner.updatedAt)}`)} alt={`Banner ${banner.title}`}/></div>
        <div className="hero-event-info"><span className="hero-event-label">GIẢI ĐẤU TẠI PICKLEZONE</span><h2>{banner.title}</h2><time dateTime={`${banner.eventStartAt}+07:00`}>{eventTime}</time>{registrationOpen && <button type="button" className="hero-event-register" onClick={() => setRegistrationDialogOpen(true)}>Đăng ký tham gia</button>}<p>{banner.summary}</p>{banner.linkUrl && <a href={banner.linkUrl} target="_blank" rel="noopener noreferrer">Xem chi tiết →</a>}</div>
      </article> : <div className="hero-court"><div className="court-marking"><i/><i/><i/></div><div className="hero-ball">🏓</div><div className="hero-note"><b>PickleZone Court</b><small>06:00–23:00 · Mỗi ngày</small></div></div>}
    </div></section>
    <div className="container perks"><div><b>01 / Đặt sân nhanh</b><p>Kiểm tra lịch trống và giá trực tiếp, không cần tài khoản.</p></div><div><b>02 / Cửa hàng chung</b><p>Vợt, quần áo, phụ kiện và nước uống trong cùng giỏ hàng.</p></div><div><b>03 / Giao đến sân</b><p>Nhân viên mang sản phẩm đến sân khi bạn đang chơi.</p></div></div>
    <ProductsPage featured/>
    <section className="container callout"><div><span className="eyebrow">ĐÃ CÓ LỊCH CHƠI?</span><h2>Chọn thêm sân cho đồng đội.</h2><p>Một khách có thể tạo nhiều phiếu. Mỗi phiếu đặt một sân và khung giờ riêng.</p></div><a className="btn light" href="#/courts">Xem lịch sân →</a></section>
    {registrationDialogOpen && <RegistrationDialog onClose={() => setRegistrationDialogOpen(false)}/>}
  </>;
}

const tournamentCategories = ['Đơn nam', 'Đơn nữ', 'Đôi nam', 'Đôi nữ', 'Đôi nam nữ'];

function RegistrationDialog({ onClose }) {
  const [form, setForm] = useState({ fullName: '', phone: '', email: '', category: '', note: '' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [complete, setComplete] = useState(false);
  async function submit(event) {
    event.preventDefault(); setSaving(true); setError('');
    try {
      await api('/tournament/registrations', { method: 'POST', body: form });
      setComplete(true);
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <div className="tournament-registration-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !saving) onClose(); }}>
    <section className="tournament-registration-dialog" role="dialog" aria-modal="true" aria-labelledby="tournament-registration-title">
      <div className="tournament-registration-heading"><div><span className="eyebrow">PICKLEZONE TOURNAMENT</span><h2 id="tournament-registration-title">Đăng ký tham gia</h2></div><button type="button" aria-label="Đóng biểu mẫu" onClick={onClose} disabled={saving}>×</button></div>
      {complete ? <><p className="inline-message" role="status">Đăng ký thành công. Hẹn gặp bạn tại giải đấu!</p><button className="btn primary" type="button" onClick={onClose}>Hoàn tất</button></> : <form className="tournament-registration-form" onSubmit={submit}>
        <label>Họ và tên<input required maxLength="120" value={form.fullName} onChange={event => setForm(current => ({ ...current, fullName: event.target.value }))}/></label>
        <label>Số điện thoại<input required type="tel" maxLength="16" value={form.phone} onChange={event => setForm(current => ({ ...current, phone: event.target.value }))}/></label>
        <label>Email (không bắt buộc)<input type="email" maxLength="254" value={form.email} onChange={event => setForm(current => ({ ...current, email: event.target.value }))}/></label>
        <label>Hạng mục tham gia<select required value={form.category} onChange={event => setForm(current => ({ ...current, category: event.target.value }))}>
          <option value="" disabled hidden>Chọn hạng mục tham gia</option>
          {tournamentCategories.map(category => <option key={category} value={category}>{category}</option>)}
        </select></label>
        <label>Ghi chú<textarea rows="3" maxLength="500" value={form.note} onChange={event => setForm(current => ({ ...current, note: event.target.value }))}/></label>
        {error && <p className="inline-message error" role="alert">{error}</p>}
        <button className="btn primary" disabled={saving}>{saving ? 'Đang gửi…' : 'Gửi đăng ký'}</button>
      </form>}
    </section>
  </div>;
}

export default function App() {
  const routeWithQuery = useRoute();
  const route = routeWithQuery.split('?')[0];
  let page;
  if (route === '/') page = <HomePage/>;
  else if (route === '/products') page = <ProductsPage/>;
  else if (route.startsWith('/products/')) page = <ProductDetailPage id={route.split('/')[2]}/>;
  else if (route === '/courts') page = <CourtsPage/>;
  else if (route === '/cart') page = <CartPage/>;
  else if (route === '/checkout') page = <CheckoutPage/>;
  else if (route === '/booking-lookup') page = <BookingLookupPage/>;
  else if (route.startsWith('/booking-payments/')) page = <BookingPaymentPage code={route.split('/')[2]}/>;
  else if (route.startsWith('/bookings/')) page = <BookingDetailPage code={route.split('/')[2]}/>;
  else if (route.startsWith('/orders/')) page = <OrderDetailPage code={route.split('/')[2]}/>;
  else if (route === '/admin') page = <AdminPage/>;
  else if (route === '/elo') page = <EloPage/>;
  else if (route === '/profile') page = <PlayerProfilePage/>;
  else if (route === '/login') page = <PlayerAuthPage key={routeWithQuery} mode="login"/>;
  else if (route === '/register') page = <PlayerAuthPage key={routeWithQuery} mode="register"/>;
  else if (route === '/forgot-password') page = <PlayerAuthPage key={routeWithQuery} mode="forgot"/>;
  else if (route === '/reset-password') page = <PlayerAuthPage key={routeWithQuery} mode="forgot"/>;
  else if (route === '/verify-email') page = <PlayerAuthPage key={routeWithQuery} mode="verify"/>;
  else if (route === '/staff') page = <StaffPage/>;
  else page = <section className="container section"><h1>Không tìm thấy trang</h1><a href="#/">Về trang chủ</a></section>;
  return <MainLayout>{page}</MainLayout>;
}
