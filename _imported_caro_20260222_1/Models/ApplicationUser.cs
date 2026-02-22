using Microsoft.AspNetCore.Identity;
namespace Caro.Models
{
    public class ApplicationUser : IdentityUser
    {
        public string DisplayName { get; set; }
        public string? AvatarPath { get; set; }

        public int Score { get; set; } = 50;
        public int HighestScore { get; set; }
        public bool IsOnline { get; set; } = false;
        public DateTime? BannedUntil { get; set; }
        public bool IsBanned => BannedUntil.HasValue && BannedUntil > DateTime.Now;
        public DateTime LastSystemNotificationSeenAt { get; set; } = DateTime.MinValue;

    }
}