package com.dididi.booking.invoice.service;

import java.util.ArrayList;
import java.util.List;

/** Doc so tien (VND) sang chu tieng Viet. VD: 20000000 -> "Hai muoi trieu dong". */
public final class VietnameseMoney {

    private static final String[] DIGITS = {"không","một","hai","ba","bốn","năm","sáu","bảy","tám","chín"};

    private VietnameseMoney() {}

    public static String toWords(long number) {
        if (number == 0) return "Không đồng";
        boolean neg = number < 0;
        long n = Math.abs(number);
        List<Integer> groups = new ArrayList<>();
        while (n > 0) { groups.add((int) (n % 1000)); n /= 1000; }
        List<String> parts = new ArrayList<>();
        boolean started = false;
        for (int i = groups.size() - 1; i >= 0; i--) {
            int g = groups.get(i);
            if (g == 0) continue;
            String gw = readThree(g, started);
            String sc = scaleWord(i);
            parts.add((gw + (sc.isEmpty() ? "" : " " + sc)).trim());
            started = true;
        }
        String s = String.join(" ", parts).trim();
        s = s.substring(0, 1).toUpperCase() + s.substring(1);
        return (neg ? "Âm " : "") + s + " đồng";
    }

    private static String readThree(int g, boolean started) {
        int h = g / 100, t = (g % 100) / 10, u = g % 10;
        List<String> out = new ArrayList<>();
        if (h > 0) { out.add(DIGITS[h]); out.add("trăm"); }
        else if (started && (t > 0 || u > 0)) { out.add("không"); out.add("trăm"); }
        if (t > 1) {
            out.add(DIGITS[t]); out.add("mươi");
            if (u == 1) out.add("mốt");
            else if (u == 5) out.add("lăm");
            else if (u > 0) out.add(DIGITS[u]);
        } else if (t == 1) {
            out.add("mười");
            if (u == 1) out.add("một");
            else if (u == 5) out.add("lăm");
            else if (u > 0) out.add(DIGITS[u]);
        } else if (u > 0) {
            if (h > 0 || started) out.add("lẻ");
            out.add(DIGITS[u]);
        }
        return String.join(" ", out);
    }

    private static String scaleWord(int i) {
        String base = (i % 3 == 1) ? "nghìn" : (i % 3 == 2) ? "triệu" : "";
        StringBuilder tys = new StringBuilder();
        for (int k = 0; k < i / 3; k++) { if (tys.length() > 0) tys.append(" "); tys.append("tỷ"); }
        String res = base;
        if (!base.isEmpty() && tys.length() > 0) res += " ";
        res += tys.toString();
        return res.trim();
    }
}
