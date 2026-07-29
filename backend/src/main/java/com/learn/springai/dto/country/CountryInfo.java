package com.learn.springai.dto.country;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CountryInfo {
    private String name; // India
    private String iso2; // IN
    private String currency; // INR
}