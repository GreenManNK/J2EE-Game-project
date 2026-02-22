using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Caro.Models
{
    public class AchievementNotification
    {
        public int Id { get; set; }

        [Required]
        public string UserId { get; set; }

        [Required]
        public string AchievementName { get; set; }

        public DateTime CreatedAt { get; set; } = DateTime.Now;

        public bool IsRead { get; set; } = false;

        [ForeignKey("UserId")]
        public ApplicationUser User { get; set; }

    }
}
