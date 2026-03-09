package com.game.hub.controller;

import com.game.hub.games.cards.blackjack.controller.BlackjackController;
import com.game.hub.games.cards.tienlen.controller.TienLenController;
import com.game.hub.games.caro.controller.GameController;
import com.game.hub.games.chess.controller.ChessOnlineController;
import com.game.hub.games.quiz.controller.QuizController;
import com.game.hub.games.typing.controller.TypingController;
import com.game.hub.games.xiangqi.controller.XiangqiOnlineController;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DedicatedRoomRedirectControllerTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

    @Test
    void caroQueryRoomShouldRedirectToDedicatedRoomPage() {
        GameController controller = new GameController(userAccountRepository);

        String view = controller.index("ROOM-1", "X", mock(HttpServletRequest.class), new ConcurrentModel());

        assertEquals("redirect:/game/room/ROOM-1?symbol=X", view);
    }

    @Test
    void chessQueryRoomShouldRedirectToSpectatePageWhenRequested() {
        ChessOnlineController controller = new ChessOnlineController(userAccountRepository);

        String view = controller.online("CHESS-1", true, mock(HttpServletRequest.class), new ConcurrentModel());

        assertEquals("redirect:/chess/online/room/CHESS-1/spectate", view);
    }

    @Test
    void xiangqiQueryRoomShouldRedirectToDedicatedRoomPage() {
        XiangqiOnlineController controller = new XiangqiOnlineController(userAccountRepository);

        String view = controller.online("XQ-1", false, mock(HttpServletRequest.class), new ConcurrentModel());

        assertEquals("redirect:/xiangqi/online/room/XQ-1", view);
    }

    @Test
    void tienLenQueryRoomShouldRedirectToDedicatedRoomPage() {
        TienLenController controller = new TienLenController(userAccountRepository);

        String view = controller.tienLen("TL-1", mock(HttpServletRequest.class), new ConcurrentModel());

        assertEquals("redirect:/cards/tien-len/room/TL-1", view);
    }

    @Test
    void typingQueryRoomShouldRedirectToDedicatedRoomPage() {
        TypingController controller = new TypingController();

        String view = controller.typingPage("TYP-1");

        assertEquals("redirect:/games/typing/room/TYP-1", view);
    }

    @Test
    void quizSpectateQueryShouldRedirectToDedicatedSpectatePage() {
        QuizController controller = new QuizController();

        String view = controller.quizPage("QUIZ-1", "spectate");

        assertEquals("redirect:/games/quiz/room/QUIZ-1/spectate", view);
    }

    @Test
    void blackjackSpectateQueryShouldRedirectToDedicatedSpectatePage() {
        BlackjackController controller = new BlackjackController();

        String view = controller.blackjackPage("BJ-1", "spectate");

        assertEquals("redirect:/games/cards/blackjack/room/BJ-1/spectate", view);
    }
}
