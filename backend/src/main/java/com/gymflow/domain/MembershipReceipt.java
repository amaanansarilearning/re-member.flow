package com.gymflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "membership_receipts")
public class MembershipReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false, unique = true)
    private Membership membership;

    @Lob
    @Column(name = "receipt_image", nullable = false)
    private byte[] image;

    @Column(name = "receipt_image_content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "receipt_image_filename", nullable = false, length = 80)
    private String filename;

    protected MembershipReceipt() {
    }

    public MembershipReceipt(Membership membership, byte[] image, String contentType, String filename) {
        this.membership = membership;
        this.image = image;
        this.contentType = contentType;
        this.filename = filename;
    }

    public byte[] getImage() {
        return image;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilename() {
        return filename;
    }
}