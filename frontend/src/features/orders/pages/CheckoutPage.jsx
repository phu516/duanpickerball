import { useRef, useState } from 'react';
import { useCart } from '../../../app/providers';
import { orderApi } from '../api/orderApi';
import { bookingTokenKey, money, orderTokenKey } from '../../../shared/utils/format';
import { navigate } from '../../../app/router';

export default function CheckoutPage() {
  const { cart, setCart } = useCart();
  const [form, setForm] = useState({ name: '', phone: '', email: '', fulfillmentMethod: 'STORE_PICKUP', deliveryAddress: '', bookingCode: '' });
  const [error, setError] = useState('');
  const [quote, setQuote] = useState(null);
  const [busy, setBusy] = useState(false);
  const requestKey = useRef(crypto.randomUUID());
  const total = cart.reduce((sum, item) => sum + Number(item.lineTotal), 0);
  async function onQuote() { try { setQuote(await orderApi.quote(form.fulfillmentMethod)); setError(''); } catch (e) { setError(e.message); } }
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
  const field = (name, value) => setForm(current => ({ ...current, [name]: value }));
  return <section className="container section narrow"><span className="eyebrow">CHECKOUT KHÁCH VÃNG LAI</span><h1>Hoàn tất đơn hàng</h1><p>Không cần đăng nhập. Chọn cách nhận phù hợp với tất cả sản phẩm trong giỏ.</p>{error && <p className="inline-message error">{error}</p>}
    <form className="detail-card form-stack" onSubmit={submit}><label>Họ tên *<input required value={form.name} onChange={e => field('name', e.target.value)}/></label><label>Số điện thoại *<input required pattern="[0-9+() .-]{9,16}" value={form.phone} onChange={e => field('phone', e.target.value)}/></label><label>Email<input type="email" value={form.email} onChange={e => field('email', e.target.value)}/></label><label>Cách nhận<select value={form.fulfillmentMethod} onChange={e => { field('fulfillmentMethod', e.target.value); setQuote(null); }}><option value="STORE_PICKUP">Nhận tại cửa hàng</option><option value="COURT_DELIVERY">Nhân viên giao đến sân</option><option value="HOME_DELIVERY">Giao đến địa chỉ</option></select></label>
      {form.fulfillmentMethod === 'HOME_DELIVERY' && <label>Địa chỉ giao hàng *<input required value={form.deliveryAddress} onChange={e => field('deliveryAddress', e.target.value)}/></label>}
      {form.fulfillmentMethod === 'COURT_DELIVERY' && <label>Mã phiếu sân đang chơi *<input required value={form.bookingCode} onChange={e => field('bookingCode', e.target.value.toUpperCase())}/><small>Phiếu phải được nhận sân và đang trong giờ chơi trên trình duyệt này.</small></label>}
      <div className="quote"><div><span>Tạm tính</span><b>{money(total)}</b></div><div><span>Giá backend</span><b>{quote ? money(quote.totalAmount) : 'Kiểm tra trước khi đặt'}</b></div></div><div className="button-row"><button type="button" className="btn outline" onClick={onQuote}>Kiểm tra giá</button><button className="btn primary" disabled={!cart.length || busy}>{busy ? 'Đang đặt hàng…' : 'Tạo đơn hàng'}</button></div></form>
  </section>;
}
