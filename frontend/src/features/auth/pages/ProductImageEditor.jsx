import { useEffect, useState } from 'react';
import { productApi } from '../../products/api/productApi';
import ProductPhoto from '../../products/components/ProductPhoto';
import './ProductImageEditor.css';

export default function ProductImageEditor({ products, onChanged }) {
  const [productId, setProductId] = useState('');
  const [variantId, setVariantId] = useState('');
  const [url, setUrl] = useState('');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const product = products.find(item => item.id === Number(productId));
  const variant = product?.variants.find(item => item.id === Number(variantId));

  useEffect(() => {
    setUrl(variantId ? variant?.imageUrl || '' : product?.imageUrl || '');
  }, [productId, variantId, product?.imageUrl, variant?.imageUrl]);

  async function save(event) {
    event.preventDefault();
    setSaving(true);
    setMessage('');
    try {
      if (variantId) await productApi.updateVariantImage(Number(variantId), url.trim());
      else await productApi.updateImage(Number(productId), url.trim());
      await onChanged();
      setMessage('Đã lưu ảnh sản phẩm.');
    } catch (error) { setMessage(error.message); }
    finally { setSaving(false); }
  }

  return <form className="detail-card product-image-editor" onSubmit={save}>
    <h2>Chỉnh ảnh sản phẩm</h2>
    <p>Chọn sản phẩm để lưu ảnh chính, hoặc chọn thêm phiên bản để đặt ảnh riêng cho mẫu đó.</p>
    <div className="product-image-fields">
      <label>Sản phẩm<select required value={productId} onChange={event => { setProductId(event.target.value); setVariantId(''); }}><option value="">Chọn sản phẩm</option>{products.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <label>Phiên bản<select value={variantId} disabled={!product} onChange={event => setVariantId(event.target.value)}><option value="">Ảnh chính của sản phẩm</option>{product?.variants.map(item => <option key={item.id} value={item.id}>{item.label} · {item.sku}</option>)}</select></label>
    </div>
    <label>URL ảnh<input type="url" maxLength="500" placeholder="https://images.example.com/san-pham.jpg" value={url} onChange={event => setUrl(event.target.value)}/></label>
    {product && <ProductPhoto src={url || product.imageUrl} alt={`Ảnh xem trước ${product.name}`} className="product-image-preview"/>}
    <button className="btn primary" disabled={!product || saving}>{saving ? 'Đang lưu…' : 'Lưu ảnh'}</button>
    {message && <p role="status" className="inline-message">{message}</p>}
  </form>;
}
