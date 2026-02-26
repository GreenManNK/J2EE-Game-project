package com.game.hub.games.chess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/chess")
public class ChessController {

    @GetMapping("/offline")
    public String offline(Model model) {
        applyPageModel(model, false);
        return "chess/offline";
    }

    @GetMapping("/bot")
    public String bot(@RequestParam(defaultValue = "easy") String difficulty, Model model) {
        applyPageModel(model, true, difficulty);
        return "chess/offline";
    }

    private void applyPageModel(Model model, boolean botEnabled) {
        applyPageModel(model, botEnabled, "easy");
    }

    private void applyPageModel(Model model, boolean botEnabled, String difficulty) {
        if (model == null) {
            return;
        }
        String botDifficulty = normalizeDifficulty(difficulty);
        boolean hardMode = "hard".equals(botDifficulty);
        model.addAttribute("botEnabled", botEnabled);
        model.addAttribute("botDifficulty", botDifficulty);
        model.addAttribute("pageTitle", botEnabled ? "Co vua Bot" : "Co vua Offline");
        model.addAttribute("pageHeading", botEnabled
            ? ("CO VUA VOI BOT (" + (hardMode ? "HARD" : "EASY") + ")")
            : "CO VUA OFFLINE (MVP)");
        model.addAttribute("pageSubtitle", botEnabled
            ? ("Ban danh quan Trang, bot " + (hardMode ? "hard" : "easy") + " danh quan Den.")
            : "2 nguoi choi tren cung thiet bi. Khong can dang nhap.");
        model.addAttribute("pageNote", botEnabled
            ? (hardMode
                ? "Bot hard uu tien nuoc di co loi (an quan, gay suc ep, giam nguy co mat quan). Ho tro check/checkmate/stalemate va phong cap tot thanh Hau."
                : "Bot easy chon nuoc di hop le ngau nhien (uu tien nuoc an). Ho tro check/checkmate/stalemate va phong cap tot thanh Hau.")
            : "Ho tro luot di hop le, check/checkmate/stalemate, phong cap tot thanh Hau.");
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
