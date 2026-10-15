import { useEffect, useState } from 'react';
import './ProductPhoto.css';

export default function ProductPhoto({ src, alt, className = '' }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  return <div className={`product-photo ${className}`}>
    {src && !failed
      ? <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)}/>
      : <span className="product-photo-empty" role="img" aria-label={`Chưa có ảnh cho ${alt}`}>Ảnh chưa cập nhật</span>}
  </div>;
}
