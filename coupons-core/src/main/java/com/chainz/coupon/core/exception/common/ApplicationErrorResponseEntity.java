package com.chainz.coupon.core.exception.common;

import lombok.Data;

/** Error response entity. */
@Data
public class ApplicationErrorResponseEntity {

  private int numericErrorCode;

  private String errorCode;

  private String message;
}
