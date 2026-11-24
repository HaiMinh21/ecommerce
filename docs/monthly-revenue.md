# Thống kê doanh thu theo tháng

GET /api/v1/orders/admin/revenue/monthly?year=2026

Bắt buộc truyền year (1000–9998). Kết quả có year, currency = VND và months gồm đủ 12
phần tử theo thứ tự tháng, mỗi phần tử có month, totalOrders, revenue.
Tháng không có dữ liệu trả totalOrders = 0 và revenue = 0.

Chỉ tính đơn hiện có status = DELIVERED, theo deliveredAt trong khoảng
[01/01/year 00:00, 01/01/(year+1) 00:00). Dùng múi giờ local của ứng dụng,
giống các timestamp LocalDateTime hiện có.
Doanh thu là SUM(finalAmount), đã trừ giảm giá và bao gồm phí vận chuyển.
Đơn RETURNED không được tính; vì vậy báo cáo các tháng trước có thể giảm sau khi trả hàng.

deliveredAt được ghi khi chuyển SHIPPING -> DELIVERED, được giữ khi chuyển RETURNED.
Hibernate ddl-auto=update hiện tại sẽ thêm cột delivered_at nullable khi khởi động.
Các đơn cũ thiếu delivered_at bị loại khỏi thống kê tháng; không suy đoán ngày giao
từ createdAt hoặc updatedAt. Chỉ bổ sung ngày giao cũ từ dữ liệu đã được xác minh.
Vì vậy tổng 12 tháng có thể khác API tổng doanh thu hiện có nếu có đơn cũ thiếu ngày giao.

API theo đường dẫn admin hiện có; dự án chưa có cơ chế xác thực/phân quyền.
Cần bổ sung phân quyền ADMIN trước khi công khai các API quản trị.