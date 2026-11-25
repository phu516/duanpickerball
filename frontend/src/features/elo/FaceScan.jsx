import { useEffect, useRef, useState } from 'react';

const base = (import.meta.env.VITE_FACE_API_URL || '/face-api').replace(/\/$/, '');
const prompts = {
  front: 'Nhìn thẳng vào camera',
  left: 'Quay nhẹ sang trái của bạn',
  right: 'Quay nhẹ sang phải của bạn',
};

async function post(path, signal, body) {
  let response;
  try {
    response = await fetch(base + path, { method: 'POST', signal, body });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new Error('Không kết nối được dịch vụ quét mặt. Hãy kiểm tra dịch vụ ở cổng 8001 rồi thử lại.');
  }
  const contentType = response.headers.get('content-type') || '';
  const responseText = await response.text();
  let result = null;
  if (contentType.includes('json')) {
    try { result = JSON.parse(responseText); } catch { /* Keep the raw body for diagnostics below. */ }
  }
  if (!response.ok) {
    if (result?.detail) throw new Error(result.detail);
    if (response.status === 404) {
      throw new Error('Không tìm thấy API quét mặt. Hãy khởi động lại Vite để áp dụng proxy /face-api tới cổng 8001.');
    }
    if (response.status >= 500) {
      const detail = responseText.trim().replace(/\s+/g, ' ').slice(0, 240);
      throw new Error(`HTTP ${response.status} từ ${new URL(response.url).host}${detail ? `: ${detail}` : ` (${contentType || 'không có Content-Type'})`}.`);
    }
    throw new Error(`Dịch vụ quét mặt từ chối yêu cầu (HTTP ${response.status}). Vui lòng thử lại.`);
  }
  if (!result) throw new Error('Dịch vụ quét mặt trả về dữ liệu không hợp lệ.');
  return result;
}

export default function FaceScan({ purpose, onStart, onVerified, disabled }) {
  const dialog = useRef(null);
  const video = useRef(null);
  const stream = useRef(null);
  const active = useRef(null);
  const [prompt, setPrompt] = useState('');
  const [error, setError] = useState('');
  const [completed, setCompleted] = useState(0);

  function stop() {
    active.current?.abort();
    active.current = null;
    stream.current?.getTracks().forEach(track => track.stop());
    stream.current = null;
    if (video.current) video.current.srcObject = null;
  }

  useEffect(() => stop, []);

  async function scan() {
    stop();
    const session = new AbortController();
    const { signal } = session;
    active.current = session;
    onStart();
    setError('');
    setCompleted(0);
    setPrompt('Đang mở camera…');
    if (!dialog.current.open) dialog.current.showModal();
    try {
      const media = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false });
      if (signal.aborted) { media.getTracks().forEach(track => track.stop()); return; }
      stream.current = media;
      const preview = video.current;
      preview.srcObject = media;
      await preview.play();
      if (!preview.videoWidth || !preview.videoHeight) {
        await new Promise((resolve, reject) => {
          const cleanup = () => {
            preview.removeEventListener('loadedmetadata', onReady);
            signal.removeEventListener('abort', onAbort);
          };
          const onReady = () => { cleanup(); resolve(); };
          const onAbort = () => { cleanup(); reject(new DOMException('Đã hủy quét.', 'AbortError')); };
          preview.addEventListener('loadedmetadata', onReady, { once: true });
          signal.addEventListener('abort', onAbort, { once: true });
          if (preview.videoWidth && preview.videoHeight) onReady();
        });
      }
      const { challenge, pose } = await post('/challenge/' + purpose, signal);
      setPrompt(prompts[pose]);
      const canvas = document.createElement('canvas');
      canvas.width = Math.min(480, preview.videoWidth);
      canvas.height = Math.round(preview.videoHeight * canvas.width / preview.videoWidth);
      while (!signal.aborted) {
        await new Promise(resolve => setTimeout(resolve, 120));
        if (signal.aborted) return;
        canvas.getContext('2d').drawImage(preview, 0, 0, canvas.width, canvas.height);
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .72));
        if (!blob) throw new Error('Không đọc được hình từ camera.');
        const body = new FormData();
        body.append('image', blob, 'frame.jpg');
        const step = await post('/frame/' + challenge, signal, body);
        setCompleted(step.completed);
        if (step.proof) {
          media.getTracks().forEach(track => track.stop());
          setPrompt('Đang xác minh…');
          await onVerified(step.proof, signal);
          if (!signal.aborted) dialog.current?.close();
          return;
        }
        setPrompt(step.hint || prompts[step.pose]);
      }
    } catch (err) {
      if (!signal.aborted) {
        setError(err.name === 'NotAllowedError' ? 'Hãy cho phép trình duyệt dùng camera.' : err.message);
        stop();
      }
    }
  }

  return <>
    <button type="button" className="btn outline" disabled={disabled} onClick={scan}>Quét khuôn mặt</button>
    <dialog ref={dialog} className="elo-scan" onClose={stop}>
      <div className="elo-camera-dialog">
        <div className="elo-camera-heading"><h2>Quét khuôn mặt</h2><button type="button" className="elo-camera-close" aria-label="Đóng camera" onClick={() => dialog.current.close()}>×</button></div>
        <video ref={video} playsInline muted autoPlay/>
        <div className="elo-scan-progress" role="progressbar" aria-label="Tiến độ quét khuôn mặt" aria-valuemin="0" aria-valuemax="3" aria-valuenow={completed}>
          {[0, 1, 2].map(index => <span key={index} className={index < completed ? 'complete' : ''}/>) }
        </div>
        <p className={`elo-scan-prompt${error ? ' elo-scan-prompt-error' : ''}`} role="status" aria-live="polite">{error || prompt}</p>
        {error && <button type="button" className="btn primary" onClick={scan}>Quét lại</button>}
      </div>
    </dialog>
  </>;
}
