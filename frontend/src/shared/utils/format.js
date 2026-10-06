export const money = value => `${new Intl.NumberFormat('vi-VN').format(value || 0)} ₫`;
export const localDate = date => new Date(date).toLocaleDateString('en-CA');
export const localTime = date => new Date(date).toLocaleString('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
  day: '2-digit', month: '2-digit', year: 'numeric',
});
export const bookingTokenKey = code => `picklezone.booking.${code}`;
export const orderTokenKey = code => `picklezone.order.${code}`;
