import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, ShoppingBag, Trash2 } from 'lucide-react';
import { useCart } from '../../../app/providers';
import ProductPhoto from '../../products/components/ProductPhoto';
import { money } from '../../../shared/utils/format';
import './CartPage.css';

export default function CartPage() {
  const { cart, change, error: cartError, cartLoading } = useCart();
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);
  useEffect(() => { window.scrollTo(0, 0); }, []);
  const count = cart.reduce((sum, line) => sum + line.quantity, 0);
  const total = cart.reduce((sum, line) => sum + Number(line.lineTotal), 0);

  async function update(itemId, quantity) {
    if (busyId !== null) return;
    setBusyId(itemId);
    setError('');
    try { await change(itemId, quantity); }
    catch (e) { setError(e.message); }
    finally { setBusyId(null); }
  }

  return <section className="container section cart-page">
    <div className="cart-page-heading">
      <div><span className="eyebrow">PICKLEZONE SHOP</span><h1>Giỏ hàng của bạn</h1><p>{cart.length ? `${count} sản phẩm đã sẵn sàng cho trận đấu` : 'Chọn món đồ yêu thích cho trận đấu tiếp theo.'}</p></div>
      <a href="#/products" className="cart-back"><ArrowLeft size={17}/> Tiếp tục mua sắm</a>
    </div>
    {(error || cartError) && <p className="inline-message error" role="alert">{error || cartError}</p>}
    {cartLoading ? <div className="cart-empty" role="status">Đang tải giỏ hàng…</div>
      : cart.length ? <div className="cart-layout">
        <div className="cart-items" aria-label="Sản phẩm trong giỏ">
          <div className="cart-items-heading"><h2>Sản phẩm đã chọn</h2><span>{cart.length} mặt hàng</span></div>
          {cart.map(line => <article className="cart-item" key={line.itemId}>
            <ProductPhoto src={line.imageUrl} alt={line.productName} className="cart-item-photo"/>
            <div className="cart-item-info">
              <h3>{line.productName}</h3>
              <p>{line.variantLabel && line.variantLabel !== 'Mặc định' ? line.variantLabel : `SKU: ${line.sku}`}</p>
              <span className="cart-unit-price">{money(line.unitPrice)} / sản phẩm</span>
              <button className="cart-remove" type="button" onClick={() => update(line.itemId, 0)} disabled={busyId !== null} aria-label={`Xóa ${line.productName} khỏi giỏ`}><Trash2 size={15}/> Xóa</button>
            </div>
            <div className="cart-item-actions">
              <strong>{money(line.lineTotal)}</strong>
              <div className="cart-quantity" aria-label={`Số lượng ${line.productName}`}>
                <button type="button" onClick={() => update(line.itemId, line.quantity - 1)} disabled={busyId !== null} aria-label={`Giảm số lượng ${line.productName}`}>−</button>
                <span>{line.quantity}</span>
                <button type="button" onClick={() => update(line.itemId, line.quantity + 1)} disabled={busyId !== null || line.quantity >= line.available} aria-label={`Tăng số lượng ${line.productName}`}>+</button>
              </div>
            </div>
          </article>)}
        </div>
        <aside className="cart-summary">
          <span className="eyebrow">TỔNG KẾT ĐƠN HÀNG</span>
          <h2>Thanh toán</h2>
          <div className="cart-summary-row"><span>Sản phẩm ({count})</span><b>{money(total)}</b></div>
          <div className="cart-summary-row"><span>Phí giao hàng</span><span>Tính ở bước tiếp theo</span></div>
          <div className="cart-summary-total"><span>Tạm tính</span><strong>{money(total)}</strong></div>
          <p>Giá cuối cùng và cách nhận hàng sẽ được xác nhận trước khi đặt.</p>
          <a className="btn primary cart-checkout" href="#/checkout">Tiếp tục đặt hàng <ArrowRight size={17}/></a>
        </aside>
      </div> : <div className="cart-empty">
        <div className="cart-empty-icon"><ShoppingBag size={36} strokeWidth={1.5}/></div>
        <h2>Giỏ hàng đang trống</h2>
        <p>Khám phá vợt, trang phục và phụ kiện cho buổi chơi tiếp theo.</p>
        <a className="btn primary" href="#/products">Khám phá sản phẩm <ArrowRight size={17}/></a>
      </div>}
  </section>;
}
