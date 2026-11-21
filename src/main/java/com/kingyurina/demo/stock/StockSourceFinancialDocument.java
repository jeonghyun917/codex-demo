package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A separate source identity; never a fabricated SEC standard row. admittedAtUtc is actual system admission. */
public record StockSourceFinancialDocument(String packVersion,String identitySha256,String symbol,
        LocalDate publicationDate,LocalDateTime admittedAtUtc,String payloadJson) { }
