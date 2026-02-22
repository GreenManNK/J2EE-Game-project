using Microsoft.AspNetCore.Mvc;
using Caro.Data;
using System.Linq;

namespace Caro.Controllers
{
    public class LeaderboardController : Controller
    {
        private readonly ApplicationDbContext _context;

        public LeaderboardController(ApplicationDbContext context)
        {
            _context = context;
        }

        public IActionResult Index()
        {
            var players = _context.Users
                .OrderByDescending(u => u.Score)
                .Select(u => new
                {
                    u.DisplayName,
                    u.Score,
                    u.AvatarPath
                })
                .ToList();

            return View(players);
        }
    }
}
