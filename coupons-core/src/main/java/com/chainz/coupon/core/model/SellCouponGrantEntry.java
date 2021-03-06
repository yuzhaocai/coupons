package com.chainz.coupon.core.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Sell coupon grant entry.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@Table(
  name = "sell_coupon_grant_entries",
  indexes = {
    @Index(columnList = "sell_open_id"),
    @Index(columnList = "created_at"),
    @Index(columnList = "coupon_code"),
    @Index(columnList = "sell_open_id, created_at")
  }
)
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
public class SellCouponGrantEntry implements Serializable {

  private static final long serialVersionUID = -2028805814453894559L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  // indicate the seller's openId
  @Column(name = "sell_open_id")
  private String sellOpenId;

  @Column(name = "coupon_code")
  private String couponCode;

  // indicate who get the user coupon
  @Column(name = "open_id")
  private String openId;

  // or this is not a wechat user.
  @Column(name = "user_id")
  private String userId;

  @ManyToOne(cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
  @JoinColumn(name = "sell_coupon_grant_id")
  private SellCouponGrant sellCouponGrant;

  // indicate when get the user coupon from seller.
  @CreatedDate
  @Column(name = "created_at")
  private ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private ZonedDateTime updatedAt;

  @Version
  private Integer rev;

  /**
   * Default constructor.
   */
  public SellCouponGrantEntry() {
  }

  /**
   * Constructor.
   *
   * @param sellCouponGrant sell coupon grant.
   * @param sellOpenId      sell open id.
   * @param openId          open id.
   * @param couponCode      coupon code.
   */
  public SellCouponGrantEntry(
    SellCouponGrant sellCouponGrant, String sellOpenId, String openId, String couponCode) {
    this.sellCouponGrant = sellCouponGrant;
    this.sellOpenId = sellOpenId;
    this.openId = openId;
    this.couponCode = couponCode;
  }
}
