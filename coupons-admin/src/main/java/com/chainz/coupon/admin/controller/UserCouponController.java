package com.chainz.coupon.admin.controller;

import com.chainz.coupon.core.exception.CouponStatusConflictException;
import com.chainz.coupon.core.exception.InvalidGrantCodeException;
import com.chainz.coupon.core.exception.SellCouponGrantInsufficientException;
import com.chainz.coupon.core.exception.SellCouponGrantStatusConflictException;
import com.chainz.coupon.core.exception.UserCouponNotFoundException;
import com.chainz.coupon.core.service.UserCouponService;
import com.chainz.coupon.shared.objects.SimpleUserCouponInfo;
import com.chainz.coupon.shared.objects.UserCouponInfo;
import com.chainz.coupon.shared.objects.common.PaginatedApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

/** user coupon controller. */
@RestController
@Validated
@RequestMapping("/api/user-coupons")
public class UserCouponController {

  @Autowired private UserCouponService userCouponService;

  /**
   * Enable user to get coupon via grant code from seller.
   *
   * @param grantCode grant code.
   * @throws InvalidGrantCodeException invalid grant code.
   * @throws CouponStatusConflictException coupon status conflict.
   * @throws SellCouponGrantInsufficientException sell coupon grant insufficient.
   * @throws SellCouponGrantStatusConflictException sell coupon grant status conflict.
   */
  @RequestMapping(
    value = "/granted/{grantCode}",
    method = RequestMethod.POST,
    consumes = "application/json"
  )
  @ResponseStatus(HttpStatus.CREATED)
  public void granted(@PathVariable String grantCode)
      throws InvalidGrantCodeException, CouponStatusConflictException,
          SellCouponGrantInsufficientException, SellCouponGrantStatusConflictException {
    userCouponService.granted(grantCode);
  }

  /**
   * List active user coupon.
   *
   * @param page coupon pagination page.
   * @param size coupon pagination size.
   * @param sort coupon pagination sort, gotAt for default.
   * @param order coupon pagination order, desc for default.
   * @return active user coupon list.
   */
  @RequestMapping(value = "/active", method = RequestMethod.GET, produces = "application/json")
  public PaginatedApiResult<SimpleUserCouponInfo> listActiveUserCoupon(
      @Min(0) @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
      @Min(1) @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
      @Pattern(regexp = "gotAt|endDate")
          @RequestParam(value = "sort", required = false, defaultValue = "gotAt")
          String sort,
      @Pattern(regexp = "asc|desc")
          @RequestParam(value = "order", required = false, defaultValue = "desc")
          String order) {
    return userCouponService.listActiveUserCoupon(
        new PageRequest(page, size, Sort.Direction.fromString(order), sort));
  }

  /**
   * List expired user coupon.
   *
   * @param page coupon pagination page.
   * @param size coupon pagination size.
   * @return expired user coupon list.
   */
  @RequestMapping(value = "/expired", method = RequestMethod.GET, produces = "application/json")
  public PaginatedApiResult<SimpleUserCouponInfo> listExpiredUserCoupon(
      @Min(0) @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
      @Min(1) @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
      @Pattern(regexp = "gotAt|endDate")
          @RequestParam(value = "sort", required = false, defaultValue = "endDate")
          String sort,
      @Pattern(regexp = "asc|desc")
          @RequestParam(value = "order", required = false, defaultValue = "desc")
          String order) {
    return userCouponService.listExpiredUserCoupon(
        new PageRequest(page, size, Sort.Direction.fromString(order), sort));
  }

  /**
   * Get user coupon.
   *
   * @param id user coupon id.
   * @return user coupon information.
   * @throws UserCouponNotFoundException user coupon not found.
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = "application/json")
  public UserCouponInfo getUserCoupon(@PathVariable Long id) throws UserCouponNotFoundException {
    return userCouponService.getUserCoupon(id);
  }
}
