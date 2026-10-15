import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, ShoppingBag } from 'lucide-react';
import { productApi } from '../api/productApi';
import { useCart } from '../../../app/providers';
import { money } from '../../../shared/utils/format';
import ProductPhoto from '../components/ProductPhoto';
import './ProductsPage.css';

export default function ProductsPage({ featured = false }) {
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState('Tất cả');
  const [keyword, setKeyword] = useState('');
  const [message, setMessage] = useState('');
  const { add } = useCart();
  useEffect(() => { productApi.list().then(setProducts).catch(e => setMessage(e.message)); }, []);
  const categories = ['Tất cả', ...new Set(products.map(item => item.category))];
  const visible = useMemo(() => products.filter(item => (category === 'Tất cả' || item.category === category)
    && `${item.name} ${item.brand}`.toLowerCase().includes(keyword.toLowerCase())), [products, category, keyword]);
  async function onAdd(item, variantId) {
    setMessage('');
    try { await add(variantId, item.name); }
    catch (error) { setMessage(error.message); }
  }
  return <section className="container section">
    <div className="eyebrow">PICKLEZONE SHOP</div><div className="section-heading"><div><h1>{featured ? 'Trang bị cho trận đấu' : 'Cửa hàng pickleball'}</h1><p>Vợt, trang phục, phụ kiện và nước uống đều nằm trong một cửa hàng.</p></div>{featured && <a className="text-link" href="#/products">Xem tất cả <ArrowRight size={18}/></a>}</div>
    {!featured && <div className="filter-row"><input value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="Tìm sản phẩm…"/><div>{categories.map(name => <button key={name} className={category === name ? 'chip active' : 'chip'} onClick={() => setCategory(name)}>{name}</button>)}</div></div>}
    {message && <p className="inline-message">{message}</p>}
    <div className="product-grid">{(featured ? visible.slice(0, 4) : visible).map(item => <ProductCard key={item.id} item={item} onAdd={onAdd}/>)}</div>
  </section>;
}

function ProductCard({ item, onAdd }) {
  const options = item.variants?.filter(variant => variant.active) || [];
  const [variantId, setVariantId] = useState(item.variantId);
  const selected = options.find(variant => variant.id === Number(variantId));
  return <article className="product-card"><a className="product-card-photo-link" href={`#/products/${item.id}`} aria-label={`Xem chi tiết ${item.name}`}><ProductPhoto src={selected?.imageUrl || item.imageUrl} alt={item.name}/></a><div className="product-body"><small>{item.brand} · {item.category}</small><h3><a href={`#/products/${item.id}`}>{item.name}</a></h3><p>{item.description}</p>{options.length > 1 && <select aria-label={`Phiên bản ${item.name}`} value={variantId} onChange={event => setVariantId(Number(event.target.value))}>{options.map(variant => <option key={variant.id} value={variant.id}>{variant.label}</option>)}</select>}<div className="product-bottom"><strong>{money(selected?.price ?? item.price)}</strong><button className="icon-action" onClick={() => onAdd(item, variantId)} disabled={(selected?.stock ?? item.stock) < 1} title="Thêm vào giỏ"><ShoppingBag size={18}/></button></div><small>{(selected?.stock ?? item.stock) > 0 ? `Còn ${selected?.stock ?? item.stock} sản phẩm` : 'Hết hàng'}</small><a className="product-detail-link" href={`#/products/${item.id}`}>Xem chi tiết <ArrowRight size={15}/></a></div></article>;
}
