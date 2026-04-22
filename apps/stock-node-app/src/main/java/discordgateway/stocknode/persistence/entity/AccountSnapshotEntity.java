package discordgateway.stocknode.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "account_snapshot")
public class AccountSnapshotEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private StockAccountEntity account;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "portfolio_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal portfolioValue;

    @Column(name = "total_equity", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalEquity;

    protected AccountSnapshotEntity() {
    }

    private AccountSnapshotEntity(
            StockAccountEntity account,
            Instant snapshotAt,
            BigDecimal cashBalance,
            BigDecimal portfolioValue,
            BigDecimal totalEquity
    ) {
        this.account = Objects.requireNonNull(account, "account");
        this.snapshotAt = Objects.requireNonNull(snapshotAt, "snapshotAt");
        this.cashBalance = Objects.requireNonNull(cashBalance, "cashBalance");
        this.portfolioValue = Objects.requireNonNull(portfolioValue, "portfolioValue");
        this.totalEquity = Objects.requireNonNull(totalEquity, "totalEquity");
    }

    public static AccountSnapshotEntity create(
            StockAccountEntity account,
            Instant snapshotAt,
            BigDecimal cashBalance,
            BigDecimal portfolioValue,
            BigDecimal totalEquity
    ) {
        return new AccountSnapshotEntity(account, snapshotAt, cashBalance, portfolioValue, totalEquity);
    }
}
