using Caro.Data;
using Caro.Models;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Linq;

namespace Caro.Controllers
{
    public class HistoryController : Controller
    {
        private readonly ApplicationDbContext _context;
        private readonly UserManager<ApplicationUser> _userManager;

        public HistoryController(ApplicationDbContext context, UserManager<ApplicationUser> userManager)
        {
            _context = context;
            _userManager = userManager;
        }

        public async Task<IActionResult> Index()
        {
            var user = await _userManager.GetUserAsync(User);

            var histories = await _context.GameHistories
                .Where(g => g.Player1Id == user.Id || g.Player2Id == user.Id)
                .OrderByDescending(g => g.PlayedAt)
                .ToListAsync();

            var userIds = histories
                .SelectMany(h => new[] { h.Player1Id, h.Player2Id, h.FirstPlayerId, h.WinnerId })
                .Distinct()
                .ToList();

            var userDict = await _context.Users
                .Where(u => userIds.Contains(u.Id))
                .ToDictionaryAsync(u => u.Id, u => u.DisplayName);

            var viewModel = histories.Select(h => new GameHistoryViewModel
            {
                GameCode = h.GameCode,
                Player1Name = userDict.GetValueOrDefault(h.Player1Id),
                Player2Name = userDict.GetValueOrDefault(h.Player2Id),
                FirstPlayerName = userDict.GetValueOrDefault(h.FirstPlayerId),
                WinnerName = userDict.GetValueOrDefault(h.WinnerId),
                TotalMoves = h.TotalMoves,
                PlayedAt = h.PlayedAt,
                Result = h.WinnerId == user.Id ? "Thắng" : "Thua"
            }).ToList();

            return View(viewModel);
        }
    }

    public class GameHistoryViewModel
    {
        public string Result { get; set; }
        public string GameCode { get; set; }
        public string Player1Name { get; set; }
        public string Player2Name { get; set; }
        public string FirstPlayerName { get; set; }
        public int TotalMoves { get; set; }
        public string WinnerName { get; set; }
        public DateTime PlayedAt { get; set; }
    }
}
