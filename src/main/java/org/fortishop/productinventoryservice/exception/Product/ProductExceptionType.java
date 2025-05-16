package org.fortishop.productinventoryservice.exception.Product;

import org.fortishop.productinventoryservice.global.exception.BaseExceptionType;
import org.springframework.http.HttpStatus;

public enum ProductExceptionType implements BaseExceptionType {
    PRODUCT_NOT_FOUND("P001", "일치하는 상품이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_USER("P002", "잘못된 권한의 요청입니다.", HttpStatus.UNAUTHORIZED),
    ;

    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus httpStatus;

    ProductExceptionType(String errorCode, String errorMessage, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String getErrorMessage() {
        return this.errorMessage;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
