namespace Caro.Models.ViewModels
{
    public class ProfileViewModel
    {
        public string DisplayName { get; set; }
        public string Email { get; set; }
        public string AvatarPath { get; set; }
        public int Score { get; set; }

        public int TotalGames { get; set; }
        public int WinCount { get; set; }
        public double WinRate { get; set; }
        public int HighestScore { get; set; }
        public List<string> DanhHieu { get; set; }
        public int WinFastRanked { get; set; }
        public int BestStreak { get; set; }
        public List<string> Achievements { get; set; } = new();
        public List<string> DiemAchievements { get; set; } = new();
        public Dictionary<string, int> RepeatAchievements { get; set; } = new();
        public List<string> LockedAchievements { get; set; } = new();
        public bool IsFriend { get; set; }
        public bool HasPending { get; set; }
        public string ViewedUserId { get; set; }
        public bool IsOwner { get; set; }

    }

}
