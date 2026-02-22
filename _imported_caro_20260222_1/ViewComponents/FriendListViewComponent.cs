using Caro.Models;
using Caro.Services;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Caro.ViewComponents
{
    public class FriendListViewComponent : ViewComponent
    {
        private readonly FriendshipService _friendshipService;
        private readonly UserManager<ApplicationUser> _userManager;

        public FriendListViewComponent(FriendshipService friendshipService, UserManager<ApplicationUser> userManager)
        {
            _friendshipService = friendshipService;
            _userManager = userManager;
        }

        public async Task<IViewComponentResult> InvokeAsync()
        {
            var user = await _userManager.GetUserAsync(HttpContext.User);
            if (user == null)
                return View(new List<ApplicationUser>());

            var friends = await _friendshipService.GetFriends(user.Id);
            return View(friends);
        }
    }
}
