package com.dididi.booking.invoice;

import com.dididi.booking.invoice.service.InvoiceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BP-INV-01: VAT split bang BigDecimal -> preTax + vat == total chinh xac (10% va cac rate khac).
 */
class InvoiceVatTest {

    @Test
    void preTaxPlusVat_equalsTotal_at10Percent() {
        BigDecimal rate = new BigDecimal("0.10");
        for (String t : new String[]{"1000000", "999999", "333333", "1", "2500000", "10000001"}) {
            BigDecimal total = new BigDecimal(t);
            BigDecimal[] split = InvoiceService.vatSplit(total, rate);
            assertThat(split[0].add(split[1]))
                    .as("preTax + vat == total cho total=%s", t)
                    .isEqualByComparingTo(total);
        }
    }

    @Test
    void percentLabel_formatsCleanly() {
        assertThat(InvoiceService.vatPercentLabel(new BigDecimal("0.10"))).isEqualTo("10");
        assertThat(InvoiceService.vatPercentLabel(new BigDecimal("0.085"))).isEqualTo("8.5");
        assertThat(InvoiceService.vatPercentLabel(new BigDecimal("0.08"))).isEqualTo("8");
    }

    @Test
    void preTaxPlusVat_equalsTotal_atOtherRate() {
        BigDecimal rate = new BigDecimal("0.085");
        BigDecimal total = new BigDecimal("1234567");
        BigDecimal[] split = InvoiceService.vatSplit(total, rate);
        assertThat(split[0].add(split[1])).isEqualByComparingTo(total);
        assertThat(split[1].signum()).isGreaterThanOrEqualTo(0);   // VAT khong am
    }
}
