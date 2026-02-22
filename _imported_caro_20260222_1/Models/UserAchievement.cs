namespace Caro.Models
{
    public class UserAchievement
    {
        public int Id { get; set; }
        public string UserId { get; set; }
        public ApplicationUser User { get; set; }

        public string AchievementName { get; set; }
        public DateTime AchievedAt { get; set; }
    }

}
