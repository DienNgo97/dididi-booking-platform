package com.dididi.booking.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * NÉN + THU NHỎ ẢNH TRƯỚC KHI LƯU (tồn đọng từ đợt QA-A).
 *
 * <p>Trước đây ảnh được lưu nguyên bản: một tấm chụp từ điện thoại 4-6 MB, 4000px, trong khi chỗ
 * hiển thị rộng nhất chưa tới 1200px. Hệ quả: MinIO phình nhanh, và trang khách phải tải vài MB
 * chỉ để xem một thumbnail.</p>
 *
 * <p>Nguyên tắc: chỉ đụng vào JPEG/PNG tĩnh. GIF (có thể là ảnh động), WebP/AVIF (JDK không ghi
 * lại được) và video giữ nguyên. Xử lý lỗi kiểu "thà lưu bản gốc còn hơn làm hỏng ảnh của khách":
 * mọi trục trặc đều trả về byte gốc.</p>
 */
public final class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /** Cạnh dài tối đa sau khi thu nhỏ — đủ nét cho ảnh bìa full-width trên màn Retina. */
    private static final int MAX_CANH = 1600;
    /** Dưới ngưỡng này thì không đụng vào: nén lại chỉ tốn CPU mà chẳng lợi bao nhiêu. */
    private static final long BO_QUA_DUOI_BYTE = 300 * 1024L;
    private static final float CHAT_LUONG_JPEG = 0.85f;

    private ImageOptimizer() { }

    public record Ket_qua(byte[] bytes, String contentType) { }

    /** Trả về ảnh đã tối ưu, hoặc chính dữ liệu gốc nếu không nên/không thể xử lý. */
    public static Ket_qua toiUu(byte[] goc, String contentType) {
        if (goc == null || contentType == null) {
            return new Ket_qua(goc, contentType);
        }
        String ct = contentType.toLowerCase();
        boolean jpeg = ct.contains("jpeg") || ct.contains("jpg");
        boolean png = ct.contains("png");
        if (!jpeg && !png) {
            return new Ket_qua(goc, contentType);          // GIF động, WebP, AVIF, video -> để yên
        }
        try {
            BufferedImage anh = ImageIO.read(new ByteArrayInputStream(goc));
            if (anh == null) {
                return new Ket_qua(goc, contentType);      // không đọc được -> giữ nguyên
            }
            int w = anh.getWidth(), h = anh.getHeight();
            boolean canThuNho = Math.max(w, h) > MAX_CANH;
            if (!canThuNho && goc.length < BO_QUA_DUOI_BYTE) {
                return new Ket_qua(goc, contentType);
            }
            BufferedImage ra = canThuNho ? thuNho(anh, w, h) : anh;

            // PNG có nền trong thì phải giữ PNG, đổi sang JPEG là nền hoá đen.
            boolean coAlpha = png && anh.getColorModel().hasAlpha();
            byte[] moi = coAlpha ? ghiPng(ra) : ghiJpeg(ra);
            if (moi == null || moi.length >= goc.length) {
                return new Ket_qua(goc, contentType);      // nén xong lại to hơn -> giữ bản gốc
            }
            return new Ket_qua(moi, coAlpha ? "image/png" : "image/jpeg");
        } catch (Exception | OutOfMemoryError e) {
            log.warn("[storage] Không tối ưu được ảnh ({} bytes, {}): {} — lưu bản gốc",
                    goc.length, contentType, e.toString());
            return new Ket_qua(goc, contentType);
        }
    }

    private static BufferedImage thuNho(BufferedImage src, int w, int h) {
        double tiLe = (double) MAX_CANH / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * tiLe));
        int nh = Math.max(1, (int) Math.round(h * tiLe));
        BufferedImage ra = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ra.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return ra;
    }

    private static byte[] ghiJpeg(BufferedImage anh) throws Exception {
        BufferedImage rgb = anh.getType() == BufferedImage.TYPE_INT_RGB ? anh : sangRgb(anh);
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) return null;
        ImageWriter writer = it.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            if (p.canWriteCompressed()) {
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(CHAT_LUONG_JPEG);
            }
            writer.write(null, new IIOImage(rgb, null, null), p);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] ghiPng(BufferedImage anh) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return ImageIO.write(anh, "png", out) ? out.toByteArray() : null;
    }

    private static BufferedImage sangRgb(BufferedImage src) {
        BufferedImage ra = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ra.createGraphics();
        try {
            g.drawImage(src, 0, 0, java.awt.Color.WHITE, null);   // nền trắng thay cho vùng trong suốt
        } finally {
            g.dispose();
        }
        return ra;
    }
}
