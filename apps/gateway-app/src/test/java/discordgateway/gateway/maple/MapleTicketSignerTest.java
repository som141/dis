package discordgateway.gateway.maple;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapleTicketSignerTest {
    private static final String KEY="fixture-signing-key-12345678901234567890";
    private static final Clock NOW=Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void ticketIsBoundToDiscordUserExpiresAndRejectsTampering() {
        MapleTicketSigner signer=new MapleTicketSigner(KEY,NOW);
        String ticket=signer.issue("123456789012345678");
        assertThat(signer.verify(ticket)).isEqualTo("123456789012345678");
        assertThatThrownBy(()->signer.verify(ticket+"x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MapleTicketSigner(KEY,Clock.offset(NOW,Duration.ofMinutes(6))).verify(ticket))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MapleTicketSigner("different-key-123456789012345678901",NOW).verify(ticket))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
