using Microsoft.AspNetCore.Http;
using System.ComponentModel.DataAnnotations;

public class AdminCreateUserViewModel
{
    [Required]
    [EmailAddress]
    public string Email { get; set; }

    [Required]
    public string DisplayName { get; set; }

    [Required]
    [DataType(DataType.Password)]
    public string Password { get; set; }

    public IFormFile? Avatar { get; set; }

    [Required]
    public int Score { get; set; } = 50;

    [Required]
    public string Role { get; set; }  
}
