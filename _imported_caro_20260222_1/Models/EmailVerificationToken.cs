using System;
using System.ComponentModel.DataAnnotations;

public class EmailVerificationToken
{
    [Key]
    public int Id { get; set; }

    [Required]
    public string Email { get; set; }

    [Required]
    public string Token { get; set; }

    public DateTime CreatedAt { get; set; }
    public DateTime ExpireAt { get; set; }
}
