package com.mkwang.backend.modules.profile.entity;

import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * UserProfile entity - Contains personal, HR info and bank information.
 * One-to-One relationship with User (Shares primary key via @MapsId).
 */
@Entity
@Table(name = "user_profiles", indexes = {
    @Index(name = "idx_user_profiles_employee_code", columnList = "employee_code"),
    @Index(name = "idx_user_profiles_phone_number", columnList = "phone_number")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // ─── 1. HR Identity ────────────────────────────────────────────

    @Column(name = "employee_code", length = 20, unique = true)
    private String employeeCode;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // ─── 2. Personal Info ──────────────────────────────────────────

    @Column(name = "phone_number", length = 15, unique = true)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "citizen_id", length = 20)
    private String citizenId;

    @Column(name = "address", length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_file_id")
    private FileStorage avatarFile;

    // ─── 3. Bank Info (Salary) ─────────────────────────────────────

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_num", length = 30)
    private String bankAccountNum;

    @Column(name = "bank_account_owner", length = 100)
    private String bankAccountOwner;
}
