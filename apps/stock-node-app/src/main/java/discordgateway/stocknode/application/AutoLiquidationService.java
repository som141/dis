package discordgateway.stocknode.application;

import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.entity.TradeLedgerEntity;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;

public class AutoLiquidationService {

    private static final Logger log = LoggerFactory.getLogger(AutoLiquidationService.class);

    private final StockPositionRepository stockPositionRepository;
    private final TradeLedgerRepository tradeLedgerRepository;
    private final RankingCacheRepository rankingCacheRepository;
    private final Clock clock;
    private final TransactionOperations transactionOperations;

    public AutoLiquidationService(
            StockPositionRepository stockPositionRepository,
            TradeLedgerRepository tradeLedgerRepository,
            RankingCacheRepository rankingCacheRepository,
            Clock clock,
            TransactionOperations transactionOperations
    ) {
        this.stockPositionRepository = stockPositionRepository;
        this.tradeLedgerRepository = tradeLedgerRepository;
        this.rankingCacheRepository = rankingCacheRepository;
        this.clock = clock;
        this.transactionOperations = transactionOperations;
    }

    public LiquidationBatchResult liquidateExhaustedPositions(StockQuote quote) {
        String normalizedSymbol = StockQuote.normalizeSymbol(quote.symbol());
        List<Long> positionIds = stockPositionRepository.findAllBySymbolOrderByIdAsc(normalizedSymbol).stream()
                .map(StockPositionEntity::getId)
                .toList();

        int liquidatedCount = 0;
        int failureCount = 0;
        for (Long positionId : positionIds) {
            try {
                boolean liquidated = Boolean.TRUE.equals(
                        transactionOperations.execute(status -> liquidatePositionIfNeeded(positionId, quote))
                );
                if (liquidated) {
                    liquidatedCount++;
                }
            } catch (Exception exception) {
                failureCount++;
                log.warn(
                        "failed to liquidate stock position positionId={} symbol={} quotePrice={}",
                        positionId,
                        normalizedSymbol,
                        quote.price(),
                        exception
                );
            }
        }

        return new LiquidationBatchResult(
                normalizedSymbol,
                positionIds.size(),
                liquidatedCount,
                failureCount
        );
    }

    BigDecimal calculateIsolatedEquity(StockPositionEntity position, BigDecimal currentPrice) {
        BigDecimal unrealizedProfitLoss = scaleCash(
                currentPrice.subtract(position.getAverageCost()).multiply(position.getQuantity()),
                RoundingMode.HALF_UP
        );
        return scaleCash(position.getMarginAmount().add(unrealizedProfitLoss), RoundingMode.HALF_UP);
    }

    private boolean liquidatePositionIfNeeded(Long positionId, StockQuote quote) {
        StockPositionEntity position = stockPositionRepository.findById(positionId).orElse(null);
        if (position == null || position.isEmpty()) {
            return false;
        }

        String normalizedSymbol = StockQuote.normalizeSymbol(quote.symbol());
        if (!normalizedSymbol.equals(position.getSymbol())) {
            return false;
        }

        BigDecimal currentPrice = scaleCash(quote.price(), RoundingMode.HALF_UP);
        BigDecimal isolatedEquity = calculateIsolatedEquity(position, currentPrice);
        if (isolatedEquity.signum() > 0) {
            return false;
        }

        StockAccountEntity account = position.getAccount();
        BigDecimal fullQuantity = position.getQuantity();
        BigDecimal releasedMarginAmount = position.getMarginAmount();
        BigDecimal releasedNotionalAmount = position.getNotionalAmount();
        int leverage = position.getLeverage();
        BigDecimal averageCost = position.getAverageCost();

        position.applySell(fullQuantity, releasedMarginAmount, releasedNotionalAmount);
        stockPositionRepository.delete(position);
        tradeLedgerRepository.save(
                TradeLedgerEntity.create(
                        account,
                        normalizedSymbol,
                        TradeSide.SELL.name(),
                        fullQuantity,
                        currentPrice,
                        leverage,
                        releasedMarginAmount,
                        releasedNotionalAmount,
                        clock.instant()
                )
        );
        rankingCacheRepository.evictGuild(account.getGuildId(), account.getSeasonKey());

        log.info(
                "auto liquidated stock position accountId={} guildId={} userId={} seasonKey={} symbol={} leverage={} averageCost={} quotePrice={} quantity={} isolatedEquity={}",
                account.getId(),
                account.getGuildId(),
                account.getUserId(),
                account.getSeasonKey(),
                normalizedSymbol,
                leverage,
                averageCost,
                currentPrice,
                fullQuantity,
                isolatedEquity
        );
        return true;
    }

    private BigDecimal scaleCash(BigDecimal value, RoundingMode roundingMode) {
        return value.setScale(4, roundingMode);
    }
}
