using System;
using System.ComponentModel.DataAnnotations;

namespace Caro.Models
{
    public class Comment
    {
        public int Id { get; set; }

        public int PostId { get; set; }

        [Required]
        public string Content { get; set; } = null!;

        public string Author { get; set; } = null!;

        public DateTime CreatedAt { get; set; }

        public virtual Post Post { get; set; } = null!;
    }
}
