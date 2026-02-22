using Caro.Models;
using System.Collections.Generic;

public class NotificationViewModel
{
    public List<Friendship> FriendRequests { get; set; } = new();
    public List<AchievementNotification> AchievementNotifications { get; set; } = new();
    public List<SystemNotification> SystemNotifications { get; set; } = new();

}
