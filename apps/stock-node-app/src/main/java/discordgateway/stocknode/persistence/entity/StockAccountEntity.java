package discordgateway.stocknode.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(
        name = "stock_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_account_guild_user",
                columnNames = {"guild_id", "user_id"}
        )
)
public class StockAccountEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private Long guildId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    protected StockAccountEntity() {
    }

    private StockAccountEntity(Long guildId, Long userId, BigDecimal cashBalance) {
        this.guildId = guildId;
        this.userId = userId;
        this.cashBalance = cashBalance;
    }

    public static StockAccountEntity create(long guildId, long userId) {
        return new StockAccountEntity(guildId, userId, BigDecimal.ZERO);
    }

    public void updateCashBalance(BigDecimal cashBalance) {
        this.cashBalance = scaleCash(cashBalance);
    }

    public void addCash(BigDecimal amount) {
        cashBalance = cashBalance.add(scaleCash(amount));
    }

    public void subtractCash(BigDecimal amount) {
        cashBalance = cashBalance.subtract(scaleCash(amount));
    }

    private BigDecimal scaleCash(BigDecimal cashBalance) {
        return Objects.requireNonNull(cashBalance, "cashBalance").setScale(4, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Long getGuildId() {
        return guildId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }
}
