using Caro.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Caro.Controllers
{
    [Authorize]
    public class ChatController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;

        public ChatController(UserManager<ApplicationUser> userManager)
        {
            _userManager = userManager;
        }

        public async Task<IActionResult> Private(string friendId)
        {
            var currentUser = await _userManager.GetUserAsync(User);
            var friend = await _userManager.FindByIdAsync(friendId);

            if (friend == null || currentUser == null)
                return NotFound();

            ViewBag.CurrentUserId = currentUser.Id;
            ViewBag.CurrentUserName = currentUser.DisplayName;
            ViewBag.FriendId = friend.Id;
            ViewBag.FriendName = friend.DisplayName;

            return View();
        }
    }
}