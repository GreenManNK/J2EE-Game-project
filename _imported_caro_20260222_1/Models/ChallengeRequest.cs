namespace Caro.Models
{
    public class ChallengeRequest
    {
        public int Id { get; set; }

        public string FromUserId { get; set; }
        public string ToUserId { get; set; }

        public string RoomId { get; set; }
        public DateTime SentAt { get; set; }

        public bool IsAccepted { get; set; }
        public bool IsHandled { get; set; } = false;
    }

}
