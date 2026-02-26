package com.game.hub.games.xiangqi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/xiangqi")
public class XiangqiController {

    @GetMapping("/offline")
    public String offline(Model model) {
        applyPageModel(model, false, "easy");
        return "xiangqi/offline";
    }

    @GetMapping("/bot")
    public String bot(@RequestParam(defaultValue = "easy") String difficulty, Model model) {
        applyPageModel(model, true, difficulty);
        return "xiangqi/offline";
    }

    private void applyPageModel(Model model, boolean botEnabled, String difficulty) {
        if (model == null) {
            return;
        }
        String botDifficulty = normalizeDifficulty(difficulty);
        boolean hardMode = "hard".equals(botDifficulty);
        model.addAttribute("botEnabled", botEnabled);
        model.addAttribute("botDifficulty", botDifficulty);
        model.addAttribute("pageTitle", botEnabled ? "Co tuong Bot" : "Co tuong Offline");
        model.addAttribute("pageHeading", botEnabled
            ? ("CO TUONG VOI BOT (" + (hardMode ? "HARD" : "EASY") + ")")
            : "CO TUONG OFFLINE (MVP)");
        model.addAttribute("pageSubtitle", botEnabled
            ? ("Ban danh quan Do, bot " + (hardMode ? "hard" : "easy") + " danh quan Den.")
            : "2 nguoi choi tren cung thiet bi. Khong can dang nhap.");
        model.addAttribute("pageNote", botEnabled
            ? (hardMode
                ? "Bot hard uu tien nuoc an quan/gay chieu va giam nuoc di nguy hiem. MVP chua bao gom toan bo luat nang cao."
                : "Bot easy chon nuoc di hop le ngau nhien (uu tien an quan). MVP chua bao gom toan bo luat nang cao.")
            : "MVP ho tro luat di quan co ban, chieu/chieu bi va ket thuc van co.");
    }

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }
}
