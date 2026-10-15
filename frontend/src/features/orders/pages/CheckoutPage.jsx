import { useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, CircleHelp, House, MapPin, Store } from 'lucide-react';
import { useCart } from '../../../app/providers';
import ProductPhoto from '../../products/components/ProductPhoto';
import { orderApi } from '../api/orderApi';
import { bookingTokenKey, money, orderTokenKey } from '../../../shared/utils/format';
import { navigate } from '../../../app/router';
import './CheckoutPage.css';

const fulfillmentChoices = [
  { value: 'HOME_DELIVERY', title: 'Giao tận nơi', detail: 'Nhận tại địa chỉ của bạn', Icon: House },
  { value: 'COURT_DELIVERY', title: 'Giao đến sân', detail: 'Trong giờ chơi đã đặt', Icon: MapPin },
  { value: 'STORE_PICKUP', title: 'Nhận tại cửa hàng', detail: 'Chủ động đến lấy hàng', Icon: Store },
];

export default function CheckoutPage() {
  const { cart, setCart } = useCart();
  const [form, setForm] = useState({ name: '', phone: '', email: '', fulfillmentMethod: 'STORE_PICKUP', deliveryAddress: '', bookingCode: '' });
  const [error, setError] = useState('');
  const [quote, setQuote] = useState(null);
  const [busy, setBusy] = useState(false);
  const requestKey = useRef(crypto.randomUUID());
  const total = cart.reduce((sum, item) => sum + Number(item.lineTotal), 0);
  const count = cart.reduce((sum, item) => sum + item.quantity, 0);

  async function onQuote() {
    try { setQuote(await orderApi.quote(form.fulfillmentMethod)); setError(''); }
    catch (e) { setError(e.message); }
  }

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const bookingToken = form.bookingCode ? sessionStorage.getItem(bookingTokenKey(form.bookingCode)) : null;
      const order = await orderApi.create({ ...form, bookingToken }, requestKey.current);
      sessionStorage.setItem(orderTokenKey(order.orderCode), order.guestAccessToken);
      setCart([]); navigate(`/orders/${order.orderCode}`);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }

  function field(name, value) { setForm(current => ({ ...current, [name]: value })); }
  function selectFulfillment(value) {
    setForm(current => ({ ...current, fulfillmentMethod: value,
      bookingCode: value === 'COURT_DELIVERY' ? current.bookingCode : '',
      deliveryAddress: value === 'STORE_PICKUP' ? '' : current.deliveryAddress }));
    setQuote(null);
  }

  return <section className="container section checkout-page">
    <div className="checkout-heading">
      <a href="#/cart" className="checkout-back"><ArrowLeft size={16}/> Quay lại giỏ hàng</a>
      <span className="eyebrow">PICKLEZONE SHOP · THANH TOÁN AN TOÀN</span>
      <h1>Hoàn tất đơn hàng</h1>
      <p>Điền thông tin nhận hàng để PickleZone chuẩn bị đơn của bạn.</p>
    </div>

    {error && <p className="inline-message error" role="alert">{error}</p>}
    {!cart.length ? <div className="checkout-empty"><p>Giỏ hàng đang trống, hãy chọn sản phẩm trước khi thanh toán.</p><a className="btn primary" href="#/products">Xem sản phẩm <ArrowRight size={16}/></a></div>
      : <div className="checkout-layout">
        <form className="checkout-form" onSubmit={submit}>
          <section className="checkout-panel">
            <div className="checkout-section-title"><span>01</span><div><h2>Thông tin liên hệ</h2><p>Để chúng tôi xác nhận và cập nhật tình trạng đơn hàng.</p></div></div>
            <div className="checkout-contact-grid">
              <label>Họ và tên <i>*</i><input autoComplete="name" required value={form.name} onChange={e => field('name', e.target.value)} placeholder="Tên người nhận"/></label>
              <label>Số điện thoại <i>*</i><input autoComplete="tel" inputMode="tel" required pattern="[0-9+() .-]{9,16}" value={form.phone} onChange={e => field('phone', e.target.value)} placeholder="09xx xxx xxx"/></label>
              <label className="checkout-full-field">Email <span>(không bắt buộc)</span><input autoComplete="email" type="email" value={form.email} onChange={e => field('email', e.target.value)} placeholder="ban@example.com"/></label>
            </div>
          </section>

          <section className="checkout-panel">
            <div className="checkout-section-title"><span>02</span><div><h2>Cách nhận hàng</h2><p>Chọn cách nhận phù hợp với các sản phẩm trong giỏ.</p></div></div>
            <div className="fulfillment-options">
              {fulfillmentChoices.map(({ value, title, detail, Icon }) => <label key={value} className={`fulfillment-choice ${form.fulfillmentMethod === value ? 'selected' : ''}`}>
                <input type="radio" name="fulfillmentMethod" value={value} checked={form.fulfillmentMethod === value} onChange={() => selectFulfillment(value)}/>
                <span className="fulfillment-icon"><Icon size={19}/></span>
                <span className="fulfillment-copy"><b>{title}</b><small>{detail}</small></span>
                <span className="fulfillment-check"><Check size={14}/></span>
              </label>)}
            </div>
            {form.fulfillmentMethod === 'HOME_DELIVERY' && <label className="checkout-address-field">Địa chỉ giao hàng <i>*</i>
              <textarea autoComplete="street-address" required rows="3" value={form.deliveryAddress} onChange={e => field('deliveryAddress', e.target.value)} placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"/>
              <small>Vui lòng ghi địa chỉ chi tiết để nhân viên giao hàng tìm đúng nơi.</small>
            </label>}
            {form.fulfillmentMethod === 'COURT_DELIVERY' && <div className="checkout-delivery-fields">
              <label>Mã phiếu sân đang chơi <i>*</i><input required value={form.bookingCode} onChange={e => field('bookingCode', e.target.value.toUpperCase())} placeholder="Nhập mã phiếu đặt sân"/><small>Mã phiếu xác định sân nhận hàng và cần đang trong giờ chơi trên trình duyệt này.</small></label>
              <label>Ghi chú vị trí giao <span>(không bắt buộc)</span><textarea rows="2" value={form.deliveryAddress} onChange={e => field('deliveryAddress', e.target.value)} placeholder="Ví dụ: sân số 2, gần quầy lễ tân"/><small>Mã phiếu đã xác định sân; ghi thêm vị trí cụ thể nếu cần.</small></label>
            </div>}
            {form.fulfillmentMethod === 'STORE_PICKUP' && <div className="checkout-pickup-note"><Store size={17}/><span>Đơn hàng sẽ được chuẩn bị để bạn nhận tại cửa hàng PickleZone.</span></div>}
          </section>

          <div className="checkout-trust-note"><CircleHelp size={16}/><span>Phí giao hàng và tổng tiền cuối cùng sẽ được xác nhận trước khi đặt đơn.</span></div>
          <div className="checkout-form-actions">
            <button type="button" className="btn outline" onClick={onQuote}>Kiểm tra giá</button>
            <button className="btn primary" disabled={!cart.length || busy}>{busy ? 'Đang tạo đơn…' : 'Đặt hàng'} <ArrowRight size={17}/></button>
          </div>
        </form>

        <aside className="checkout-summary">
          <div className="checkout-summary-heading"><div><span className="eyebrow">ĐƠN HÀNG CỦA BẠN</span><h2>Tóm tắt</h2></div><span className="checkout-count">{count} món</span></div>
          <div className="checkout-summary-items">{cart.map(item => <div className="checkout-summary-item" key={item.itemId}>
            <ProductPhoto src={item.imageUrl} alt={item.productName} className="checkout-item-photo"/>
            <div><b>{item.productName}</b><small>{item.variantLabel && item.variantLabel !== 'Mặc định' ? item.variantLabel : item.sku} · SL {item.quantity}</small></div>
            <strong>{money(item.lineTotal)}</strong>
          </div>)}</div>
          <div className="checkout-summary-lines"><div><span>Tạm tính</span><b>{money(total)}</b></div><div><span>Giao hàng</span><span>{quote ? money(quote.deliveryFee) : 'Xác nhận sau'}</span></div></div>
          <div className="checkout-summary-total"><span>Tổng thanh toán</span><strong>{quote ? money(quote.totalAmount) : money(total)}</strong></div>
          {!quote && <p className="checkout-quote-hint">Kiểm tra giá để xem phí giao hàng áp dụng.</p>}
          <div className="checkout-secure"><Check size={15}/><span>Thông tin đơn hàng được bảo vệ an toàn.</span></div>
        </aside>
      </div>}
  </section>;
}
