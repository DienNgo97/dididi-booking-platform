package com.dididi.booking.group.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.group.domain.entity.GroupBooking;
import com.dididi.booking.group.repository.GroupBookingRepository;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GroupBookingService {

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"; // bo ky tu de nham (l,o,0,1)

    private final GroupBookingRepository groupRepo;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;

    public GroupBookingService(GroupBookingRepository groupRepo, BookingService bookingService,
                               BookingRepository bookingRepository, RoomTypeRepository roomTypeRepository) {
        this.groupRepo = groupRepo;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    @Transactional
    public GroupBooking createGroup(Long organizerUserId, Long hotelId, Long roomTypeId, String roomName,
                                    LocalDate checkIn, LocalDate checkOut, String title) {
        GroupBooking g = new GroupBooking();
        g.setToken(newToken());
        g.setOrganizerUserId(organizerUserId);
        g.setHotelId(hotelId);
        g.setRoomTypeId(roomTypeId);
        g.setRoomName(roomName);
        g.setCheckIn(checkIn);
        g.setCheckOut(checkOut);
        g.setTitle(title != null && !title.isBlank() ? title.trim() : "Nhóm đặt phòng");
        g.setStatus("OPEN");
        return groupRepo.save(g);
    }

    public GroupBooking getByToken(String token) {
        return groupRepo.findByToken(token)
                .orElseThrow(() -> new BusinessException("GROUP_NOT_FOUND", "Không tìm thấy nhóm đặt phòng", HttpStatus.NOT_FOUND));
    }

    public List<Booking> members(Long groupId) {
        return bookingRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
    }

    /**
     * Cac nhom cua user: nhom minh TO CHUC + nhom minh DA THAM GIA (suy tu cac don co group_id).
     * Tra ve nhom to chuc truoc (moi nhat truoc), roi den nhom tham gia. Khong trung lap.
     */
    public List<GroupBooking> myGroups(Long userId) {
        Map<Long, GroupBooking> map = new LinkedHashMap<>();
        for (GroupBooking g : groupRepo.findByOrganizerUserIdOrderByCreatedAtDesc(userId)) {
            map.put(g.getId(), g);
        }
        Set<Long> joinedIds = new LinkedHashSet<>();
        for (Booking b : bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (b.getGroupId() != null) joinedIds.add(b.getGroupId());
        }
        joinedIds.removeAll(map.keySet());
        if (!joinedIds.isEmpty()) {
            for (GroupBooking g : groupRepo.findAllById(joinedIds)) map.putIfAbsent(g.getId(), g);
        }
        return new ArrayList<>(map.values());
    }

    /** Thanh vien them phong cua minh -> tao Booking (gan group_id). Controller se chuyen sang /payment.
     *  Neu roomTypeId duoc chon (va thuoc dung khach san) thi dung hang phong do; nguoc lai dung hang phong cua nhom. */
    @Transactional
    public Booking addMyRoom(GroupBooking g, Long userId, String guestName, Long roomTypeId,
                             LocalDate checkIn, LocalDate checkOut, int rooms) {
        if ("CLOSED".equals(g.getStatus())) {
            throw new BusinessException("GROUP_CLOSED", "Nhóm đã đóng, không thể thêm phòng", HttpStatus.BAD_REQUEST);
        }
        if (g.isEnded()) {
            throw new BusinessException("GROUP_ENDED", "Chuyến đi đã kết thúc, không thể thêm phòng", HttpStatus.BAD_REQUEST);
        }
        if (g.getDeadline() != null && LocalDateTime.now().isAfter(g.getDeadline())) {
            throw new BusinessException("GROUP_DEADLINE", "Đã quá hạn chót của nhóm, không thể thêm phòng", HttpStatus.BAD_REQUEST);
        }
        Long rtId = g.getRoomTypeId();
        String rtName = g.getRoomName();
        if (roomTypeId != null) {
            RoomType rt = roomTypeRepository.findById(roomTypeId).orElse(null);
            if (rt != null && rt.getHotelId() != null && rt.getHotelId().equals(g.getHotelId())) {
                rtId = rt.getId();
                rtName = rt.getName();
            }
        }
        Booking b = bookingService.createHotelBooking(userId, g.getHotelId(), rtId, rtName,
                guestName, checkIn, checkOut, rooms);
        b.setGroupId(g.getId());
        return bookingRepository.save(b);
    }

    /**
     * Xoa 1 phong khoi nhom NEU CHUA THANH TOAN. Cho phep chu so huu phong HOAC chu nhom.
     * Dat trang thai CANCELLED (tha giu cho 20'); phong da CONFIRMED khong xoa duoc.
     */
    @Transactional
    public void removeRoom(String token, String bookingCode, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        Booking b = bookingRepository.findByPublicCode(bookingCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy phòng", HttpStatus.NOT_FOUND));
        if (b.getGroupId() == null || !b.getGroupId().equals(g.getId())) {
            throw new BusinessException("NOT_IN_GROUP", "Phòng không thuộc nhóm này", HttpStatus.BAD_REQUEST);
        }
        boolean owner = requesterUserId != null && requesterUserId.equals(b.getUserId());
        boolean organizer = requesterUserId != null && requesterUserId.equals(g.getOrganizerUserId());
        if (!owner && !organizer) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền xoá phòng này", HttpStatus.FORBIDDEN);
        }
        if (b.getStatus() == BookingStatus.CONFIRMED) {
            throw new BusinessException("ALREADY_PAID", "Phòng đã thanh toán, không thể xoá", HttpStatus.BAD_REQUEST);
        }
        b.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(b);
    }

    /**
     * Nguoi to chuc xoa han 1 thanh vien khoi nhom: huy TAT CA phong chua thanh toan cua nguoi do.
     * Chan neu thanh vien con phong DA thanh toan (can huy/hoan tien rieng truoc); khong cho xoa chinh chu nhom.
     */
    @Transactional
    public void removeMember(String token, Long memberUserId, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được xoá thành viên", HttpStatus.FORBIDDEN);
        }
        if (memberUserId == null || memberUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("BAD_MEMBER", "Không thể xoá người tạo nhóm khỏi nhóm", HttpStatus.BAD_REQUEST);
        }
        List<Booking> rooms = bookingRepository.findByGroupIdOrderByCreatedAtAsc(g.getId());
        boolean hasPaid = rooms.stream().anyMatch(b ->
                memberUserId.equals(b.getUserId()) && b.getStatus() == BookingStatus.CONFIRMED);
        if (hasPaid) {
            throw new BusinessException("MEMBER_HAS_PAID",
                    "Thành viên này đã có phòng đã thanh toán — hãy huỷ/hoàn tiền phòng đó trước khi xoá", HttpStatus.CONFLICT);
        }
        int removed = 0;
        for (Booking b : rooms) {
            if (memberUserId.equals(b.getUserId())
                    && b.getStatus() != BookingStatus.CANCELLED
                    && b.getStatus() != BookingStatus.CONFIRMED) {
                b.setStatus(BookingStatus.CANCELLED);   // giai phong cho dang giu
                bookingRepository.save(b);
                removed++;
            }
        }
        if (removed == 0) {
            throw new BusinessException("NO_ROOMS", "Thành viên này không có phòng nào đang chờ để xoá", HttpStatus.BAD_REQUEST);
        }
    }

    /** Danh sach hang phong cua khach san (cho dropdown sua hang phong). */
    public List<RoomType> roomTypesFor(Long hotelId) {
        return roomTypeRepository.findByHotelIdOrderByBasePrice(hotelId);
    }

    /**
     * Cap nhat nhom (CHI nguoi to chuc). Doi ten nhom va/hoac hang phong.
     * Doi hang phong chi anh huong cac phong THEM MOI sau do; phong da dat giu nguyen.
     */
    @Transactional
    public GroupBooking updateGroup(String token, Long requesterUserId, String title, Long roomTypeId,
                                    LocalDateTime deadline, boolean splitEven) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được chỉnh sửa", HttpStatus.FORBIDDEN);
        }
        if (title != null && !title.isBlank()) {
            g.setTitle(title.trim());
        }
        if (roomTypeId != null) {
            RoomType rt = roomTypeRepository.findById(roomTypeId).orElse(null);
            // chi chap nhan hang phong thuoc dung khach san cua nhom
            if (rt == null || rt.getHotelId() == null || !rt.getHotelId().equals(g.getHotelId())) {
                throw new BusinessException("BAD_ROOM_TYPE", "Hạng phòng không hợp lệ cho khách sạn này", HttpStatus.BAD_REQUEST);
            }
            g.setRoomTypeId(rt.getId());
            g.setRoomName(rt.getName());
        }
        g.setDeadline(deadline);     // null = bo han chot
        g.setSplitEven(splitEven);
        return groupRepo.save(g);
    }

    /** Dong nhom (chu nhom): khong cho them phong moi. */
    @Transactional
    public void closeGroup(String token, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được đóng nhóm", HttpStatus.FORBIDDEN);
        }
        g.setStatus("CLOSED");
        groupRepo.save(g);
    }

    /** Mo lai nhom (chu nhom). */
    @Transactional
    public void reopenGroup(String token, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được mở lại nhóm", HttpStatus.FORBIDDEN);
        }
        g.setStatus("OPEN");
        groupRepo.save(g);
    }

    /**
     * BP-GRP-01: chot tap booking se thanh toan khi mo giao dich "tra ca nhom".
     * Tra ve danh sach booking PENDING_PAYMENT hien tai (de tinh tong) va luu ID set len group,
     * de luc return chi confirm dung cac booking nay.
     */
    @Transactional
    public List<Booking> beginGroupPayment(Long groupId) {
        List<Booking> pending = new ArrayList<>();
        StringBuilder ids = new StringBuilder();
        for (Booking b : bookingRepository.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                pending.add(b);
                if (ids.length() > 0) ids.append(',');
                ids.append(b.getId());
            }
        }
        groupRepo.findById(groupId).ifPresent(g -> {
            g.setPayGroupBookingIds(ids.length() > 0 ? ids.toString() : null);
            groupRepo.save(g);
        });
        return pending;
    }

    /**
     * Xac nhan CHI cac phong da chot luc bam "tra ca nhom" (BP-GRP-01) — khong phai moi don con PENDING.
     * Goi sau khi VNPay return thanh cong cho giao dich gop. Tra ve token de chuyen huong.
     */
    @Transactional
    public String confirmGroupBookings(Long groupId) {
        GroupBooking g = groupRepo.findById(groupId).orElse(null);
        Long organizerId = (g != null) ? g.getOrganizerUserId() : null;
        Set<Long> chosen = parseIds(g != null ? g.getPayGroupBookingIds() : null);
        for (Booking b : bookingRepository.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            // Chi confirm don NAM TRONG tap da chot (neu co tap chot). Phong them sau khong duoc confirm.
            if (!chosen.isEmpty() && !chosen.contains(b.getId())) continue;
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                if (organizerId != null) b.setPaidByUserId(organizerId);  // tra gop -> chu nhom chi tien
                bookingService.markConfirmed(b);
            }
        }
        if (g != null) {
            g.setPayGroupBookingIds(null);   // dung 1 lan: tra xong thi xoa tap da chot
            groupRepo.save(g);
        }
        return g != null ? g.getToken() : "";
    }

    /**
     * P1-6: đơn có nằm trong tập ĐANG trả gộp không?
     *
     * <p>Khi chủ nhóm bấm "trả cả nhóm", các phòng của thành viên vẫn ở PENDING_PAYMENT và KHÔNG có
     * Payment riêng — tiền thu một lần cho cả tập. Job hết hạn giữ chỗ nhìn từng đơn thấy "chưa
     * thanh toán" nên huỷ, trong khi khách đang nhập OTP: khách trả đủ tiền mà mất phòng.</p>
     *
     * <p>Có TRẦN thời gian ({@code window}) tính từ lúc chốt tập: khách bỏ ngang giữa chừng thì đơn
     * phải được nhả lại cho người khác, không giam phòng vĩnh viễn.</p>
     */
    @Transactional(readOnly = true)
    public boolean dangTraGop(Booking b, java.time.Duration window) {
        if (b == null || b.getGroupId() == null) return false;
        GroupBooking g = groupRepo.findById(b.getGroupId()).orElse(null);
        if (g == null || g.getPayGroupBookingIds() == null || g.getPayGroupBookingIds().isBlank()) {
            return false;
        }
        if (!parseIds(g.getPayGroupBookingIds()).contains(b.getId())) return false;
        java.time.Instant chotLuc = g.getUpdatedAt() != null ? g.getUpdatedAt() : g.getCreatedAt();
        return chotLuc != null && chotLuc.isAfter(java.time.Instant.now().minus(window));
    }

    private static Set<Long> parseIds(String csv) {
        Set<Long> out = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            try { out.add(Long.parseLong(part.trim())); } catch (NumberFormatException ignore) { /* bo qua */ }
        }
        return out;
    }

    public String tokenOf(Long groupId) {
        return groupRepo.findById(groupId).map(GroupBooking::getToken).orElse("");
    }

    /** Chu nhom bam "Ket thuc chuyen di": mo khoa xuat hoa don chia tien; khong cho them phong moi. */
    @Transactional
    public void endTrip(String token, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được kết thúc chuyến đi", HttpStatus.FORBIDDEN);
        }
        g.setEndedAt(LocalDateTime.now());
        groupRepo.save(g);
    }

    /** Mo lai chuyen di (huy trang thai ket thuc). */
    @Transactional
    public void reopenTrip(String token, Long requesterUserId) {
        GroupBooking g = getByToken(token);
        if (!requesterUserId.equals(g.getOrganizerUserId())) {
            throw new BusinessException("FORBIDDEN", "Chỉ người tạo nhóm mới được mở lại chuyến đi", HttpStatus.FORBIDDEN);
        }
        g.setEndedAt(null);
        groupRepo.save(g);
    }

    private String newToken() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
