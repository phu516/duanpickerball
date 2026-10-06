import { useState } from 'react';
import { useCart } from '../../../app/providers';
import { money } from '../../../shared/utils/format';

export default function CartPage() {
  const { cart, change } = useCart();
  const [error, setError] = useState('');
  const total = cart.reduce((sum, line) => sum + Number(line.lineTotal), 0);
  async function update(itemId, quantity) { try { await change(itemId, quantity); } catch (e) { setError(e.message); } }
  return <section className="container section narrow"><span className="eyebrow">GIỎ HÀNG</span><h1>Sản phẩm đã chọn</h1>{error && <p className="inline-message error">{error}</p>}{cart.length ? <><div className="detail-card">{cart.map(line => <div className="cart-line" key={line.itemId}><div><b>{line.productName}</b><small>{line.sku} · {money(line.unitPrice)}</small></div><div className="quantity"><button onClick={() => update(line.itemId, line.quantity - 1)}>−</button><b>{line.quantity}</b><button onClick={() => update(line.itemId, line.quantity + 1)}>+</button></div><strong>{money(line.lineTotal)}</strong></div>)}<div className="detail-row total"><span>Tạm tính</span><b>{money(total)}</b></div></div><a className="btn primary" href="#/checkout">Tiếp tục đặt hàng →</a></> : <div className="empty-card"><p>Giỏ hàng đang trống.</p><a className="btn primary" href="#/products">Xem sản phẩm</a></div>}</section>;
}
