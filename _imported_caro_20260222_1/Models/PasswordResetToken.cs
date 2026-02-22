using System;

namespace Caro.Models
{
    public class PasswordResetToken
    {
        public int Id { get; set; }
        public string UserId { get; set; }
        public string Token { get; set; }
        public DateTime CreatedAt { get; set; }
        public DateTime ExpireAt { get; set; }

        public ApplicationUser User { get; set; }
    }
}
