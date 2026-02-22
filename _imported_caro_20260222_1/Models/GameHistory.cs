namespace Caro.Models
{
    public class GameHistory
    {
        public int Id { get; set; }
        public string GameCode { get; set; }
        public string Player1Id { get; set; }
        public string Player2Id { get; set; }
        public string FirstPlayerId { get; set; }
        public int TotalMoves { get; set; }
        public string WinnerId { get; set; }

        public DateTime PlayedAt { get; set; } = DateTime.Now;
    }
}
