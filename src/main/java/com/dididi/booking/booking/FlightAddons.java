package com.dididi.booking.booking;

import java.util.List;

/**
 * Danh mục dịch vụ thêm cho vé máy bay: suất ăn & hành lý (giống Vietnam Airlines).
 * Mỗi lựa chọn có phụ phí cộng vào tổng đơn. Giá ở đây là NGUỒN CHÂN LÝ phía server
 * (controller tính lại phụ phí từ mã khách gửi lên, không tin client).
 */
public final class FlightAddons {

    public record Option(String code, String label, long price) {}

    public static final List<Option> MEALS = List.of(
            new Option("STD",     "Suất ăn tiêu chuẩn",       0),
            new Option("CHICKEN", "Cơm gà",               90000),
            new Option("BEEF",    "Cơm bò",               90000),
            new Option("VEGGIE",  "Mì chay",              70000),
            new Option("SEAFOOD", "Hải sản (suất đặc biệt)", 120000)
    );

    public static final List<Option> BAGS = List.of(
            new Option("BAG0",  "Xách tay 7kg (kèm sẵn)",      0),
            new Option("BAG20", "Ký gửi 20kg",            200000),
            new Option("BAG30", "Ký gửi 30kg",            350000),
            new Option("BAG40", "Ký gửi 40kg",            500000)
    );

    public static long mealPrice(String code) { return priceOf(MEALS, code); }
    public static long bagPrice(String code)  { return priceOf(BAGS, code); }
    public static String mealLabel(String code) { return labelOf(MEALS, code, "Suất ăn tiêu chuẩn"); }
    public static String bagLabel(String code)  { return labelOf(BAGS, code, "Xách tay 7kg"); }

    private static long priceOf(List<Option> list, String code) {
        if (code == null) return 0;
        for (Option o : list) if (o.code().equals(code)) return o.price();
        return 0;
    }

    private static String labelOf(List<Option> list, String code, String dflt) {
        if (code == null) return dflt;
        for (Option o : list) if (o.code().equals(code)) return o.label();
        return dflt;
    }

    private FlightAddons() {}
}
