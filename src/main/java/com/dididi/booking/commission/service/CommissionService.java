package com.dididi.booking.commission.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.commission.api.dto.CommissionReportDto;
import com.dididi.booking.commission.api.dto.CommissionReportRow;
import com.dididi.booking.commission.api.dto.VendorCommissionDto;
import com.dididi.booking.commission.domain.CommissionConfig;
import com.dididi.booking.commission.domain.VendorCommissionRate;
import com.dididi.booking.commission.repository.CommissionConfigRepository;
import com.dididi.booking.commission.repository.VendorCommissionRateRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hoa hong san: ty le mac dinh + ghi de theo vendor + bao cao hoa hong. */
@Service
public class CommissionService {

    private final CommissionConfigRepository configRepository;
    private final VendorCommissionRateRepository vendorRateRepository;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;

    @Value("${app.commission.default-rate:0.15}")
    private BigDecimal fallbackRate;

    public CommissionService(CommissionConfigRepository configRepository,
                             VendorCommissionRateRepository vendorRateRepository,
                             BookingRepository bookingRepository, HotelRepository hotelRepository,
                             UserRepository userRepository) {
        this.configRepository = configRepository;
        this.vendorRateRepository = vendorRateRepository;
        this.bookingRepository = bookingRepository;
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
    }

    public BigDecimal getDefaultRate() {
        return configRepository.findTopByOrderByIdAsc().map(CommissionConfig::getDefaultRate).orElse(fallbackRate);
    }

    @Transactional
    public BigDecimal setDefaultRate(BigDecimal rate) {
        validate(rate);
        CommissionConfig c = configRepository.findTopByOrderByIdAsc().orElseGet(CommissionConfig::new);
        c.setDefaultRate(rate);
        configRepository.save(c);
        return rate;
    }

    public List<VendorCommissionDto> listVendorRates() {
        List<VendorCommissionDto> out = new ArrayList<>();
        for (VendorCommissionRate r : vendorRateRepository.findAll()) {
            User u = userRepository.findById(r.getVendorId()).orElse(null);
            out.add(new VendorCommissionDto(r.getVendorId(),
                    u != null ? u.getFullName() : null, u != null ? u.getEmail() : null, r.getRate()));
        }
        return out;
    }

    @Transactional
    public void setVendorRate(Long vendorId, BigDecimal rate) {
        validate(rate);
        VendorCommissionRate r = vendorRateRepository.findByVendorId(vendorId).orElseGet(VendorCommissionRate::new);
        r.setVendorId(vendorId);
        r.setRate(rate);
        vendorRateRepository.save(r);
    }

    @Transactional
    public void removeVendorRate(Long vendorId) {
        vendorRateRepository.findByVendorId(vendorId).ifPresent(vendorRateRepository::delete);
    }

    public BigDecimal effectiveRate(Long vendorId) {
        if (vendorId != null) {
            VendorCommissionRate r = vendorRateRepository.findByVendorId(vendorId).orElse(null);
            if (r != null) return r.getRate();
        }
        return getDefaultRate();
    }

    /**
     * Hoa hong cua 1 don (lam tron) cho bao cao theo ky; tra ve null neu don khong thuoc vendor nao
     * (don CHANNEL/PMS hoac ve may bay). Dung ty le hieu luc cua vendor (rieng hoac mac dinh).
     */
    public BigDecimal commissionForReport(Booking b) {
        Long vendorId = resolveVendor(b);
        if (vendorId == null) return null;
        BigDecimal amt = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
        return amt.multiply(effectiveRate(vendorId)).setScale(0, RoundingMode.HALF_UP);
    }

    /** Bao cao hoa hong: tinh tren cac don HOTEL da CONFIRMED co vendor (DIRECT), gom theo vendor. */
    public CommissionReportDto report() {
        Map<Long, long[]> count = new LinkedHashMap<>();
        Map<Long, BigDecimal> gross = new LinkedHashMap<>();
        for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
            Long vendorId = resolveVendor(b);
            if (vendorId == null) continue;
            BigDecimal amt = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
            gross.merge(vendorId, amt, BigDecimal::add);
            count.computeIfAbsent(vendorId, k -> new long[1])[0]++;
        }
        List<CommissionReportRow> rows = new ArrayList<>();
        BigDecimal tGross = BigDecimal.ZERO, tComm = BigDecimal.ZERO, tNet = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : gross.entrySet()) {
            Long vendorId = e.getKey();
            BigDecimal g = e.getValue();
            BigDecimal rate = effectiveRate(vendorId);
            BigDecimal comm = g.multiply(rate).setScale(0, RoundingMode.HALF_UP);
            BigDecimal net = g.subtract(comm);
            User u = userRepository.findById(vendorId).orElse(null);
            rows.add(new CommissionReportRow(vendorId, u != null ? u.getFullName() : ("Vendor #" + vendorId),
                    count.get(vendorId)[0], g, rate, comm, net));
            tGross = tGross.add(g);
            tComm = tComm.add(comm);
            tNet = tNet.add(net);
        }
        rows.sort((a, b) -> b.gross().compareTo(a.gross()));
        return new CommissionReportDto(rows, tGross, tComm, tNet);
    }

    private Long resolveVendor(Booking b) {
        if (b.getType() == BookingType.HOTEL && b.getTargetId() != null) {
            Hotel h = hotelRepository.findById(b.getTargetId()).orElse(null);
            return h != null ? h.getVendorId() : null;
        }
        return null;
    }

    private void validate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("INVALID_RATE", "Tỷ lệ hoa hồng phải trong khoảng 0 đến 1 (vd 0.15 = 15%)",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
