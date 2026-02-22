using Caro.Data;
using Caro.Models;
using Caro.Models.ViewModels;
using Caro.Services;
using Caro.ViewModels;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Caro.Controllers
{
    [Authorize]
    public class FriendshipController : Controller
    {
        private readonly FriendshipService _friendshipService;
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly ApplicationDbContext _context;

        public FriendshipController(FriendshipService friendshipService, UserManager<ApplicationUser> userManager, ApplicationDbContext context)
        {
            _friendshipService = friendshipService;
            _userManager = userManager;
            _context = context;
        }

        // GET: /Friendship
        [Authorize(Roles = "Admin, Manager")]
        public async Task<IActionResult> Index()
        {
            var currentUser = await _userManager.GetUserAsync(User);
            var friends = await _friendshipService.GetFriends(currentUser.Id);
            var pendingRequests = await _friendshipService.GetPendingRequests(currentUser.Id);

            var viewModel = new FriendshipIndexViewModel
            {
                Friends = friends,
                PendingRequests = pendingRequests
            };

            return View(viewModel);
        }

        // POST: /Friendship/SendRequest (từ thanh tìm kiếm)
        [HttpPost]
        public async Task<IActionResult> SendRequest(string email)
        {
            var currentUser = await _userManager.GetUserAsync(User);
            if (string.IsNullOrEmpty(email)) return RedirectToAction("Index");

            var targetUser = await _userManager.FindByEmailAsync(email);
            if (targetUser != null)
            {
                await _friendshipService.SendRequest(currentUser.Id, targetUser.Id);
            }

            return RedirectToAction("Index");
        }

        // POST: /Friendship/SendRequestById (từ trang chi tiết người dùng)
        [HttpPost]
        public async Task<IActionResult> SendRequestById(string addresseeId)
        {
            var currentUser = await _userManager.GetUserAsync(User);

            if (string.IsNullOrEmpty(addresseeId) || currentUser == null)
                return RedirectToAction("Index");

            await _friendshipService.SendRequest(currentUser.Id, addresseeId);
            return RedirectToAction("UserDetail", new { id = addresseeId });
        }

        // POST: /Friendship/Accept
        [HttpPost]
        public async Task<IActionResult> Accept(int id)
        {
            await _friendshipService.AcceptRequest(id);
            return RedirectToAction("Index", "Home");
        }
        [HttpPost]
        public async Task<IActionResult> Decline(int id)
        {
            var friendship = await _context.Friendships.FindAsync(id);
            if (friendship == null || friendship.IsAccepted)
            {
                return NotFound();
            }

            _context.Friendships.Remove(friendship);
            await _context.SaveChangesAsync();

            return RedirectToAction("Notifications");
        }

        // POST: /Friendship/Remove
        [HttpPost]
        public async Task<IActionResult> Remove(string id)
        {
            var currentUser = await _userManager.GetUserAsync(User);
            await _friendshipService.RemoveFriendship(currentUser.Id, id);
            return RedirectToAction("Index", "Home");
        }

        // GET: /Friendship/Search?query=...
        public async Task<IActionResult> Search(string query)
        {
            if (string.IsNullOrWhiteSpace(query))
                return View("SearchResults", new List<ApplicationUser>());

            var normalizedQuery = query.ToLower();

            var allUsers = await _userManager.Users.ToListAsync();

            // Tìm khớp chính xác
            var exactMatches = allUsers
                .Where(u => u.DisplayName.ToLower() == normalizedQuery
                         || u.Email.ToLower() == normalizedQuery)
                .ToList();

            ViewBag.Query = query;

            if (exactMatches.Any())
            {
                ViewBag.ExactMatches = exactMatches;
                var exactIds = exactMatches.Select(u => u.Id).ToList();

                // Gợi ý gần giống (tránh trùng khớp chính xác)
                var similarMatches = allUsers
                    .Where(u =>
                        (u.DisplayName.ToLower().Contains(normalizedQuery)
                      || u.Email.ToLower().Contains(normalizedQuery))
                        && !exactIds.Contains(u.Id))
                    .ToList();

                return View("SearchResults", similarMatches);
            }
            else
            {
                // Trường hợp KHÔNG có ai khớp chính xác → tìm ai có ít nhất 5 ký tự trùng
                var similarMatches = allUsers
                    .Where(u =>
                    {
                        var name = u.DisplayName?.ToLower() ?? "";
                        var email = u.Email?.ToLower() ?? "";
                        int matchCount = GetLongestCommonSubstringLength(name, normalizedQuery);
                        matchCount = Math.Max(matchCount, GetLongestCommonSubstringLength(email, normalizedQuery));
                        return matchCount >= 5;
                    })
                    .ToList();

                ViewBag.ExactMatches = new List<ApplicationUser>(); // Không có kết quả khớp hoàn toàn
                return View("SearchResults", similarMatches);
            }
        }


        // GET: /Friendship/UserDetail/{id}
        public async Task<IActionResult> UserDetail(string id)
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
            var currentUser = await _userManager.GetUserAsync(User);
            var user = await _userManager.FindByIdAsync(id);
            if (user == null) return NotFound();

            var allGames = _context.GameHistories
                .Where(g => g.Player1Id == user.Id || g.Player2Id == user.Id)
                .OrderBy(g => g.PlayedAt)
                .ToList();

            int totalGames = allGames.Count;
            int winCount = allGames.Count(g => g.WinnerId == user.Id);
            double winRate = totalGames > 0 ? Math.Round((double)winCount / totalGames * 100, 2) : 0;

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

            var allUsers = _userManager.Users.OrderByDescending(u => u.Score).ToList();
            int rank = allUsers.FindIndex(u => u.Id == user.Id) + 1;

            List<string> danhHieu = new();
            if (rank == 1) danhHieu.Add("👑 Vua trò chơi");
            else if (rank <= 3) danhHieu.Add("Trùm server");
            else if (rank <= 5) danhHieu.Add("Thách đấu");

            var pointBased = new HashSet<string> { "Bạc", "Vàng", "Kim cương", "Thách đấu" };
            var allPossibleAchievements = new List<string>
            {
                "Bạc", "Vàng", "Kim cương", "Thách đấu",
                "Đấu trí thắng Phật", "Chiến thắng không tưởng", "Chuỗi bất bại", "Hạ gục vua",
                "Chuỗi thua huyền thoại", "Chơi vì đam mê", "1 phút lơ là",
                "Kẻ sát thần", "Thất bại xứng đáng", "Chiến thắng khó khăn",
                "Kẻ cố chấp","Không thể ngăn cản"
            };

            var allAchievements = _context.UserAchievements
                .Where(a => a.UserId == user.Id)
                .ToList();

            var diemAchievements = allAchievements
                .Where(a => pointBased.Contains(a.AchievementName))
                .Select(a => a.AchievementName)
                .Distinct()
                .ToList();

            var repeatAchievements = allAchievements
                .Where(a => !pointBased.Contains(a.AchievementName))
                .GroupBy(a => a.AchievementName)
                .ToDictionary(g => g.Key, g => g.Count());

            var freeAchievements = allAchievements
                .Where(a => a.AchievementName.StartsWith("Kẻ cố chấp") || a.AchievementName.StartsWith("Không thể ngăn cản"))
                .Select(a => a.AchievementName)
                .ToList();
            // Thành tựu chưa đạt
            var achieved = diemAchievements.Concat(repeatAchievements.Keys).ToHashSet();
            var locked = allPossibleAchievements
                .Where(a => !achieved.Contains(a))
                .ToList();

            var isFriend = _context.Friendships.Any(f =>
                (f.RequesterId == currentUser.Id && f.AddresseeId == user.Id ||
                 f.RequesterId == user.Id && f.AddresseeId == currentUser.Id) &&
                f.IsAccepted);

            var hasPending = _context.Friendships.Any(f =>
                (f.RequesterId == currentUser.Id && f.AddresseeId == user.Id ||
                 f.RequesterId == user.Id && f.AddresseeId == currentUser.Id) &&
                !f.IsAccepted);

            var model = new ProfileViewModel
            {
                DisplayName = user.DisplayName,
                Email = user.Email,
                AvatarPath = user.AvatarPath,
                Score = user.Score,
                HighestScore = user.HighestScore,
                TotalGames = totalGames,
                WinCount = winCount,
                WinRate = winRate,
                IsFriend = isFriend,
                HasPending = hasPending,
                ViewedUserId = user.Id,
                IsOwner = currentUser.Id == user.Id,
                DanhHieu = danhHieu,
                WinFastRanked = winFastRanked,
                BestStreak = maxStreak,
                DiemAchievements = diemAchievements,
                RepeatAchievements = repeatAchievements,
                Achievements = freeAchievements,
                LockedAchievements = locked
            };

            return View("~/Views/Profile/Index.cshtml", model);
        }



        public async Task<IActionResult> FriendList()
        {
            var currentUser = await _userManager.GetUserAsync(User);
            var friends = await _friendshipService.GetFriends(currentUser.Id);
            return ViewComponent("FriendList", new { friends });
        }

        // GET: /Friendship/Notifications
        public async Task<IActionResult> Notifications()
        {
            var currentUser = await _userManager.GetUserAsync(User);

            //Lấy lời mời kết bạn đang chờ
            var pendingRequests = await _friendshipService.GetPendingRequests(currentUser.Id);

            // Lấy danh sách thành tựu chưa đọc
            var newAchievements = _context.AchievementNotifications
                .Where(a => a.UserId == currentUser.Id && !a.IsRead)
                .ToList();

            //Đánh dấu thành tựu là đã đọc
            foreach (var noti in newAchievements)
            {
                noti.IsRead = true;
            }

            //Lấy 5 thông báo hệ thống mới nhất
            var systemNotis = _context.SystemNotifications
                .OrderByDescending(n => n.CreatedAt)
                .Take(5)
                .ToList();

            //Cập nhật thời gian đã xem thông báo hệ thống
            currentUser.LastSystemNotificationSeenAt = DateTime.Now;
            await _userManager.UpdateAsync(currentUser);

            //Lưu thay đổi (cả Achievement & User)
            await _context.SaveChangesAsync();

            //Lấy danh sách thành tựu đã đọc để hiển thị
            var achievementNotis = _context.AchievementNotifications
                .Where(a => a.UserId == currentUser.Id)
                .OrderByDescending(a => a.CreatedAt)
                .ToList();

            //Gán ViewModel
            var viewModel = new NotificationViewModel
            {
                FriendRequests = pendingRequests,
                AchievementNotifications = achievementNotis,
                SystemNotifications = systemNotis
            };

            return View(viewModel);
        }




        private int GetLongestCommonSubstringLength(string source, string target)
        {
            int[,] table = new int[source.Length + 1, target.Length + 1];
            int maxLength = 0;

            for (int i = 1; i <= source.Length; i++)
            {
                for (int j = 1; j <= target.Length; j++)
                {
                    if (source[i - 1] == target[j - 1])
                    {
                        table[i, j] = table[i - 1, j - 1] + 1;
                        maxLength = Math.Max(maxLength, table[i, j]);
                    }
                }
            }

            return maxLength;
        }

    }
}
