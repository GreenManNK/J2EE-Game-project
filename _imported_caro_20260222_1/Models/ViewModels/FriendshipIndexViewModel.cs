using Caro.Models;

namespace Caro.ViewModels
{
    public class FriendshipIndexViewModel
    {
        public List<ApplicationUser> Friends { get; set; } = new();
        public List<Friendship> PendingRequests { get; set; } = new();
    }
}
