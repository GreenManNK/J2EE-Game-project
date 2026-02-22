using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;

namespace Caro.Models
{
    public class Post
    {
        public int Id { get; set; }

        [Required]
        public string Content { get; set; } = null!;

        public string Author { get; set; } = null!;

        public DateTime CreatedAt { get; set; }
        public string? ImagePath { get; set; }

        public virtual ICollection<Comment> Comments { get; set; } = new List<Comment>();
    }
}
