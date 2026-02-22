using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Caro.Models;

namespace caro.Controllers
{
    
    public class GameController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;

        public GameController(UserManager<ApplicationUser> userManager)
        {
            _userManager = userManager;
        }
        [Authorize]
        public async Task<IActionResult> Index(string roomId, string symbol)
        {
            var user = await _userManager.GetUserAsync(User);

            ViewBag.RoomId = roomId;
            ViewBag.Symbol = symbol;
            ViewBag.AvatarPath = user.AvatarPath;
            ViewBag.DisplayName = user.DisplayName;
            ViewBag.UserId = user.Id;
            ViewBag.MyScore = user.Score; 

           
            ViewBag.OpponentId = "";
            ViewBag.OpponentScore = 0;
            ViewBag.OpponentDisplayName = "";
            ViewBag.OpponentAvatar = "";

            return View();
        }
        public IActionResult Offline()
        {
            return View();
        }
        public IActionResult Waiting(string requestId)
        {
            ViewData["RequestId"] = requestId;
            return View();
        }


    }
}
