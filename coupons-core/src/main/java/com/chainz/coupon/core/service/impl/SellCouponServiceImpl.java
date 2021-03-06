package com.chainz.coupon.core.service.impl;

import com.chainz.coupon.core.config.TimeoutConfig;
import com.chainz.coupon.core.credentials.ClientPermission;
import com.chainz.coupon.core.credentials.Operator;
import com.chainz.coupon.core.credentials.OperatorManager;
import com.chainz.coupon.core.exception.CouponExpiredException;
import com.chainz.coupon.core.exception.CouponInsufficientException;
import com.chainz.coupon.core.exception.CouponStatusConflictException;
import com.chainz.coupon.core.exception.InvalidGrantCodeException;
import com.chainz.coupon.core.exception.SellCouponInsufficientException;
import com.chainz.coupon.core.exception.SellCouponNotFoundException;
import com.chainz.coupon.core.model.Coupon;
import com.chainz.coupon.core.model.QCouponDateInfo;
import com.chainz.coupon.core.model.QSellCoupon;
import com.chainz.coupon.core.model.SellCoupon;
import com.chainz.coupon.core.model.SellCouponGrant;
import com.chainz.coupon.core.redis.CouponGrant;
import com.chainz.coupon.core.repository.CouponRepository;
import com.chainz.coupon.core.repository.SellCouponGrantRepository;
import com.chainz.coupon.core.repository.SellCouponRepository;
import com.chainz.coupon.core.repository.common.JoinDescriptor;
import com.chainz.coupon.core.service.SellCouponService;
import com.chainz.coupon.core.utils.CommonUtils;
import com.chainz.coupon.core.utils.Constants;
import com.chainz.coupon.shared.objects.BulkCouponInfo;
import com.chainz.coupon.shared.objects.BulkSellCouponInfo;
import com.chainz.coupon.shared.objects.CouponDateType;
import com.chainz.coupon.shared.objects.CouponStatus;
import com.chainz.coupon.shared.objects.GrantCode;
import com.chainz.coupon.shared.objects.SimpleSellCouponInfo;
import com.chainz.coupon.shared.objects.common.PaginatedApiResult;
import com.querydsl.core.types.dsl.BooleanExpression;
import ma.glasnost.orika.MapperFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/** Sell coupon service implementation. */
@SuppressWarnings({"unchecked"})
@Service
public class SellCouponServiceImpl implements SellCouponService {

  @Autowired private CouponRepository couponRepository;
  @Autowired private SellCouponRepository sellCouponRepository;
  @Autowired private RedisTemplate<String, CouponGrant> couponGrantRedisTemplate;
  @Autowired private MapperFacade mapperFacade;
  @Autowired private SellCouponGrantRepository sellCouponGrantRepository;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private TimeoutConfig timeoutConfig;

  @Override
  @ClientPermission
  @Transactional
  public void granted(String grantCode)
      throws InvalidGrantCodeException, CouponStatusConflictException, CouponInsufficientException,
          CouponExpiredException {
    String key = Constants.COUPON_GRANT_PREFIX + grantCode;
    CouponGrant couponGrant = couponGrantRedisTemplate.opsForValue().get(key);
    if (couponGrant == null) {
      throw new InvalidGrantCodeException(grantCode);
    }
    Coupon coupon = couponRepository.findOne(couponGrant.getCouponId());
    if (coupon.getStatus() != CouponStatus.VERIFIED) {
      throw new CouponStatusConflictException(coupon.getId(), coupon.getStatus());
    }
    CommonUtils.checkCouponExpired(coupon);
    if (coupon.getSku() < couponGrant.getCount()) {
      throw new CouponInsufficientException(coupon.getId());
    }
    Operator operator = OperatorManager.getOperator();
    String openId = operator.getOpenId();
    Integer count = couponGrant.getCount();
    SellCoupon sellCoupon = sellCouponRepository.findOneByOpenIdAndCoupon(openId, coupon);
    if (sellCoupon == null) {
      sellCoupon = new SellCoupon();
      sellCoupon.setCoupon(coupon);
      sellCoupon.setOpenId(openId);
      sellCoupon.setSku(count);
    } else {
      sellCoupon.setSku(sellCoupon.getSku() + count);
    }
    coupon.setSku(coupon.getSku() - count);
    couponRepository.save(coupon);
    sellCouponRepository.save(sellCoupon);
    couponGrantRedisTemplate.delete(key);
  }

  @Override
  @ClientPermission
  @Transactional(readOnly = true)
  public PaginatedApiResult<SimpleSellCouponInfo> listSellCoupon(Pageable pageable) {
    Operator operator = OperatorManager.getOperator();
    String openId = operator.getOpenId();
    QSellCoupon sellCoupon = QSellCoupon.sellCoupon;
    QCouponDateInfo dateInfo = sellCoupon.coupon.dateInfo;
    BooleanExpression predicate =
        sellCoupon
            .openId
            .eq(openId)
            .and(sellCoupon.sku.gt(0))
            .and(sellCoupon.coupon.status.ne(CouponStatus.INVALID))
            .andAnyOf(
                dateInfo.dateType.eq(CouponDateType.DATE_TYPE_FIXED_TERM),
                dateInfo
                    .dateType
                    .eq(CouponDateType.DATE_TYPE_FIXED_TIME_RANGE)
                    .and(dateInfo.timeRangeEnd.goe(LocalDate.now())));
    Page<SellCoupon> coupons =
        sellCouponRepository.findAll(
            predicate, pageable, JoinDescriptor.innerJoin(sellCoupon.coupon));
    return new PaginatedApiResult<>(
        pageable.getPageNumber(),
        pageable.getPageSize(),
        coupons.getNumberOfElements(),
        coupons.getTotalElements(),
        mapperFacade.mapAsList(coupons.getContent(), SimpleSellCouponInfo.class));
  }

  @Override
  @ClientPermission
  @Transactional
  public GrantCode generateSellCouponGrantCode(Long id, Integer count)
      throws SellCouponNotFoundException, SellCouponInsufficientException, CouponExpiredException {
    Operator operator = OperatorManager.getOperator();
    String openId = operator.getOpenId();
    QSellCoupon qSellCoupon = QSellCoupon.sellCoupon;
    // use predicate to join coupon to avoid coupon lazy load
    SellCoupon sellCoupon =
        sellCouponRepository.findOne(
            qSellCoupon.id.eq(id), JoinDescriptor.join(qSellCoupon.coupon));
    if (!openId.equals(sellCoupon.getOpenId())) {
      throw new SellCouponNotFoundException(id);
    }
    if (sellCoupon.getSku() < count) {
      throw new SellCouponInsufficientException(id);
    }
    Coupon coupon = sellCoupon.getCoupon();
    if (coupon.getStatus() == CouponStatus.INVALID) {
      throw new CouponStatusConflictException(coupon.getId(), coupon.getStatus());
    }
    CommonUtils.checkCouponExpired(coupon);
    sellCoupon.setSku(sellCoupon.getSku() - count);
    SellCouponGrant sellCouponGrant =
        SellCouponGrant.newInstance(openId, count, sellCoupon, timeoutConfig.getSellCouponGrant());
    String key = Constants.SELL_COUPON_GRANT_PREFIX + sellCouponGrant.getId();
    sellCouponRepository.save(sellCoupon);
    sellCouponGrantRepository.save(sellCouponGrant);
    stringRedisTemplate
        .opsForValue()
        .set(key, count.toString(), timeoutConfig.getSellCouponGrant(), TimeUnit.SECONDS);
    return new GrantCode(sellCouponGrant.getId());
  }

  @Override
  @ClientPermission
  @Transactional
  public BulkSellCouponInfo getSellCoupon(Long sellCouponId) throws SellCouponNotFoundException {
    // use predicate and join to avoid lazy load coupon
    QSellCoupon qSellCoupon = QSellCoupon.sellCoupon;
    SellCoupon sellCoupon =
        sellCouponRepository.findOne(
            qSellCoupon.id.eq(sellCouponId), JoinDescriptor.join(qSellCoupon.coupon));
    Operator operator = OperatorManager.getOperator();
    if (sellCoupon == null || !operator.getOpenId().equals(sellCoupon.getOpenId())) {
      throw new SellCouponNotFoundException(sellCouponId);
    }
    BulkSellCouponInfo bulkSellCouponInfo = new BulkSellCouponInfo();
    bulkSellCouponInfo.setId(sellCouponId);
    bulkSellCouponInfo.setSku(sellCoupon.getSku());
    BulkCouponInfo bulkCouponInfo = mapperFacade.map(sellCoupon.getCoupon(), BulkCouponInfo.class);
    bulkSellCouponInfo.setCoupon(bulkCouponInfo);
    return bulkSellCouponInfo;
  }
}
