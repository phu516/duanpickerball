import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { CheckCircle2, X } from 'lucide-react';
import { cartApi } from '../features/cart/api/cartApi';
import './toast.css';

const CartContext = createContext(null);

export function AppProviders({ children }) {
  const [cart, setCart] = useState([]);
  const [error, setError] = useState('');
  const [cartLoading, setCartLoading] = useState(true);
  const [notice, setNotice] = useState('');
  const noticeTimer = useRef(null);
  useEffect(() => { cartApi.open().then(setCart).catch(e => setError(e.message)).finally(() => setCartLoading(false)); }, []);
  useEffect(() => () => window.clearTimeout(noticeTimer.current), []);
  async function add(variantId, productName, quantity = 1) {
    setCart(await cartApi.add(variantId, quantity));
    setNotice(`${productName || 'Sản phẩm'} đã được thêm vào giỏ hàng`);
    window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(''), 3600);
  }
  async function change(itemId, quantity) {
    setCart(quantity ? await cartApi.change(itemId, quantity) : await cartApi.remove(itemId));
  }
  return <CartContext.Provider value={{ cart, setCart, add, change, error, setError, cartLoading }}>
    {children}
    {notice && <div className="shop-toast" role="status" aria-live="polite">
      <span className="shop-toast-icon"><CheckCircle2 size={20}/></span>
      <span className="shop-toast-message">{notice}</span>
      <button type="button" className="shop-toast-close" aria-label="Đóng thông báo" onClick={() => setNotice('')}><X size={17}/></button>
    </div>}
  </CartContext.Provider>;
}

export const useCart = () => useContext(CartContext);
