import MainLayout from '../layouts/MainLayout';
import { useRoute } from './router';
import ProductsPage from '../features/products/pages/ProductsPage';
import CourtsPage from '../features/courts/pages/CourtsPage';
import CartPage from '../features/cart/pages/CartPage';
import CheckoutPage from '../features/orders/pages/CheckoutPage';
import OrderDetailPage from '../features/orders/pages/OrderDetailPage';
import BookingDetailPage from '../features/bookings/pages/BookingDetailPage';
import BookingLookupPage from '../features/bookings/pages/BookingLookupPage';
import AdminPage from '../features/auth/pages/AdminPage';
import StaffPage from '../features/orders/pages/StaffPage';

function HomePage() {
  return <><section className="hero"><div className="container hero-grid"><div><span className="eyebrow">PICKLEBALL · SHOP · COURTS</span><h1>Sẵn sàng cho<br/><em>mọi trận đấu.</em></h1><p>Chọn sân, chọn giờ, mua vợt hay gọi nước uống đến tận sân — tất cả trong một nơi.</p><div className="button-row"><a className="btn primary" href="#/courts">Đặt sân ngay →</a><a className="btn outline" href="#/products">Khám phá cửa hàng</a></div><div className="hero-facts"><span><b>06 sân</b> ban đầu</span><span><b>30 phút</b> giữ sân chờ cọc</span><span><b>02 giờ</b> tối thiểu</span></div></div><div className="hero-court"><div className="court-marking"><i/><i/><i/></div><div className="hero-ball">🏓</div><div className="hero-note"><b>PickleZone Court</b><small>06:00–23:00 · Mỗi ngày</small></div></div></div></section><div className="container perks"><div><b>01 / Đặt sân nhanh</b><p>Kiểm tra lịch trống và giá trực tiếp, không cần tài khoản.</p></div><div><b>02 / Cửa hàng chung</b><p>Vợt, quần áo, phụ kiện và nước uống trong cùng giỏ hàng.</p></div><div><b>03 / Giao đến sân</b><p>Nhân viên mang sản phẩm đến sân khi bạn đang chơi.</p></div></div><ProductsPage featured/><section className="container callout"><div><span className="eyebrow">ĐÃ CÓ LỊCH CHƠI?</span><h2>Chọn thêm sân cho đồng đội.</h2><p>Một khách có thể tạo nhiều phiếu. Mỗi phiếu đặt một sân và khung giờ riêng.</p></div><a className="btn light" href="#/courts">Xem lịch sân →</a></section></>;
}

export default function App() {
  const route = useRoute();
  let page;
  if (route === '/') page = <HomePage/>;
  else if (route === '/products') page = <ProductsPage/>;
  else if (route === '/courts') page = <CourtsPage/>;
  else if (route === '/cart') page = <CartPage/>;
  else if (route === '/checkout') page = <CheckoutPage/>;
  else if (route === '/booking-lookup') page = <BookingLookupPage/>;
  else if (route.startsWith('/bookings/')) page = <BookingDetailPage code={route.split('/')[2]}/>;
  else if (route.startsWith('/orders/')) page = <OrderDetailPage code={route.split('/')[2]}/>;
  else if (route === '/admin') page = <AdminPage/>;
  else if (route === '/staff') page = <StaffPage/>;
  else page = <section className="container section"><h1>Không tìm thấy trang</h1><a href="#/">Về trang chủ</a></section>;
  return <MainLayout>{page}</MainLayout>;
}
