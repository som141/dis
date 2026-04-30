package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyAllowanceServiceTest {

    @Mock
    private StockAccountApplicationService stockAccountApplicationService;

    @Mock
    private StockAccountRepository stockAccountRepository;

    @Mock
    private AllowanceLedgerRepository allowanceLedgerRepository;

    @Mock
    private RankingCacheRepository rankingCacheRepository;

    @Test
    void grantsAllowanceOncePerDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-30T00:10:00Z"));
        StockAccountEntity account = account(1L, new BigDecimal("0"));
        when(stockAccountApplicationService.ensureAccountEntity(1001L, 2002L)).thenReturn(account);
        when(allowanceLedgerRepository.existsByAccountIdAndAllowanceTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                any(), any(), any(), any()
        )).thenReturn(false, true);

        DailyAllowanceService service = new DailyAllowanceService(
                stockAccountApplicationService,
                stockAccountRepository,
                allowanceLedgerRepository,
                rankingCacheRepository,
                clock
        );

        service.ensureSettledAccount(1001L, 2002L);
        service.ensureSettledAccount(1001L, 2002L);

        assertThat(account.getCashBalance()).isEqualByComparingTo("10000.0000");
        verify(stockAccountRepository, times(1)).save(account);
        verify(allowanceLedgerRepository, times(1)).save(any());
        verify(rankingCacheRepository, times(1)).evictGuild(1001L);
    }

    @Test
    void grantsAllowanceAgainOnNextDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-30T00:10:00Z"));
        StockAccountEntity account = account(1L, new BigDecimal("0"));
        when(stockAccountApplicationService.ensureAccountEntity(1001L, 2002L)).thenReturn(account);
        when(allowanceLedgerRepository.existsByAccountIdAndAllowanceTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                any(), any(), any(), any()
        )).thenReturn(false, false);

        DailyAllowanceService service = new DailyAllowanceService(
                stockAccountApplicationService,
                stockAccountRepository,
                allowanceLedgerRepository,
                rankingCacheRepository,
                clock
        );

        service.ensureSettledAccount(1001L, 2002L);
        clock.advance(Duration.ofDays(1));
        service.ensureSettledAccount(1001L, 2002L);

        assertThat(account.getCashBalance()).isEqualByComparingTo("20000.0000");
        verify(stockAccountRepository, times(2)).save(account);
        verify(allowanceLedgerRepository, times(2)).save(any());
        verify(rankingCacheRepository, times(2)).evictGuild(1001L);
    }

    private static StockAccountEntity account(Long id, BigDecimal cashBalance) {
        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        ReflectionTestUtils.setField(account, "id", id);
        account.updateCashBalance(cashBalance);
        return account;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
