import { createContext, useContext, useEffect, useState } from 'react';
import { cartApi } from '../features/cart/api/cartApi';

const CartContext = createContext(null);

export function AppProviders({ children }) {
  const [cart, setCart] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => { cartApi.open().then(setCart).catch(e => setError(e.message)); }, []);
  async function add(variantId) { setCart(await cartApi.add(variantId)); }
  async function change(itemId, quantity) {
    setCart(quantity ? await cartApi.change(itemId, quantity) : await cartApi.remove(itemId));
  }
  return <CartContext.Provider value={{ cart, setCart, add, change, error, setError }}>
    {children}
  </CartContext.Provider>;
}

export const useCart = () => useContext(CartContext);
