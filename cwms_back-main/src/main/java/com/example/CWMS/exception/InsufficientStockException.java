// InsufficientStockException.java
package com.example.CWMS.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String item, String location,
                                      String lot, Object available, Object requested) {
        super(String.format(
                "Stock insuffisant pour article '%s' emplacement '%s' lot '%s': " +
                        "disponible=%s, demandé=%s", item, location, lot, available, requested));
    }
}