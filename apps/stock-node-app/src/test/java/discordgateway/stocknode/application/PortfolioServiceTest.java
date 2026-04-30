package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private StockPositionRepository stockPositionRepository;

    @Mock
    private QuoteService quoteService;

    @Test
    void calculatesPortfolioTotalsFromHoldingsAndQuotes() {
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setDefaultMarket("us");
        PortfolioService portfolioService = new PortfolioService(
                stockPositionRepository,
                quoteService,
                stockQuoteProperties
        );

        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        ReflectionTestUtils.setField(account, "id", 10L);
        account.updateCashBalance(new BigDecimal("1000.0000"));

        StockPositionEntity aapl = StockPositionEntity.create(account, "AAPL");
        aapl.applyBuy(new BigDecimal("2.00000000"), new BigDecimal("150.00"));
        StockPositionEntity msft = StockPositionEntity.create(account, "MSFT");
        msft.applyBuy(new BigDecimal("1.00000000"), new BigDecimal("100.00"));
        when(stockPositionRepository.findAllByAccountIdOrderBySymbolAsc(10L)).thenReturn(List.of(aapl, msft));
        when(quoteService.getQuote("us", "AAPL", QuoteUsage.QUERY)).thenReturn(quote("AAPL", "200.00"));
        when(quoteService.getQuote("us", "MSFT", QuoteUsage.QUERY)).thenReturn(quote("MSFT", "80.00"));

        PortfolioView portfolioView = portfolioService.build(account, QuoteUsage.QUERY);

        assertThat(portfolioView.totalMarketValue()).isEqualByComparingTo("480.0000");
        assertThat(portfolioView.totalCostBasis()).isEqualByComparingTo("400.0000");
        assertThat(portfolioView.totalEquity()).isEqualByComparingTo("1480.0000");
        assertThat(portfolioView.totalProfitLoss()).isEqualByComparingTo("80.0000");
        assertThat(portfolioView.positions()).hasSize(2);
    }

    private StockQuoteResult quote(String symbol, String price) {
        return new StockQuoteResult(
                new StockQuote("us", symbol, new BigDecimal(price), Instant.parse("2026-04-30T01:00:00Z")),
                QuoteSource.CACHE_FRESH,
                true
        );
    }
}
