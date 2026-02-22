using Microsoft.AspNetCore.Http;
using System.ComponentModel.DataAnnotations;

public class AdminEditUserViewModel
{
    public string Id { get; set; }

    [Required]
    [EmailAddress]
    public string Email { get; set; }

    [Required]
    public string DisplayName { get; set; }

    public int Score { get; set; }

    public IFormFile? Avatar { get; set; }

    public string? ExistingAvatarPath { get; set; }

    [Required]
    public string Role { get; set; }
    public DateTime? BannedUntil { get; set; }

}
