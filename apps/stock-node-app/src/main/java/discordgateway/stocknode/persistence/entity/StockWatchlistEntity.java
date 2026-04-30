package discordgateway.stocknode.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(
        name = "stock_watchlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_watchlist_market_symbol",
                columnNames = {"market", "symbol"}
        )
)
public class StockWatchlistEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market", nullable = false, length = 20)
    private String market;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected StockWatchlistEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getMarket() {
        return market;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String normalizedMarket() {
        return Objects.requireNonNull(market, "market").trim().toUpperCase(Locale.ROOT);
    }

    public String normalizedSymbol() {
        return Objects.requireNonNull(symbol, "symbol").trim().toUpperCase(Locale.ROOT);
    }
}
