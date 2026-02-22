using Microsoft.AspNetCore.Mvc;
using Caro.Logic;
using System.Collections.Generic;

public class BotController : Controller
{
    [HttpGet]
    public IActionResult Easy()
    {
        return View();
    }

    [HttpGet]
    public IActionResult Hard()
    {
        return View();
    }

    [HttpPost]
    public JsonResult EasyMove([FromBody] MoveModel move)
    {
        BotEasy.DatQuanNguoiChoi(move.X, move.Y);

        bool playerWin = BotEasy.KiemTraThang('X');
        if (playerWin)
        {
            return Json(new
            {
                x = (int?)null,
                y = (int?)null,
                playerWin = true,
                botWin = false
            });
        }

        var botMove = BotEasy.GetNextMove(move.X, move.Y);
        bool botWin = BotEasy.KiemTraThang('O');

        return Json(new
        {
            x = botMove.X,
            y = botMove.Y,
            playerWin = false,
            botWin = botWin
        });
    }

    [HttpPost]
    public JsonResult HardMove([FromBody] MoveModel move)
    {
        var botMove = BotHard.GetNextMove(move.X, move.Y);

        List<(int x, int y)> playerWinLine;
        List<(int x, int y)> botWinLine;

        bool playerWin = BotHard.KiemTraThang('X', out playerWinLine);
        bool botWin = BotHard.KiemTraThang('O', out botWinLine);

        return Json(new
        {
            x = botMove.X,
            y = botMove.Y,
            playerWin = playerWin,
            botWin = botWin,
            winLine = playerWin ? playerWinLine : (botWin ? botWinLine : null)
        });
    }

    [HttpPost]
    public JsonResult Reset()
    {
        BotEasy.ResetBoard();
        return Json(new { success = true });
    }

    [HttpPost]
    public IActionResult ResetHardBoard()
    {
        BotHard.ResetBoard();
        return Ok();
    }

    public class MoveModel
    {
        public int X { get; set; }
        public int Y { get; set; }
    }
}
