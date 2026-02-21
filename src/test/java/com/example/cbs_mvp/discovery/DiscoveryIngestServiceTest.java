package com.example.cbs_mvp.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cbs_mvp.dto.discovery.CsvIngestResultResponse;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingResponse;

import com.example.cbs_mvp.service.StateTransitionService;

@ExtendWith(MockitoExtension.class)
class DiscoveryIngestServiceTest {

        @Mock
        private DiscoveryItemRepository repository;
        @Mock
        private DiscoveryScoringService scoringService;
        @Mock
        private PricingCalculator pricingCalculator;
        @Mock
        private FxRateService fxRateService;
        @Mock
        private StateTransitionService transitions;

        private DiscoveryItemValidator validator;
        private DiscoveryIngestService service;

        @BeforeEach
        void setUp() {
                validator = new DiscoveryItemValidator();
                service = new DiscoveryIngestService(repository, scoringService, validator, pricingCalculator,
                                fxRateService,
                                transitions);
        }

        @Test
        void ingestFromCsv_singleNewItem_insertsSuccessfully() throws Exception {
                // parseCsvLine column order: sourceUrl, title, priceYen, weightKg, condition
                String csv = "source_url,title,price_yen,weight_kg,condition\n" +
                                "https://example.com/item1,Test Item,10000,1.5,NEW\n";
                InputStream inputStream = new ByteArrayInputStream(csv.getBytes());

                when(repository.findBySourceUrl("https://example.com/item1")).thenReturn(java.util.Optional.empty());
                when(fxRateService.getCurrentRate())
                                .thenReturn(new FxRateService.FxRateResult(new BigDecimal("150.0"), null, null));
                when(pricingCalculator.calculate(any())).thenReturn(
                                PricingResponse.builder()
                                                .profitRate(new BigDecimal("0.25"))
                                                .gateProfitOk(true)
                                                .build());
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                CsvIngestResultResponse result = service.ingestFromCsv(inputStream);

                assertEquals(1, result.inserted());
                assertEquals(0, result.updated());
                assertEquals(0, result.errors().size());
                verify(repository, times(1)).save(any(DiscoveryItem.class));
        }

        @Test
        void ingestFromCsv_existingItem_updatesSuccessfully() throws Exception {
                // parseCsvLine column order: sourceUrl, title, priceYen, weightKg, condition
                String csv = "source_url,title,price_yen,weight_kg,condition\n" +
                                "https://example.com/item2,Updated Title,20000,2.0,USED\n";
                InputStream inputStream = new ByteArrayInputStream(csv.getBytes());

                DiscoveryItem existingItem = new DiscoveryItem();
                existingItem.setId(1L);
                existingItem.setSourceUrl("https://example.com/item2");
                existingItem.setPriceYen(new BigDecimal("15000"));

                when(repository.findBySourceUrl("https://example.com/item2"))
                                .thenReturn(java.util.Optional.of(existingItem));
                when(fxRateService.getCurrentRate())
                                .thenReturn(new FxRateService.FxRateResult(new BigDecimal("150.0"), null, null));
                when(pricingCalculator.calculate(any())).thenReturn(
                                PricingResponse.builder()
                                                .profitRate(new BigDecimal("0.30"))
                                                .gateProfitOk(true)
                                                .build());
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                CsvIngestResultResponse result = service.ingestFromCsv(inputStream);

                assertEquals(0, result.inserted());
                assertEquals(1, result.updated());
                assertEquals(0, result.errors().size());
                assertEquals(new BigDecimal("20000"), existingItem.getPriceYen());
                assertEquals("Updated Title", existingItem.getTitle());
        }

        @Test
        void ingestFromCsv_invalidRow_recordsError() throws Exception {
                // parseCsvLine: sourceUrl, title, priceYen, weightKg, condition
                String csv = "source_url,title,price_yen,weight_kg,condition\n" +
                                "https://example.com/item3,Valid Item,10000,1.0,NEW\n" +
                                ",,,,\n" +
                                "https://example.com/item4,No Price,,,NEW\n";
                InputStream inputStream = new ByteArrayInputStream(csv.getBytes());

                when(repository.findBySourceUrl("https://example.com/item3")).thenReturn(java.util.Optional.empty());
                when(fxRateService.getCurrentRate())
                                .thenReturn(new FxRateService.FxRateResult(new BigDecimal("150.0"), null, null));
                when(pricingCalculator.calculate(any())).thenReturn(
                                PricingResponse.builder()
                                                .profitRate(new BigDecimal("0.20"))
                                                .gateProfitOk(true)
                                                .build());
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                CsvIngestResultResponse result = service.ingestFromCsv(inputStream);

                assertEquals(1, result.inserted()); // Only first row succeeds
                assertEquals(0, result.updated());
                assertEquals(2, result.errors().size()); // 2 error rows
                assertTrue(result.errors().get(0).message().contains("sourceUrl"));
                assertTrue(result.errors().get(1).message().contains("priceYen"));
        }
}
