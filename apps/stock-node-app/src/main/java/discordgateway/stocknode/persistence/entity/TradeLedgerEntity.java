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
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "trade_ledger")
public class TradeLedgerEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private StockAccountEntity account;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "side", nullable = false, length = 16)
    private String side;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TradeLedgerEntity() {
    }

    private TradeLedgerEntity(
            StockAccountEntity account,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Instant occurredAt
    ) {
        this.account = Objects.requireNonNull(account, "account");
        this.symbol = Objects.requireNonNull(symbol, "symbol").trim().toUpperCase(Locale.ROOT);
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static TradeLedgerEntity create(
            StockAccountEntity account,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Instant occurredAt
    ) {
        return new TradeLedgerEntity(account, symbol, side, quantity, unitPrice, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public StockAccountEntity getAccount() {
        return account;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
