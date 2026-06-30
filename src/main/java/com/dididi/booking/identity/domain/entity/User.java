package com.dididi.booking.identity.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** Chi co gia tri khi role = VENDOR. */
    @Column(name = "vendor_id")
    private Long vendorId;

    /** Cong ty (B2B) nhan vien truc thuoc - null neu khach le. */
    @Column(name = "company_id")
    private Long companyId;

    /** So dien thoai da xac thuc OTP chua. */
    @Column(name = "phone_verified", columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean phoneVerified = false;

    /** Tai khoan da lien ket dang nhap Google chua. */
    @Column(name = "google_linked", columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean googleLinked = false;

    /**
     * User da co mat khau dung de dang nhap form chua.
     * = false cho user tao qua Google (mat khau ngau nhien). DEFAULT 1 de moi user cu deu coi nhu da co.
     */
    @Column(name = "password_set", columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean passwordSet = true;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public boolean isGoogleLinked() { return googleLinked; }
    public void setGoogleLinked(boolean googleLinked) { this.googleLinked = googleLinked; }

    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }
}
