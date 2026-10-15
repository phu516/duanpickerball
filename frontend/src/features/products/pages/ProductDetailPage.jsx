import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Check, Minus, Plus, ShoppingBag, Truck } from 'lucide-react';
import { productApi } from '../api/productApi';
import { useCart } from '../../../app/providers';
import { money } from '../../../shared/utils/format';
import ProductPhoto from '../components/ProductPhoto';
import './ProductDetailPage.css';

const fulfillmentLabels = {
  HOME_DELIVERY: 'Giao tận nơi',
  COURT_DELIVERY: 'Giao đến sân',
  STORE_PICKUP: 'Nhận tại cửa hàng',
};

export default function ProductDetailPage({ id }) {
  const [product, setProduct] = useState(null);
  const [variantId, setVariantId] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { add } = useCart();

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    productApi.detail(id)
      .then(data => {
        if (!active) return;
        setProduct(data);
        setVariantId(data.variantId);
      })
      .catch(problem => { if (active) setError(problem.message || 'Không tìm thấy sản phẩm.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id]);

  const variants = useMemo(() => product?.variants?.filter(item => item.active) || [], [product]);
  const selected = variants.find(item => item.id === Number(variantId)) || product;
  const stock = selected?.stock ?? 0;
  const imageUrl = selected?.imageUrl || product?.imageUrl;

  async function addToCart() {
    try {
      await add(selected.id, product.name, quantity);
      setError('');
    } catch (problem) {
      setError(problem.message || 'Không thể thêm sản phẩm vào giỏ hàng.');
    }
  }

  if (loading) return <section className="container section product-detail-state"><p>Đang tải thông tin sản phẩm…</p></section>;
  if (error && !product) return <section className="container section product-detail-state"><p className="inline-message error">{error}</p><a className="btn outline" href="#/products"><ArrowLeft size={16}/> Quay lại cửa hàng</a></section>;

  return <section className="container section product-detail-page">
    <a href="#/products" className="product-detail-back"><ArrowLeft size={16}/> Cửa hàng</a>
    {error && <p className="inline-message error" role="alert">{error}</p>}
    <div className="product-detail-layout">
      <div className="product-detail-gallery">
        <ProductPhoto src={imageUrl} alt={product.name} className="product-detail-photo"/>
        <div className="product-detail-photo-caption"><Check size={16}/> Ảnh sản phẩm từ danh mục PickleZone</div>
      </div>
      <div className="product-detail-info">
        <span className="eyebrow">{product.category}</span>
        <p className="product-detail-brand">{product.brand}</p>
        <h1>{product.name}</h1>
        <p className="product-detail-description">{product.description || 'Sản phẩm chính hãng tại PickleZone.'}</p>
        <div className="product-detail-price">{money(selected?.price ?? product.price)}</div>
        <div className={`product-detail-stock${stock < 1 ? ' is-out' : ''}`}>
          <span className="product-stock-dot"/>{stock > 0 ? `Còn ${stock} sản phẩm` : 'Tạm hết hàng'}
          {selected?.sku && <span className="product-detail-sku">SKU: {selected.sku}</span>}
        </div>

        {variants.length > 1 && <label className="product-detail-variant">Phiên bản
          <select value={variantId ?? ''} onChange={event => { setVariantId(Number(event.target.value)); setQuantity(1); }}>
            {variants.map(variant => <option key={variant.id} value={variant.id}>{variant.label} · {variant.sku}</option>)}
          </select>
        </label>}

        <div className="product-detail-buy-row">
          <div className="product-detail-quantity" aria-label="Số lượng">
            <button type="button" onClick={() => setQuantity(value => Math.max(1, value - 1))} disabled={quantity <= 1} aria-label="Giảm số lượng"><Minus size={16}/></button>
            <span>{quantity}</span>
            <button type="button" onClick={() => setQuantity(value => Math.min(stock, value + 1))} disabled={quantity >= stock} aria-label="Tăng số lượng"><Plus size={16}/></button>
          </div>
          <button type="button" className="btn primary product-detail-add" onClick={addToCart} disabled={!stock}>
            <ShoppingBag size={18}/> Thêm vào giỏ
          </button>
        </div>

        <div className="product-detail-delivery">
          <div className="product-detail-delivery-icon"><Truck size={19}/></div>
          <div><b>Cách nhận hàng</b><p>{(product.fulfillmentOptions || []).map(method => fulfillmentLabels[method] || method).join(' · ') || 'Liên hệ cửa hàng để biết thêm'}</p></div>
        </div>
      </div>
    </div>
  </section>;
}
