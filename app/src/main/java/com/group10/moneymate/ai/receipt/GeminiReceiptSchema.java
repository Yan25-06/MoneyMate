package com.group10.moneymate.ai.receipt;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

final class GeminiReceiptSchema {

    private GeminiReceiptSchema() {
    }

    @NonNull
    static String buildPrompt(@NonNull List<String> allowedCategories) {
        StringBuilder builder = new StringBuilder();
        builder.append("Bạn là bộ trích xuất dữ liệu hóa đơn tiếng Việt cho ứng dụng quản lý chi tiêu.\n");
        builder.append("Bạn đang nhìn trực tiếp ảnh hóa đơn, có thể có 2 ảnh của cùng một bill: 1 ảnh gốc màu và 1 ảnh tăng tương phản để đọc chữ nhỏ.\n");
        builder.append("Ảnh có thể chứa tiếng Việt có dấu hoặc không dấu; hãy hiểu chúng là cùng một nghĩa.\n");
        builder.append("Hãy hiểu bố cục theo hàng/cột, nhất là cột giá hoặc cột 'Tiền' nằm bên phải.\n");
        builder.append("Danh mục hợp lệ của ứng dụng là: ")
                .append(TextListFormatter.joinQuoted(allowedCategories))
                .append(".\n");
        builder.append("Quy tắc bắt buộc:\n");
        builder.append("1. Chỉ trả về JSON hợp lệ, không thêm markdown, không thêm giải thích.\n");
        builder.append("2. total phải là tổng tiền thanh toán cuối cùng của cả bill, tính theo VND integer, KHÔNG có dấu phân cách. Ví dụ: '54,000' hoặc '54.000' phải trả về 54000.\n");
        builder.append("3. Hãy quét toàn bộ ảnh và liệt kê mọi dòng total-like vào total_candidates theo thứ tự từ trên xuống dưới.\n");
        builder.append("4. Ưu tiên total cuối cùng có ý nghĩa thanh toán như 'Tổng cộng', 'Tổng thanh toán', 'Grand total', 'Amount due', 'Phải trả'.\n");
        builder.append("5. Không dùng VAT, thuế, service charge, phụ thu, surcharge, tip, thành tiền trung gian, tạm tính, tiền khách đưa, tiền thừa, change, cash, received làm total cuối cùng.\n");
        builder.append("6. Nếu không có total rõ ràng, cộng các dòng mặt hàng có giá tiền để suy ra total.\n");
        builder.append("6a. Nếu bill có nhiều lớp tiền như 'Tổng cộng 500000', 'VAT 50000', 'Service charge 50000', 'Thanh toán 550000' thì total bắt buộc là 550000 vì đó là số cuối cùng phải trả.\n");
        builder.append("6b. Nếu có cả 'Tổng cộng' và 'Thanh toán' thì ưu tiên 'Thanh toán', 'Phải trả', 'Amount due', 'Grand total', 'Balance due' hơn 'Tổng cộng' hoặc 'Thành tiền'.\n");
        builder.append("7. Với items, price phải là thành tiền cuối cùng của dòng đó, không phải số lượng. Nếu có SL và đơn giá riêng thì ưu tiên cột 'Tiền' hoặc cột phải nhất. item.category nếu có cũng phải thuộc danh sách danh mục hợp lệ.\n");
        builder.append("8. merchant chỉ là tên cửa hàng/quán, không kèm nhân viên, bàn, giờ vào, mã số, số điện thoại.\n");
        builder.append("9. category_hint phải là đúng MỘT trong danh sách danh mục hợp lệ bên trên, viết đúng chính tả và dấu tiếng Việt như danh sách đó.\n");
        builder.append("10. Hãy suy luận category_hint từ TOÀN BỘ ảnh: merchant, item names, dòng mô tả, và bố cục hóa đơn; không chỉ nhìn một dòng đơn lẻ.\n");
        builder.append("11. Ví dụ suy luận danh mục: cafe, trà sữa, nhà hàng, sprite, coca, pepsi, soda => 'Ăn uống'; grab, taxi, xăng, vé máy bay => 'Di chuyển'; circle k, gs25, winmart, supermarket, shopee, lazada => 'Mua sắm'; điện, nước, internet, viettel, fpt => 'Hoá đơn'; CGV, Lotte Cinema, Galaxy Cinema, ticket, thẻ vào phòng chiếu phim, gold class => 'Giải trí'; bệnh viện, nhà thuốc, Pharmacity => 'Y tế'; học phí, IELTS, TOEIC => 'Giáo dục'; spa, nail, salon => 'Làm đẹp'; gym, yoga, pilates => 'Thể thao'; pet, vet, dog, cat => 'Thú cưng'.\n");
        builder.append("12. note_hint phải là mô tả giao dịch ngắn gọn, có nghĩa, bằng tiếng Việt. Ví dụ: 'Chi tiêu tại CÀ PHÊ HOÀNG PHÚC - Cà phê đá, bún thịt xào'. Không chỉ trả về 1 từ vô nghĩa.\n");
        builder.append("13. confidence là số 0-100.\n");
        builder.append("14. Nếu thiếu dữ liệu, dùng chuỗi rỗng hoặc mảng rỗng; không bịa thêm.\n");
        builder.append("15. date phải là ngày giao dịch in trên hóa đơn, ưu tiên định dạng dd/MM/yyyy. Nếu ảnh có chữ 'Ngày', 'Date', 'Ngày in', hãy lấy đúng ngày ở gần nhãn đó.\n");
        builder.append("16. Không được tự suy đoán ngày hiện tại. Nếu không đọc chắc được ngày từ ảnh thì trả về chuỗi rỗng.\n");
        builder.append("17. Nếu có cả giờ và ngày trong cùng khu vực, chỉ lấy phần ngày giao dịch; bỏ qua giờ, số bàn, số hóa đơn và số điện thoại.\n");
        builder.append("18. Nếu ảnh dùng tháng chữ tiếng Anh như '01/MAR/2023', hãy chuẩn hóa về '01/03/2023'.\n");
        builder.append("Ví dụ JSON hợp lệ:\n");
        builder.append("{\"merchant\":\"CÀ PHÊ HOÀNG PHÚC\",\"date\":\"18/02/2019\",\"total\":54000,\"category_hint\":\"Ăn uống\",\"note_hint\":\"Chi tiêu tại CÀ PHÊ HOÀNG PHÚC - Cà phê đá, bún thịt xào\",\"items\":[{\"name\":\"Cà phê đá\",\"price\":10000,\"quantity\":1,\"category\":\"Ăn uống\"},{\"name\":\"Bún thịt xào\",\"price\":15000,\"quantity\":1,\"category\":\"Ăn uống\"}],\"total_candidates\":[{\"label\":\"Thành tiền\",\"amount\":54000,\"line_order\":8},{\"label\":\"Tổng\",\"amount\":54000,\"line_order\":9}],\"confidence\":93}\n");
        builder.append("{\"merchant\":\"VINH NGUYEN RES\",\"date\":\"29/03/2019\",\"total\":225000,\"category_hint\":\"Ăn uống\",\"note_hint\":\"Chi tiêu tại VINH NGUYEN RES - Coca, Sprite, Soda\",\"items\":[{\"name\":\"Coca\",\"price\":50000,\"quantity\":2,\"category\":\"Ăn uống\"},{\"name\":\"Sprite\",\"price\":50000,\"quantity\":2,\"category\":\"Ăn uống\"},{\"name\":\"Soda\",\"price\":25000,\"quantity\":1,\"category\":\"Ăn uống\"}],\"total_candidates\":[{\"label\":\"T.Cộng\",\"amount\":225000,\"line_order\":12},{\"label\":\"TIỀN MẶT\",\"amount\":225000,\"line_order\":13}],\"confidence\":95}\n");
        builder.append("{\"merchant\":\"CGV VINCOM ĐỒNG KHỞI\",\"date\":\"01/03/2023\",\"total\":300000,\"category_hint\":\"Giải trí\",\"note_hint\":\"Chi tiêu giải trí tại CGV VINCOM ĐỒNG KHỞI - vé xem phim Gold Class\",\"items\":[{\"name\":\"Ticket Price\",\"price\":195000,\"quantity\":1,\"category\":\"Giải trí\"},{\"name\":\"Service Charge\",\"price\":105000,\"quantity\":1,\"category\":\"Giải trí\"}],\"total_candidates\":[{\"label\":\"VND\",\"amount\":300000,\"line_order\":14}],\"confidence\":94}\n");
        builder.append("{\"merchant\":\"NHÀ HÀNG ABC\",\"date\":\"02/04/2026\",\"total\":550000,\"category_hint\":\"Ăn uống\",\"note_hint\":\"Chi tiêu tại NHÀ HÀNG ABC - bữa ăn có VAT và phí dịch vụ\",\"items\":[{\"name\":\"Món ăn\",\"price\":500000,\"quantity\":1,\"category\":\"Ăn uống\"}],\"total_candidates\":[{\"label\":\"Tổng cộng\",\"amount\":500000,\"line_order\":18},{\"label\":\"VAT\",\"amount\":50000,\"line_order\":19},{\"label\":\"Service Charge\",\"amount\":50000,\"line_order\":20},{\"label\":\"Thanh toán\",\"amount\":550000,\"line_order\":21}],\"confidence\":96}");
        return builder.toString();
    }

    @NonNull
    static JSONObject buildResponseSchema() throws JSONException {
        JSONObject itemSchema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "STRING"))
                        .put("price", new JSONObject().put("type", "NUMBER"))
                        .put("quantity", new JSONObject().put("type", "NUMBER"))
                        .put("category", new JSONObject().put("type", "STRING"))
                );

        JSONObject totalCandidateSchema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("label", new JSONObject().put("type", "STRING"))
                        .put("amount", new JSONObject().put("type", "NUMBER"))
                        .put("line_order", new JSONObject().put("type", "NUMBER"))
                );

        return new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("merchant", new JSONObject().put("type", "STRING"))
                        .put("date", new JSONObject().put("type", "STRING"))
                        .put("total", new JSONObject().put("type", "NUMBER"))
                        .put("category_hint", new JSONObject().put("type", "STRING"))
                        .put("note_hint", new JSONObject().put("type", "STRING"))
                        .put("items", new JSONObject()
                                .put("type", "ARRAY")
                                .put("items", itemSchema))
                        .put("total_candidates", new JSONObject()
                                .put("type", "ARRAY")
                                .put("items", totalCandidateSchema))
                        .put("confidence", new JSONObject().put("type", "NUMBER"))
                );
    }

    @NonNull
    static String buildRequestBody(@NonNull List<InlineImagePayload> images,
                                   @NonNull List<String> allowedCategories) throws JSONException {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", buildPrompt(allowedCategories)));
        for (InlineImagePayload image : images) {
            parts.put(new JSONObject().put("inline_data", new JSONObject()
                    .put("mime_type", image.mimeType)
                    .put("data", image.base64ImageData)));
        }
        JSONArray contents = new JSONArray()
                .put(new JSONObject().put("parts", parts));

        JSONObject generationConfig = new JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
                .put("responseSchema", buildResponseSchema());

        return new JSONObject()
                .put("contents", contents)
                .put("generationConfig", generationConfig)
                .toString();
    }

    static final class InlineImagePayload {
        @NonNull
        private final String mimeType;
        @NonNull
        private final String base64ImageData;

        InlineImagePayload(@NonNull String mimeType, @NonNull String base64ImageData) {
            this.mimeType = mimeType;
            this.base64ImageData = base64ImageData;
        }
    }

    private static final class TextListFormatter {
        @NonNull
        private static String joinQuoted(@NonNull List<String> values) {
            if (values.isEmpty()) {
                return "\"Khác (Chi)\"";
            }
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append('"').append(values.get(index)).append('"');
            }
            return builder.toString();
        }
    }
}
