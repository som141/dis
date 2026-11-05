package discordgateway.gateway.maple;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MapleNotificationScheduler {
    private static final Logger log=LoggerFactory.getLogger(MapleNotificationScheduler.class);
    private final MapleDiscordProperties settings;
    private final MapleDiscordService service;
    private final JDA jda;
    private final Set<String> inFlight=ConcurrentHashMap.newKeySet();

    public MapleNotificationScheduler(MapleDiscordProperties settings,MapleDiscordService service,JDA jda){
        this.settings=settings;this.service=service;this.jda=jda;
    }

    @Scheduled(fixedDelayString="${maple.discord.notification-poll-ms:30000}")
    public void poll(){
        if(!settings.isEnabled())return;
        try{
            for(MapleDiscordService.Notice notice:service.pendingNotices()){
                if(!inFlight.add(notice.id()))continue;
                jda.retrieveUserById(notice.discordUserId()).queue(user ->
                        user.openPrivateChannel().queue(channel ->
                                channel.sendMessage(service.noticeMessage(notice)).queue(
                                        ignored->ack(notice.id(),true),failure->ack(notice.id(),false)),
                                failure->ack(notice.id(),false)),
                        failure->ack(notice.id(),false));
            }
        }catch(Exception error){log.warn("Maple notification poll failed");}
    }

    private void ack(String id,boolean delivered){
        CompletableFuture.runAsync(()->{
            try{service.acknowledgeNotice(id,delivered);}
            catch(Exception error){log.warn("Maple notification acknowledgement failed");}
            finally{inFlight.remove(id);}
        });
    }
}
