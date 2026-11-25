import { useState } from 'react';
import { CalendarDays, ShoppingBag, UserRound, House, Search, LogOut } from 'lucide-react';
import { useCart } from '../app/providers';
import { navigate } from '../app/router';
import { usePlayerAuth } from '../features/auth/PlayerAuthContext';
import './MainLayout.css';

export default function MainLayout({ children }) {
  const { cart } = useCart();
  const { user, logout } = usePlayerAuth();
  const [logoutError, setLogoutError] = useState('');
  const count = cart.reduce((sum, item) => sum + item.quantity, 0);
  return <>
    <header className="site-header"><div className="container nav">
      <a className="logo" href="#/">◉ Pickle<span>Zone</span></a>
      <nav><a href="#/"><House size={17}/> Trang chủ</a><a href="#/courts"><CalendarDays size={17}/> Đặt sân</a><a href="#/products"><Search size={17}/> Cửa hàng</a><a href="#/elo">Tìm trận</a></nav>
      <div className="nav-actions"><a href="#/cart"><ShoppingBag size={18}/> Giỏ hàng <b>{count}</b></a>{user === undefined ? null : user ? <details className="player-account-menu"><summary className="player-nav-name" aria-label="Mở menu tài khoản"><UserRound size={18}/> {user.fullName || user.username}</summary><div className="player-account-dropdown"><a href="#/profile">Hồ sơ của tôi</a><button type="button" className="player-nav-logout" onClick={() => logout().then(() => { setLogoutError(''); navigate('/'); }).catch(error => setLogoutError(error.message))}><LogOut size={17} aria-hidden="true"/> Đăng xuất</button></div></details> : <a href="#/login"><UserRound size={18}/> Đăng nhập</a>}<a href="#/admin">Quản lý</a></div>
    </div></header>
    {logoutError && <p className="inline-message error container" role="alert">{logoutError}</p>}
    <main>{children}</main>
    <footer className="site-footer"><div className="container footer-grid"><div><b className="logo">◉ Pickle<span>Zone</span></b><p>Đặt sân, chọn đồ chơi và tiếp năng lượng cho trận đấu.</p></div><div><b>Khám phá</b><a href="#/courts">Lịch sân</a><a href="#/products">Sản phẩm</a><a href="#/booking-lookup">Tra cứu phiếu</a></div><div><b>Hỗ trợ</b><p>06:00–23:00 mỗi ngày</p><p>TP. Hồ Chí Minh</p><a href="#/staff">Nhân viên giao sân</a></div></div></footer>
  </>;
}
