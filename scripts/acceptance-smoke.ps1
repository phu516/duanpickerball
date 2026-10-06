param([string]$Base = 'http://localhost:8080/api/v1')
$ErrorActionPreference = 'Stop'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Host "PASS: $Message"
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod "$Base/auth/csrf" -WebSession $session
$headers = @{ $csrf.headerName = $csrf.token }
$products = Invoke-RestMethod "$Base/products" -WebSession $session
$courts = Invoke-RestMethod "$Base/courts" -WebSession $session
Assert-True ($products.Count -ge 10) 'Faker/seed sản phẩm'
Assert-True ($courts.Count -ge 6) 'Seed 6 sân'
Assert-True (@($products | Where-Object category -eq 'Nước uống').Count -ge 1) 'Nước là sản phẩm'

$day = (Get-Date).AddDays((Get-Random -Minimum 30 -Maximum 150)).ToString('yyyy-MM-dd')
function BookingBody([long]$CourtId, [string]$Start, [string]$End) {
    return @{ courtId = $CourtId; name = 'Nguoi thu nghiem'; phone = '0901234567';
        startAt = "${day}T${Start}:00+07:00"; endAt = "${day}T${End}:00+07:00" } | ConvertTo-Json
}
function CreateBooking([long]$CourtId, [string]$Start, [string]$End) {
    return Invoke-RestMethod "$Base/guest/bookings" -Method Post -WebSession $session `
        -Headers $headers -ContentType 'application/json' -Body (BookingBody $CourtId $Start $End)
}

$first = CreateBooking $courts[0].id '10:00' '12:00'
$second = CreateBooking $courts[1].id '10:00' '12:00'
Assert-True ($first.bookingCode -ne $second.bookingCode) 'Một khách tạo nhiều phiếu độc lập'
$bookingKey = [guid]::NewGuid().ToString('N')
$bookingRequest = BookingBody $courts[3].id '10:00' '12:00'
$bookingHeaders = $headers.Clone()
$bookingHeaders['Idempotency-Key'] = $bookingKey
$bookingOnce = Invoke-RestMethod "$Base/guest/bookings" -Method Post -WebSession $session `
    -Headers $bookingHeaders -ContentType 'application/json' -Body $bookingRequest
$bookingAgain = Invoke-RestMethod "$Base/guest/bookings" -Method Post -WebSession $session `
    -Headers $bookingHeaders -ContentType 'application/json' -Body $bookingRequest
Assert-True ($bookingOnce.bookingCode -eq $bookingAgain.bookingCode) 'Gửi lại yêu cầu đặt sân không tạo phiếu thứ hai'
[void]$session.Headers.Remove('Idempotency-Key')
$adjacent = CreateBooking $courts[0].id '12:00' '14:00'
Assert-True ($adjacent.status -eq 'HOLD') 'Khung giờ nối tiếp hợp lệ'

try {
    CreateBooking $courts[0].id '11:00' '13:00' | Out-Null
    throw 'FAIL: Đặt trùng sân phải bị từ chối'
} catch {
    if ($_.Exception.Message -like 'FAIL:*') { throw }
    Assert-True ($_.Exception.Response.StatusCode -eq 409) 'Khung giờ chồng nhau bị từ chối'
}

try {
    CreateBooking $courts[2].id '10:00' '11:00' | Out-Null
    throw 'FAIL: Lượt dưới 2 giờ phải bị từ chối'
} catch {
    if ($_.Exception.Message -like 'FAIL:*') { throw }
    Assert-True ($_.Exception.Response.StatusCode -eq 400) 'Thời lượng tối thiểu 2 giờ'
}

try {
    Invoke-RestMethod "$Base/guest/bookings/$($first.bookingCode)" -WebSession $session | Out-Null
    throw 'FAIL: Đọc phiếu không token phải bị từ chối'
} catch {
    if ($_.Exception.Message -like 'FAIL:*') { throw }
    Assert-True ($_.Exception.Response.StatusCode -in @(400, 401, 403)) 'Không đọc phiếu khi thiếu token'
}

$headers['X-Guest-Access-Token'] = $first.guestAccessToken
$cancelled = Invoke-RestMethod "$Base/guest/bookings/$($first.bookingCode)/cancel" -Method Post `
    -WebSession $session -Headers $headers
Assert-True ($cancelled.status -eq 'CANCELLED') 'Hủy HOLD giải phóng lịch'
$headers['X-Guest-Access-Token'] = $second.guestAccessToken
$stillHeld = Invoke-RestMethod "$Base/guest/bookings/$($second.bookingCode)" -WebSession $session -Headers $headers
Assert-True ($stillHeld.status -eq 'HOLD') 'Hủy phiếu này không ảnh hưởng phiếu khác'

$payment = Invoke-RestMethod "$Base/guest/bookings/$($second.bookingCode)/payments" -Method Post `
    -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"purpose":"DEPOSIT"}'
$paid = Invoke-RestMethod "$Base/dev/payments/$($payment.paymentId)/complete" -Method Post `
    -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{"success":true}'
$confirmed = Invoke-RestMethod "$Base/guest/bookings/$($second.bookingCode)" -WebSession $session -Headers $headers
Assert-True ($paid.status -eq 'SUCCEEDED' -and $confirmed.status -eq 'CONFIRMED') 'Cọc sandbox xác nhận phiếu'

$headers.Remove('X-Guest-Access-Token')
Invoke-RestMethod "$Base/guest/cart" -Method Post -WebSession $session -Headers $headers | Out-Null
$line = Invoke-RestMethod "$Base/guest/cart/items" -Method Post -WebSession $session `
    -Headers $headers -ContentType 'application/json' -Body (@{ variantId = $products[0].variantId; quantity = 2 } | ConvertTo-Json)
Assert-True ($line.Count -eq 1) 'Thêm SKU vào giỏ'
$orderHeaders = $headers.Clone()
$orderHeaders['Idempotency-Key'] = [guid]::NewGuid().ToString('N')
$orderRequest = @{ name = 'Nguoi thu nghiem'; phone = '0901234567'; fulfillmentMethod = 'STORE_PICKUP' } | ConvertTo-Json
$order = Invoke-RestMethod "$Base/guest/orders" -Method Post -WebSession $session `
    -Headers $orderHeaders -ContentType 'application/json' -Body $orderRequest
Assert-True ($order.totalAmount -eq 2 * $products[0].price -and $order.guestAccessToken) 'Checkout giữ kho và cấp token riêng'
$orderAgain = Invoke-RestMethod "$Base/guest/orders" -Method Post -WebSession $session `
    -Headers $orderHeaders -ContentType 'application/json' -Body $orderRequest
Assert-True ($order.orderCode -eq $orderAgain.orderCode) 'Gửi lại checkout không tạo đơn thứ hai'

Write-Host 'Hoàn tất smoke test. Các phiếu/đơn tạo trong profile dev được lưu để admin kiểm tra.'
