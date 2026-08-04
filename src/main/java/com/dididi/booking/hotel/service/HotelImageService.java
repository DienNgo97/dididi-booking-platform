package com.dididi.booking.hotel.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.api.dto.HotelImageDto;
import com.dididi.booking.hotel.domain.entity.HotelImage;
import com.dididi.booking.hotel.repository.HotelImageRepository;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Quan ly gallery anh khach san: upload (MinIO) + liet ke + xoa + phuc vu bytes. */
@Service
public class HotelImageService {

    private final HotelImageRepository imageRepository;
    private final HotelRepository hotelRepository;
    private final StorageService storageService;

    public HotelImageService(HotelImageRepository imageRepository, HotelRepository hotelRepository,
                             StorageService storageService) {
        this.imageRepository = imageRepository;
        this.hotelRepository = hotelRepository;
        this.storageService = storageService;
    }

    @Transactional
    public HotelImageDto addImage(Long hotelId, MultipartFile file) {
        hotelRepository.findById(hotelId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
        String key = storageService.upload(file, "hotels/" + hotelId);
        int order = (int) imageRepository.countByHotelId(hotelId);
        HotelImage img = new HotelImage();
        img.setHotelId(hotelId);
        img.setObjectKey(key);
        img.setContentType(file.getContentType());
        img.setSortOrder(order);
        imageRepository.save(img);
        return HotelImageDto.from(img);
    }

    public List<HotelImageDto> listImages(Long hotelId) {
        return imageRepository.findByHotelIdOrderBySortOrderAscIdAsc(hotelId)
                .stream().map(HotelImageDto::from).toList();
    }

    /** URL anh dau tien (lam thumbnail) hoac null neu chua co anh. */
    public String firstImageUrl(Long hotelId) {
        return imageRepository.findFirstByHotelIdOrderBySortOrderAscIdAsc(hotelId)
                .map(i -> HotelImageDto.from(i).url()).orElse(null);
    }

    /**
     * URL anh dau tien theo LO (fix M5 N+1 trang /hotels): 1 query IN thay vi 1 query/KS.
     * Chunk 1000 id/luot cho an toan voi IN clause. KS chua co anh -> khong co key trong map.
     */
    public java.util.Map<Long, String> firstImageUrls(java.util.Collection<Long> hotelIds) {
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        if (hotelIds == null || hotelIds.isEmpty()) return out;
        java.util.List<Long> ids = new java.util.ArrayList<>(hotelIds);
        for (int i = 0; i < ids.size(); i += 1000) {
            List<HotelImage> chunk = imageRepository
                    .findByHotelIdInOrderByHotelIdAscSortOrderAscIdAsc(ids.subList(i, Math.min(i + 1000, ids.size())));
            for (HotelImage img : chunk) {
                out.putIfAbsent(img.getHotelId(), HotelImageDto.from(img).url()); // ban ghi dau = anh sort dau
            }
        }
        return out;
    }

    @Transactional
    public void deleteImage(Long hotelId, Long imageId) {
        HotelImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND));
        if (!img.getHotelId().equals(hotelId)) {
            throw new BusinessException("FORBIDDEN", "Ảnh không thuộc khách sạn này", HttpStatus.FORBIDDEN);
        }
        storageService.remove(img.getObjectKey());
        imageRepository.delete(img);
    }

    public StorageService.StoredObject loadImage(Long hotelId, Long imageId) {
        HotelImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND));
        if (!img.getHotelId().equals(hotelId)) {
            throw new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND);
        }
        return storageService.load(img.getObjectKey());
    }
}
