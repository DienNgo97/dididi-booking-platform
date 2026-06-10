package com.dididi.booking.invoice.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.repository.CompanyRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Sinh hoa don VAT (PDF) cho don dat tra bang ngan sach cong ty (B2B). Font Unicode nhung de in tieng Viet. */
@Service
public class InvoiceService {

    private final BookingRepository bookingRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Value("${app.invoice.vat-rate:0.10}")
    private double vatRate;
    @Value("${app.invoice.seller.name:Công ty TNHH Dididi}")
    private String sellerName;
    @Value("${app.invoice.seller.tax-code:0312345678}")
    private String sellerTaxCode;
    @Value("${app.invoice.seller.address:Tầng 10, Tòa nhà Dididi, 1 Nguyễn Huệ, Quận 1, TP.HCM}")
    private String sellerAddress;

    private final NumberFormat vnd = NumberFormat.getInstance(new Locale("vi", "VN"));

    public InvoiceService(BookingRepository bookingRepository, CompanyRepository companyRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    /** Sinh PDF hoa don VAT theo ma don. Don phai la don cong ty (companyId != null) va da CONFIRMED. */
    public byte[] generateForBooking(String publicCode) {
        Booking b = bookingRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (b.getCompanyId() == null) {
            throw new BusinessException("NOT_CORPORATE", "Đơn này không phải đặt theo công ty", HttpStatus.BAD_REQUEST);
        }
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("NOT_CONFIRMED", "Chỉ xuất hóa đơn cho đơn đã xác nhận", HttpStatus.CONFLICT);
        }
        Company co = companyRepository.findById(b.getCompanyId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy công ty", HttpStatus.NOT_FOUND));
        User booker = userRepository.findById(b.getUserId()).orElse(null);
        return build(b, co, booker);
    }

    private byte[] build(Booking b, Company co, User booker) {
        try {
            BaseFont bf = baseFont("fonts/DejaVuSans.ttf");
            BaseFont bfBold = baseFont("fonts/DejaVuSans-Bold.ttf");
            Font fTitle = new Font(bfBold, 16, Font.NORMAL, Color.BLACK);
            Font fSub = new Font(bf, 10, Font.ITALIC, new Color(80, 80, 80));
            Font fH = new Font(bfBold, 11, Font.NORMAL, Color.BLACK);
            Font f = new Font(bf, 10.5f, Font.NORMAL, Color.BLACK);
            Font fBold = new Font(bfBold, 10.5f, Font.NORMAL, Color.BLACK);
            Font fSmall = new Font(bf, 9, Font.ITALIC, new Color(90, 90, 90));

            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Paragraph title = new Paragraph("HÓA ĐƠN GIÁ TRỊ GIA TĂNG", fTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph sub = new Paragraph("(Bản thể hiện của hóa đơn điện tử)", fSub);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);

            LocalDate date = b.getCreatedAt() != null
                    ? b.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
            Paragraph meta = new Paragraph("Số hóa đơn: INV-" + b.getPublicCode()
                    + "    Ngày: " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fSmall);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(10);
            doc.add(meta);

            doc.add(line("Đơn vị bán hàng: ", sellerName, fBold, f));
            doc.add(line("Mã số thuế: ", sellerTaxCode, fBold, f));
            doc.add(line("Địa chỉ: ", sellerAddress, fBold, f));
            doc.add(spacer(6));
            doc.add(line("Đơn vị mua hàng: ", nz(co.getName()), fBold, f));
            doc.add(line("Mã số thuế: ", nz(co.getTaxCode()), fBold, f));
            doc.add(line("Địa chỉ: ", nz(co.getAddress()), fBold, f));
            if (booker != null) {
                String who = nz(booker.getFullName())
                        + (booker.getEmail() != null ? " (" + booker.getEmail() + ")" : "");
                doc.add(line("Người đặt: ", who, fBold, f));
            }
            doc.add(spacer(10));

            PdfPTable table = new PdfPTable(new float[]{1.2f, 6f, 1.3f, 3f, 3.2f});
            table.setWidthPercentage(100);
            header(table, "STT", fH);
            header(table, "Nội dung", fH);
            header(table, "SL", fH);
            header(table, "Đơn giá", fH);
            header(table, "Thành tiền", fH);

            BigDecimal total = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
            int qty = b.getQuantity() <= 0 ? 1 : b.getQuantity();
            BigDecimal unit = total.divide(BigDecimal.valueOf(qty), 0, RoundingMode.HALF_UP);
            cell(table, "1", f, Element.ALIGN_CENTER);
            cell(table, nz(b.getTitle()), f, Element.ALIGN_LEFT);
            cell(table, String.valueOf(qty), f, Element.ALIGN_CENTER);
            cell(table, vnd.format(unit), f, Element.ALIGN_RIGHT);
            cell(table, vnd.format(total), f, Element.ALIGN_RIGHT);
            doc.add(table);
            doc.add(spacer(8));

            BigDecimal preTax = total.divide(BigDecimal.valueOf(1 + vatRate), 0, RoundingMode.HALF_UP);
            BigDecimal vat = total.subtract(preTax);
            int pct = (int) Math.round(vatRate * 100);
            doc.add(totalLine("Cộng tiền hàng (chưa VAT): ", vnd.format(preTax) + " đ", f, fBold));
            doc.add(totalLine("Thuế GTGT (" + pct + "%): ", vnd.format(vat) + " đ", f, fBold));
            doc.add(totalLine("Tổng tiền thanh toán: ", vnd.format(total) + " đ", fBold, fBold));

            Paragraph words = new Paragraph(
                    "Số tiền viết bằng chữ: " + VietnameseMoney.toWords(total.longValue()) + ".",
                    new Font(bf, 10, Font.ITALIC, Color.BLACK));
            words.setSpacingBefore(6);
            doc.add(words);

            Paragraph note = new Paragraph(
                    "Hình thức thanh toán: Ngân sách công ty (B2B). Chứng từ phục vụ mục đích minh họa đồ án.", fSmall);
            note.setSpacingBefore(16);
            doc.add(note);

            doc.close();
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PDF_ERROR", "Lỗi tạo PDF hóa đơn: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BaseFont baseFont(String classpath) throws Exception {
        byte[] bytes = new ClassPathResource(classpath).getInputStream().readAllBytes();
        return BaseFont.createFont(classpath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private Paragraph spacer(float h) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(h);
        return p;
    }

    private Paragraph line(String label, String value, Font fl, Font fv) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label, fl));
        p.add(new Chunk(value, fv));
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph totalLine(String label, String value, Font fl, Font fv) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Chunk(label, fl));
        p.add(new Chunk(value, fv));
        p.setSpacingAfter(2);
        return p;
    }

    private void header(PdfPTable t, String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBackgroundColor(new Color(235, 238, 242));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(5);
        t.addCell(c);
    }

    private void cell(PdfPTable t, String s, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        t.addCell(c);
    }
}
