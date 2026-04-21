// LocationNotFoundException.java
package com.example.CWMS.exception;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(String warehouse, String location) {
        super(String.format("Emplacement '%s' introuvable dans le magasin '%s'",
                location, warehouse));
    }
}