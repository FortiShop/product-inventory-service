package org.fortishop.productinventoryservice.exception.Product;

import org.fortishop.productinventoryservice.global.exception.BaseException;
import org.fortishop.productinventoryservice.global.exception.BaseExceptionType;

public class ProductException extends BaseException {
    private final BaseExceptionType exceptionType;

    public ProductException(BaseExceptionType exceptionType) {
        this.exceptionType = exceptionType;
    }

    @Override
    public BaseExceptionType getExceptionType() {
        return exceptionType;
    }
}
