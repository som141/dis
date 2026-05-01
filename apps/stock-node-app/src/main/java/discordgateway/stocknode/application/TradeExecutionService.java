package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.RankingCacheRepository;
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

    private static final int MIN_LEVERAGE = 1;
    private static final int MAX_LEVERAGE = 50;
    private static final String MAX_LEVERAGE_WARNING =
            "50배 레버리지는 약 2%만 불리하게 움직여도 포지션 평가금액이 0원에 가까워질 수 있습니다.";

    private final DailyAllowanceService dailyAllowanceService;
    private final StockWatchlistService stockWatchlistService;
    private final StockAccountRepository stockAccountRepository;
    private final StockPositionRepository stockPositionRepository;
    private final TradeLedgerRepository tradeLedgerRepository;
    private final QuoteService quoteService;
    private final RankingCacheRepository rankingCacheRepository;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public TradeExecutionService(
            DailyAllowanceService dailyAllowanceService,
            StockWatchlistService stockWatchlistService,
            StockAccountRepository stockAccountRepository,
            StockPositionRepository stockPositionRepository,
            TradeLedgerRepository tradeLedgerRepository,
            QuoteService quoteService,
            RankingCacheRepository rankingCacheRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.dailyAllowanceService = dailyAllowanceService;
        this.stockWatchlistService = stockWatchlistService;
        this.stockAccountRepository = stockAccountRepository;
        this.stockPositionRepository = stockPositionRepository;
        this.tradeLedgerRepository = tradeLedgerRepository;
        this.quoteService = quoteService;
        this.rankingCacheRepository = rankingCacheRepository;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    @Transactional
    public TradeExecutionResult buy(long guildId, long userId, String symbol, BigDecimal amount, int leverage) {
        validatePositive(amount, "매수 금액");
        validateLeverage(leverage);
        stockWatchlistService.validateTradable(stockQuoteProperties.getDefaultMarket(), symbol);
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        StockQuoteResult quoteResult = quoteService.getQuote(
                stockQuoteProperties.getDefaultMarket(),
                symbol,
                QuoteUsage.TRADE
        );
        ensureFreshQuote(quoteResult, symbol);

        BigDecimal requestedMargin = scaleCash(amount, RoundingMode.DOWN);
        BigDecimal targetNotional = requestedMargin.multiply(BigDecimal.valueOf(leverage));
        BigDecimal executedQuantity = targetNotional.divide(quoteResult.quote().price(), 8, RoundingMode.DOWN);
        if (executedQuantity.signum() <= 0) {
            throw new InvalidTradeArgumentException("현재 시세 기준으로 매수 금액이 너무 작습니다.");
        }

        BigDecimal actualNotional = scaleCash(executedQuantity.multiply(quoteResult.quote().price()), RoundingMode.DOWN);
        BigDecimal marginAmount = scaleCash(
                actualNotional.divide(BigDecimal.valueOf(leverage), 4, RoundingMode.DOWN),
                RoundingMode.DOWN
        );
        if (marginAmount.signum() <= 0) {
            throw new InvalidTradeArgumentException("요청한 레버리지 기준으로 매수 금액이 너무 작습니다.");
        }
        if (account.getCashBalance().compareTo(marginAmount) < 0) {
            throw new InsufficientCashException("Not enough cash to buy " + StockQuote.normalizeSymbol(symbol));
        }

        String normalizedSymbol = StockQuote.normalizeSymbol(symbol);
        StockPositionEntity position = stockPositionRepository.findByAccountIdAndSymbol(account.getId(), normalizedSymbol)
                .orElseGet(() -> StockPositionEntity.create(account, normalizedSymbol, leverage));
        if (!position.isEmpty() && position.getLeverage() != leverage) {
            throw new LeverageMismatchException("Existing position for " + normalizedSymbol + " uses " + position.getLeverage() + "x leverage");
        }

        account.subtractCash(marginAmount);
        position.applyBuy(executedQuantity, quoteResult.quote().price(), marginAmount, actualNotional, leverage);

        stockAccountRepository.save(account);
        stockPositionRepository.save(position);
        tradeLedgerRepository.save(
                TradeLedgerEntity.create(
                        account,
                        normalizedSymbol,
                        TradeSide.BUY.name(),
                        executedQuantity,
                        quoteResult.quote().price(),
                        leverage,
                        marginAmount,
                        actualNotional,
                        clock.instant()
                )
        );
        rankingCacheRepository.evictGuild(guildId, account.getSeasonKey());

        return new TradeExecutionResult(
                account.getId(),
                guildId,
                userId,
                TradeSide.BUY,
                quoteResult.quote().market(),
                normalizedSymbol,
                requestedMargin,
                null,
                leverage,
                marginAmount,
                actualNotional,
                executedQuantity,
                quoteResult.quote().price(),
                marginAmount,
                account.getCashBalance(),
                position.getQuantity(),
                position.getAverageCost(),
                leverage == MAX_LEVERAGE ? MAX_LEVERAGE_WARNING : null
        );
    }

    @Transactional
    public TradeExecutionResult sell(long guildId, long userId, String symbol, BigDecimal quantity) {
        validatePositive(quantity, "매도 수량");
        stockWatchlistService.validateTradable(stockQuoteProperties.getDefaultMarket(), symbol);
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
        BigDecimal quantityRatio = normalizedQuantity.divide(position.getQuantity(), 16, RoundingMode.HALF_UP);
        BigDecimal releasedMarginAmount = scaleCash(position.getMarginAmount().multiply(quantityRatio), RoundingMode.DOWN);
        BigDecimal releasedNotionalAmount = scaleCash(position.getNotionalAmount().multiply(quantityRatio), RoundingMode.DOWN);
        BigDecimal realizedProfitLoss = scaleCash(
                normalizedQuantity.multiply(quoteResult.quote().price().subtract(position.getAverageCost())),
                RoundingMode.DOWN
        );
        BigDecimal settledAmount = scaleCash(
                releasedMarginAmount.add(realizedProfitLoss).max(BigDecimal.ZERO.setScale(4, RoundingMode.DOWN)),
                RoundingMode.DOWN
        );

        account.addCash(settledAmount);
        position.applySell(normalizedQuantity, releasedMarginAmount, releasedNotionalAmount);

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
                        position.getLeverage(),
                        releasedMarginAmount,
                        releasedNotionalAmount,
                        clock.instant()
                )
        );
        rankingCacheRepository.evictGuild(guildId, account.getSeasonKey());

        return new TradeExecutionResult(
                account.getId(),
                guildId,
                userId,
                TradeSide.SELL,
                quoteResult.quote().market(),
                normalizedSymbol,
                null,
                normalizedQuantity,
                position.getLeverage(),
                releasedMarginAmount,
                releasedNotionalAmount,
                normalizedQuantity,
                quoteResult.quote().price(),
                settledAmount,
                account.getCashBalance(),
                position.isEmpty() ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : position.getQuantity(),
                position.isEmpty() ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : position.getAverageCost(),
                position.getLeverage() == MAX_LEVERAGE ? MAX_LEVERAGE_WARNING : null
        );
    }

    private void validatePositive(BigDecimal amount, String label) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTradeArgumentException(label + "은(는) 0보다 커야 합니다.");
        }
    }

    private void ensureFreshQuote(StockQuoteResult quoteResult, String symbol) {
        if (!quoteResult.fresh()) {
            throw new StaleQuoteException(
                    "Current quote is older than the 45 second trade freshness limit for "
                            + StockQuote.normalizeSymbol(symbol)
                            + ". Please try again shortly."
            );
        }
    }

    private void validateLeverage(int leverage) {
        if (leverage < MIN_LEVERAGE || leverage > MAX_LEVERAGE) {
            throw new InvalidLeverageException("Leverage must be between " + MIN_LEVERAGE + " and " + MAX_LEVERAGE);
        }
    }

    private BigDecimal scaleCash(BigDecimal value, RoundingMode roundingMode) {
        return value.setScale(4, roundingMode);
    }
}
