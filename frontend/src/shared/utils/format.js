export const money = value => `${new Intl.NumberFormat('vi-VN').format(value || 0)} ₫`;
export const localDate = date => new Date(date).toLocaleDateString('en-CA');
export const localTime = date => {
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return '—';
  return value.toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
};
export const localDateTime = date => {
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return '—';
  const datePart = value.toLocaleDateString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
  });
  const timePart = value.toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
  });
  return `${datePart} ${timePart}`;
};
export const bookingTokenKey = code => `picklezone.booking.${code}`;
export const bookingPaymentSuccessKey = code => `picklezone.booking.payment-success.${code}`;
export const orderTokenKey = code => `picklezone.order.${code}`;
