package discordgateway.application;

import java.util.concurrent.CompletableFuture;

public class InProcessMusicCommandBus implements MusicCommandBus {

    private final MusicWorkerService musicWorkerService;
    private final MusicCommandMessageFactory musicCommandMessageFactory;

    public InProcessMusicCommandBus(
            MusicWorkerService musicWorkerService,
            MusicCommandMessageFactory musicCommandMessageFactory
    ) {
        this.musicWorkerService = musicWorkerService;
        this.musicCommandMessageFactory = musicCommandMessageFactory;
    }

    @Override
    public CompletableFuture<CommandResult> dispatch(MusicCommand command) {
        return musicWorkerService.handle(musicCommandMessageFactory.create(command));
    }
}
