package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.entity.TradeLedgerEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;

public class TradeExecutionService {

    private final DailyAllowanceService dailyAllowanceService;
    private final StockAccountRepository stockAccountRepository;
    private final StockPositionRepository stockPositionRepository;
    private final TradeLedgerRepository tradeLedgerRepository;
    private final QuoteService quoteService;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public TradeExecutionService(
            DailyAllowanceService dailyAllowanceService,
            StockAccountRepository stockAccountRepository,
            StockPositionRepository stockPositionRepository,
            TradeLedgerRepository tradeLedgerRepository,
            QuoteService quoteService,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.dailyAllowanceService = dailyAllowanceService;
        this.stockAccountRepository = stockAccountRepository;
        this.stockPositionRepository = stockPositionRepository;
        this.tradeLedgerRepository = tradeLedgerRepository;
        this.quoteService = quoteService;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    @Transactional
    public TradeExecutionResult buy(long guildId, long userId, String symbol, BigDecimal amount) {
        validatePositive(amount, "buy amount");
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        StockQuoteResult quoteResult = quoteService.getQuote(
                stockQuoteProperties.getDefaultMarket(),
                symbol,
                QuoteUsage.TRADE
        );
        ensureFreshQuote(quoteResult, symbol);

        BigDecimal executedQuantity = amount.divide(quoteResult.quote().price(), 8, RoundingMode.DOWN);
        if (executedQuantity.signum() <= 0) {
            throw new InvalidTradeArgumentException("Buy amount is too small for the current quote");
        }

        BigDecimal settledAmount = scaleCash(executedQuantity.multiply(quoteResult.quote().price()), RoundingMode.DOWN);
        if (account.getCashBalance().compareTo(settledAmount) < 0) {
            throw new InsufficientCashException("Not enough cash to buy " + StockQuote.normalizeSymbol(symbol));
        }

        String normalizedSymbol = StockQuote.normalizeSymbol(symbol);
        StockPositionEntity position = stockPositionRepository.findByAccountIdAndSymbol(account.getId(), normalizedSymbol)
                .orElseGet(() -> StockPositionEntity.create(account, normalizedSymbol));

        account.subtractCash(settledAmount);
        position.applyBuy(executedQuantity, quoteResult.quote().price());

        stockAccountRepository.save(account);
        stockPositionRepository.save(position);
        tradeLedgerRepository.save(
                TradeLedgerEntity.create(
                        account,
                        normalizedSymbol,
                        TradeSide.BUY.name(),
                        executedQuantity,
                        quoteResult.quote().price(),
                        clock.instant()
                )
        );

        return new TradeExecutionResult(
                account.getId(),
                guildId,
                userId,
                TradeSide.BUY,
                quoteResult.quote().market(),
                normalizedSymbol,
                scaleCash(amount, RoundingMode.DOWN),
                null,
                executedQuantity,
                quoteResult.quote().price(),
                settledAmount,
                account.getCashBalance(),
                position.getQuantity(),
                position.getAverageCost()
        );
    }

    @Transactional
    public TradeExecutionResult sell(long guildId, long userId, String symbol, BigDecimal quantity) {
        validatePositive(quantity, "sell quantity");
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        String normalizedSymbol = StockQuote.normalizeSymbol(symbol);
        StockPositionEntity position = stockPositionRepository.findByAccountIdAndSymbol(account.getId(), normalizedSymbol)
                .orElseThrow(() -> new InsufficientQuantityException("No holding exists for " + normalizedSymbol));
        if (!position.hasEnoughQuantity(quantity)) {
            throw new InsufficientQuantityException("Not enough quantity to sell " + normalizedSymbol);
        }

        StockQuoteResult quoteResult = quoteService.getQuote(
                stockQuoteProperties.getDefaultMarket(),
                normalizedSymbol,
                QuoteUsage.TRADE
        );
        ensureFreshQuote(quoteResult, normalizedSymbol);

        BigDecimal normalizedQuantity = quantity.setScale(8, RoundingMode.HALF_UP);
        BigDecimal settledAmount = scaleCash(normalizedQuantity.multiply(quoteResult.quote().price()), RoundingMode.DOWN);

        account.addCash(settledAmount);
        position.applySell(normalizedQuantity);

        stockAccountRepository.save(account);
        if (position.isEmpty()) {
            stockPositionRepository.delete(position);
        } else {
            stockPositionRepository.save(position);
        }
        tradeLedgerRepository.save(
                TradeLedgerEntity.create(
                        account,
                        normalizedSymbol,
                        TradeSide.SELL.name(),
                        normalizedQuantity,
                        quoteResult.quote().price(),
                        clock.instant()
                )
        );

        return new TradeExecutionResult(
                account.getId(),
                guildId,
                userId,
                TradeSide.SELL,
                quoteResult.quote().market(),
                normalizedSymbol,
                null,
                normalizedQuantity,
                normalizedQuantity,
                quoteResult.quote().price(),
                settledAmount,
                account.getCashBalance(),
                position.isEmpty() ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : position.getQuantity(),
                position.isEmpty() ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : position.getAverageCost()
        );
    }

    private void validatePositive(BigDecimal amount, String label) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTradeArgumentException(label + " must be positive");
        }
    }

    private void ensureFreshQuote(StockQuoteResult quoteResult, String symbol) {
        if (!quoteResult.fresh()) {
            throw new StaleQuoteException("Trade quote is stale for " + StockQuote.normalizeSymbol(symbol));
        }
    }

    private BigDecimal scaleCash(BigDecimal value, RoundingMode roundingMode) {
        return value.setScale(4, roundingMode);
    }
}
