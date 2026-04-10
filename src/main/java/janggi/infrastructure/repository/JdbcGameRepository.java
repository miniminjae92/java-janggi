package janggi.infrastructure.repository;

import janggi.domain.Game;
import janggi.domain.MoveEvent;
import janggi.domain.Side;
import janggi.domain.board.Board;
import janggi.domain.board.BoardFactory;
import janggi.domain.piece.Piece;
import janggi.domain.piece.PieceFactory;
import janggi.domain.piece.PieceType;
import janggi.domain.player.Name;
import janggi.domain.player.Player;
import janggi.domain.player.Players;
import janggi.domain.repository.GameRepository;
import janggi.domain.space.Position;
import janggi.domain.state.ChoTurn;
import janggi.domain.state.Finished;
import janggi.domain.state.GameState;
import janggi.domain.state.HanTurn;
import janggi.dto.GameDto;
import janggi.infrastructure.dao.GameDao;
import janggi.infrastructure.dao.MoveHistoryDao;
import janggi.infrastructure.dao.dto.GameEntity;
import janggi.infrastructure.dao.dto.MoveEntity;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcGameRepository implements GameRepository {
    private final GameDao gameDao;
    private final MoveHistoryDao moveHistoryDao;

    public JdbcGameRepository(GameDao gameDao, MoveHistoryDao moveHistoryDao) {
        this.gameDao = gameDao;
        this.moveHistoryDao = moveHistoryDao;
    }

    @Override
    public Long save(Game game) {
        try {
            Long gameId = gameDao.insertGame(
                    game.getPlayerNameBySide(Side.CHO).name(),
                    game.getPlayerNameBySide(Side.HAN).name(),
                    game.getPlayerFormationBySide(Side.CHO).name(),
                    game.getPlayerFormationBySide(Side.HAN).name(),
                    game.getCurrentSide().name()
            );
            saveUncommittedEvents(gameId, game);
            return gameId;
        } catch (SQLException e) {
            throw new RuntimeException("새 게임 저장 실패", e);
        }
    }

    @Override
    public void update(Long gameId, Game game) {
        try {
            gameDao.updateGame(gameId, game.getCurrentSide().name(), game.isPlaying());
            saveUncommittedEvents(gameId, game);
        } catch (SQLException e) {
            throw new RuntimeException("게임 업데이트 실패", e);
        }
    }

    private void saveUncommittedEvents(Long gameId, Game game) throws SQLException {
        List<MoveEvent> events = game.getUncommittedEvents();
        for (MoveEvent event : events) {
            moveHistoryDao.insertMove(gameId, event.source(), event.target());
        }
        game.clearEvents();
    }

    @Override
    public Optional<Game> findById(Long gameId) {
        return gameDao.findById(gameId).map(entity -> {
            Game game = Game.startNew(
                    BoardFactory.create(entity.choFormation(), entity.hanFormation()),
                    new Players(
                            new Player(new Name(entity.choPlayerName()), Side.CHO, entity.choFormation()),
                            new Player(new Name(entity.hanPlayerName()), Side.HAN, entity.hanFormation())
                    )
            );

            // 2. 💡 Replay: 이력을 순서대로 다시 실행하여 현재 상태로 복원
            List<MoveEntity> histories = moveHistoryDao.findAllByGameId(gameId);
            for (MoveEntity m : histories) {
                game.move(Position.of(m.sx(), m.sy()), Position.of(m.tx(), m.ty()));
            }
            game.clearEvents(); // 복원 중 쌓인 이벤트는 DB 저장 대상이 아니므로 삭제
            return game;
        });
    }

    @Override
    public List<GameDto> findAllGames() {
        return gameDao.findAll().stream()
                .map(entity -> new GameDto(
                        entity.id(),
                        entity.choPlayerName(),
                        entity.hanPlayerName(),
                        entity.currentTurn()))
                .toList();
    }
}
