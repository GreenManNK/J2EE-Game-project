using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Identity;
using Caro.Models;
using Caro.Data;
using System.Threading.Tasks;
using System.IO;
using System.Linq;
using Caro.Models.ViewModels;
using System.Collections.Generic;

namespace Caro.Controllers
{
    public class ProfileController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly ApplicationDbContext _context;

        public ProfileController(UserManager<ApplicationUser> userManager, ApplicationDbContext context)
        {
            _userManager = userManager;
            _context = context;
        }

        public async Task<IActionResult> Index()
        {
            ViewBag.AchievementDescriptions = new Dictionary<string, string>
            {
                { "Bạc", "Đạt 100 điểm" },
                { "Vàng", "Đạt 300 điểm" },
                { "Kim cương", "Đạt 500 điểm" },
                { "Thách đấu", "Đạt 1000 điểm" },
                { "Đấu trí thắng Phật", "Thắng người có điểm gấp 3 lần" },
                { "Chiến thắng không tưởng", "Thắng người có điểm gấp 10 lần bạn" },
                { "Chuỗi bất bại", "Thắng liên tiếp 5 trận" },
                { "Không thể ngăn cản", "Chuỗi thắng dài nhất đạt được" },
                { "Hạ gục vua", "Thắng người đứng đầu bảng xếp hạng" },
                { "Chuỗi thua huyền thoại", "Thua liên tiếp 5 trận" },
                { "Kẻ cố chấp", "Chuỗi thua dài nhất đạt được" },
                { "Chơi vì đam mê", "Chơi 100 trận" },
                { "1 phút lơ là", "Thua người có điểm thấp hơn 3 lần" },
                { "Kẻ sát thần", "Thắng admin" },
                { "Thất bại xứng đáng", "Thua trận có trên 60 nước đi" },
                { "Chiến thắng khó khăn", "Thắng trận có trên 60 nước đi" }
            };
            var user = await _userManager.GetUserAsync(User);
            if (user == null) return RedirectToAction("Login", "Account");

            var allGames = _context.GameHistories
                .Where(g => g.Player1Id == user.Id || g.Player2Id == user.Id)
                .OrderBy(g => g.PlayedAt)
                .ToList();

            int totalGames = allGames.Count;
            int winCount = allGames.Count(g => g.WinnerId == user.Id);
            double winRate = totalGames > 0 ? Math.Round((double)winCount / totalGames * 100, 2) : 0;

            // ======= Danh hiệu ========
            var allUsers = _userManager.Users.OrderByDescending(u => u.Score).ToList();
            int rank = allUsers.FindIndex(u => u.Id == user.Id) + 1;

            List<string> danhHieu = new();
            if (rank == 1) danhHieu.Add("👑 Vua trò chơi");
            else if (rank <= 3) danhHieu.Add("Trùm server");
            else if (rank <= 5) danhHieu.Add("Thách đấu");

            // ======= Thành tích đặc biệt ========
            int winFastRanked = _context.GameHistories.Count(g =>
                g.WinnerId == user.Id &&
                g.TotalMoves < 15
            );

            int maxStreak = 0, currentStreak = 0;
            foreach (var g in allGames)
            {
                if (g.WinnerId == user.Id)
                {
                    currentStreak++;
                    maxStreak = Math.Max(maxStreak, currentStreak);
                }
                else currentStreak = 0;
            }

            // ======= Xử lý thành tựu ========
            var pointBased = new HashSet<string> { "Bạc", "Vàng", "Kim cương", "Thách đấu" };
            var allPossibleAchievements = new List<string>
            {
                "Bạc", "Vàng", "Kim cương", "Thách đấu",
                "Đấu trí thắng Phật", "Chiến thắng không tưởng", "Hạ gục vua", "Chuỗi bất bại",
                "Chuỗi thua huyền thoại", "Chơi vì đam mê", "1 phút lơ là",
                "Kẻ sát thần", "Thất bại xứng đáng", "Chiến thắng khó khăn",
                "Kẻ cố chấp", "Không thể ngăn cản"

            };

            var userAchievements = _context.UserAchievements
                .Where(a => a.UserId == user.Id)
                .ToList();

            // Thành tựu điểm – chỉ lấy duy nhất
            var diemAchievements = userAchievements
                .Where(a => pointBased.Contains(a.AchievementName))
                .Select(a => a.AchievementName)
                .Distinct()
                .ToList();

            // Thành tựu đặc biệt – đếm số lần
            var repeatAchievements = userAchievements
                .Where(a => !pointBased.Contains(a.AchievementName))
                .GroupBy(a => a.AchievementName)
                .ToDictionary(g => g.Key, g => g.Count());
            // Thành tựu đếm chuỗi dài nhất
            var freeAchievements = userAchievements
            .Where(a => a.AchievementName.StartsWith("Kẻ cố chấp") || a.AchievementName.StartsWith("Không thể ngăn cản"))
            .Select(a => a.AchievementName)
            .ToList();
            // Thành tựu chưa đạt
            var allUnlocked = new HashSet<string>(diemAchievements.Concat(repeatAchievements.Keys));
            var lockedAchievements = allPossibleAchievements
                .Where(a => !allUnlocked.Contains(a))
                .ToList();

            var model = new ProfileViewModel
            {
                DisplayName = user.DisplayName,
                Email = user.Email,
                AvatarPath = user.AvatarPath,
                Score = user.Score,
                TotalGames = totalGames,
                WinCount = winCount,
                WinRate = winRate,
                HighestScore = user.HighestScore,
                DanhHieu = danhHieu,
                WinFastRanked = winFastRanked,
                BestStreak = maxStreak,
                DiemAchievements = diemAchievements,
                RepeatAchievements = repeatAchievements,
                Achievements = freeAchievements,
                LockedAchievements = lockedAchievements,
                IsOwner = true
            };

            return View(model);
        }

        [HttpGet]
        public async Task<IActionResult> Edit()
        {
            var user = await _userManager.GetUserAsync(User);
            if (user == null) return RedirectToAction("Login", "Account");
            return View(user);
        }

        [HttpPost]
        public async Task<IActionResult> Edit(ApplicationUser model, IFormFile? avatar)
        {
            var user = await _userManager.GetUserAsync(User);
            if (user == null) return RedirectToAction("Login", "Account");

            user.DisplayName = model.DisplayName;
            user.Email = model.Email;

            if (avatar != null && avatar.Length > 0)
            {
                var folderPath = Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "avatars");
                Directory.CreateDirectory(folderPath);

                var fileName = $"{Guid.NewGuid()}{Path.GetExtension(avatar.FileName)}";
                var filePath = Path.Combine(folderPath, fileName);

                using (var stream = new FileStream(filePath, FileMode.Create))
                {
                    await avatar.CopyToAsync(stream);
                }

                user.AvatarPath = $"/avatars/{fileName}";
            }

            await _userManager.UpdateAsync(user);
            return RedirectToAction("Index");
        }
    }
}
