using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Caro.Models;
using System.Threading.Tasks;
using Caro.ViewModels;
using Microsoft.AspNetCore.Hosting;
using System.IO;
using System;
using Microsoft.EntityFrameworkCore;
using Caro.Data;
using Caro.Services;
using Microsoft.AspNetCore.Authorization;
namespace Caro.Controllers
{
    public class AccountController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly SignInManager<ApplicationUser> _signInManager;
        private readonly IWebHostEnvironment _webHostEnvironment;
        private readonly ApplicationDbContext _context;
        private readonly IEmailService _emailService;

        public AccountController(
        UserManager<ApplicationUser> userManager,
        SignInManager<ApplicationUser> signInManager,
        IWebHostEnvironment webHostEnvironment,
        ApplicationDbContext context,
        IEmailService emailService)
        {
            _userManager = userManager;
            _signInManager = signInManager;
            _webHostEnvironment = webHostEnvironment;
            _context = context;
            _emailService = emailService;
        }

        [HttpGet]
        public IActionResult Login(string returnUrl = null)
        {
            ViewData["ReturnUrl"] = returnUrl;
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> Login(LoginViewModel model, string returnUrl = null)
        {
            ViewData["ReturnUrl"] = returnUrl;
            await _signInManager.SignOutAsync();

            if (ModelState.IsValid)
            {
                var user = await _userManager.FindByEmailAsync(model.Email);
                if (user == null)
                {
                    ModelState.AddModelError(string.Empty, "Tài khoản không tồn tại.");
                    return View(model);
                }

                //Kiểm tra bị ban
                if (user.BannedUntil != null && user.BannedUntil > DateTime.Now)
                {
                    TempData["BanMessage"] = $"Tài khoản của bạn đã bị cấm đến {user.BannedUntil?.ToString("g")}. Bạn sẽ bị đăng xuất.";
                    return RedirectToAction("BanNotification");
                }

                var result = await _signInManager.PasswordSignInAsync(user.Email, model.Password, model.RememberMe, false);
                if (result.Succeeded)
                {
                    user.IsOnline = true;
                    await _userManager.UpdateAsync(user);

                    return RedirectToAction("Index", "Lobby");
                }

                ModelState.AddModelError(string.Empty, "Đăng nhập thất bại.");
            }

            return View(model);
        }



        [HttpGet]
        public IActionResult Register()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> Register(RegisterViewModel model)
        {
            if (ModelState.IsValid)
            {
                var existingUser = await _userManager.FindByEmailAsync(model.Email);
                if (existingUser != null)
                {
                    ModelState.AddModelError("Email", "Email này đã được đăng ký. Vui lòng dùng email khác.");
                    return View(model);
                }
                string avatarPath = null;

                // Lưu ảnh như cũ
                if (model.Avatar != null && model.Avatar.Length > 0)
                {
                    try
                    {
                        var uploadsFolder = Path.Combine(_webHostEnvironment.WebRootPath, "uploads/avatars");
                        Directory.CreateDirectory(uploadsFolder);
                        var uniqueFileName = Guid.NewGuid().ToString() + Path.GetExtension(model.Avatar.FileName);
                        var filePath = Path.Combine(uploadsFolder, uniqueFileName);
                        using (var stream = new FileStream(filePath, FileMode.Create))
                        {
                            await model.Avatar.CopyToAsync(stream);
                        }
                        avatarPath = "/uploads/avatars/" + uniqueFileName;
                    }
                    catch (Exception ex)
                    {
                        ModelState.AddModelError(string.Empty, $"Lỗi khi lưu ảnh đại diện: {ex.Message}");
                        return View(model);
                    }
                }
                else
                {
                    avatarPath = "/uploads/avatars/default-avatar.jpg";
                }

                //Lưu thông tin đăng ký vào TempData (hoặc Session nếu muốn an toàn hơn)
                TempData["PendingEmail"] = model.Email;
                TempData["PendingDisplayName"] = model.DisplayName;
                TempData["PendingPassword"] = model.Password;
                TempData["PendingAvatar"] = avatarPath;

                var token = new Random().Next(100000, 999999).ToString();
                _context.EmailVerificationTokens.Add(new EmailVerificationToken
                {
                    Email = model.Email,
                    Token = token,
                    CreatedAt = DateTime.UtcNow,
                    ExpireAt = DateTime.UtcNow.AddMinutes(5)
                });
                await _context.SaveChangesAsync();
                var subject = "Caro Game - Xác thực Email";
                var message = $@"
                <p>Xin chào <strong>{model.DisplayName}</strong>,</p>
                <p>Bạn vừa đăng ký tài khoản trên <strong>Caro Game</strong>.</p>
                <p><strong>Mã xác thực của bạn là:</strong> <span style='color:blue; font-size:18px;'>{token}</span></p>
                <p>Mã này có hiệu lực trong vòng <strong>5 phút</strong>. Vui lòng nhập mã để hoàn tất quá trình đăng ký.</p>
                <p>Nếu bạn không yêu cầu đăng ký tài khoản, vui lòng bỏ qua email này.</p>
                <hr />
                <p>Trân trọng,<br />Đội ngũ <strong>Caro Game</strong></p>";
                await _emailService.SendEmailAsync(model.Email, subject, message);


                return RedirectToAction("VerifyEmail", new { email = model.Email });
            }

            return View(model);
        }


        [HttpGet]
        public IActionResult VerifyEmail(string email)
        {
            ViewBag.Email = email;
            return View();
        }


        [HttpPost]
        public async Task<IActionResult> VerifyEmail(string email, string code)
        {
            var tokenEntity = await _context.EmailVerificationTokens
                .Where(t => t.Email == email && t.Token == code)
                .OrderByDescending(t => t.CreatedAt)
                .FirstOrDefaultAsync();

            if (tokenEntity == null || tokenEntity.ExpireAt < DateTime.UtcNow)
            {
                TempData["Error"] = "Mã không hợp lệ hoặc đã hết hạn.";
                return RedirectToAction("VerifyEmail", new { email });
            }

            // Lấy thông tin từ TempData
            var displayName = TempData["PendingDisplayName"]?.ToString();
            var password = TempData["PendingPassword"]?.ToString();
            var avatar = TempData["PendingAvatar"]?.ToString();

            if (string.IsNullOrEmpty(displayName) || string.IsNullOrEmpty(password))
            {
                TempData["Error"] = "Dữ liệu xác nhận đã hết hạn. Vui lòng đăng ký lại.";
                return RedirectToAction("Register");
            }

            var user = new ApplicationUser
            {
                UserName = email,
                Email = email,
                DisplayName = displayName,
                AvatarPath = avatar,
                EmailConfirmed = true
            };

            var result = await _userManager.CreateAsync(user, password);
            if (result.Succeeded)
            {
                await _signInManager.SignInAsync(user, false);
                return RedirectToAction("Index", "Home");
            }

            TempData["Error"] = "Tạo tài khoản thất bại.";
            return RedirectToAction("Register");
        }


        public IActionResult ChangePassword()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> ChangePassword(ChangePasswordViewModel model)
        {
            if (!ModelState.IsValid) return View(model);

            var user = await _userManager.GetUserAsync(User);
            if (user == null)
            {
                TempData["ErrorMessage"] = "Không tìm thấy người dùng";
                return RedirectToAction("Login");
            }

            var result = await _userManager.ChangePasswordAsync(user, model.CurrentPassword, model.NewPassword);

            if (result.Succeeded)
            {
                await _signInManager.RefreshSignInAsync(user);
                TempData["SuccessMessage"] = "Đổi mật khẩu thành công!";
                return View();
            }

            foreach (var error in result.Errors)
            {
                ModelState.AddModelError(string.Empty, error.Description);
            }

            TempData["ErrorMessage"] = "Đổi mật khẩu thất bại";
            return View(model);
        }

        [HttpPost]
        public async Task<IActionResult> Logout()
        {
            var user = await _userManager.GetUserAsync(User);
            if (user != null)
            {
                user.IsOnline = false;
                await _userManager.UpdateAsync(user);
            }
            await _signInManager.SignOutAsync();
            return RedirectToAction("Index", "Home");
        }
        public IActionResult AccessDenied()
        {
            return View();
        }


        //--------------------------------
        [HttpGet]
        public IActionResult RequestReset() => View();

        [HttpPost]
        public async Task<IActionResult> SendResetCode(string email)
        {
            var user = await _userManager.FindByEmailAsync(email);
            if (user == null)
            {
                TempData["Error"] = "Email không tồn tại.";
                return RedirectToAction("RequestReset");
            }

            var token = new Random().Next(100000, 999999).ToString();
            var tokenEntity = new PasswordResetToken
            {
                UserId = user.Id,
                Token = token,
                CreatedAt = DateTime.UtcNow,
                ExpireAt = DateTime.UtcNow.AddMinutes(5)
            };
            _context.PasswordResetTokens.Add(tokenEntity);
            await _context.SaveChangesAsync();

            var subject = "Caro Game - Xác thực đặt lại mật khẩu";
            var message = $@"
            <p>Xin chào <strong>{user.DisplayName}</strong>,</p>
            <p>Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản Caro của mình.</p>
            <p><strong>Mã xác nhận của bạn là:</strong> <span style='color:blue; font-size:18px;'>{token}</span></p>
            <p>Mã này có hiệu lực trong 5 phút.</p>
            <p>Nếu bạn không yêu cầu thao tác này, vui lòng bỏ qua email này.</p>
            <hr />
            <p>Trân trọng,<br />Đội ngũ Caro Game</p>";
            await _emailService.SendEmailAsync(user.Email, subject, message);

            // Chuyển sang bước xác minh
            return RedirectToAction("VerifyResetCode", new { userId = user.Id });
        }
        [HttpGet]
        public IActionResult VerifyResetCode(string userId)
        {
            ViewBag.UserId = userId;
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> VerifyResetCode(string userId, string code)
        {
            var token = await _context.PasswordResetTokens
                .Where(t => t.UserId == userId && t.Token == code)
                .OrderByDescending(t => t.CreatedAt)
                .FirstOrDefaultAsync();

            if (token == null || token.ExpireAt < DateTime.UtcNow)
            {
                TempData["Error"] = "Mã không hợp lệ hoặc đã hết hạn.";
                return RedirectToAction("VerifyResetCode", new { userId });
            }

            return RedirectToAction("ResetPassword", new { userId, code });
        }
        [HttpGet]
        public IActionResult ResetPassword(string userId, string code)
        {
            ViewBag.UserId = userId;
            ViewBag.Token = code;
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> ResetPassword(string userId, string code, string NewPassword, string ConfirmPassword)
        {
            if (NewPassword != ConfirmPassword)
            {
                TempData["Error"] = "Mật khẩu không khớp.";
                return RedirectToAction("ResetPassword", new { userId, code });
            }

            var user = await _userManager.FindByIdAsync(userId);
            if (user == null) return RedirectToAction("RequestReset");

            var resetToken = await _userManager.GeneratePasswordResetTokenAsync(user);
            var result = await _userManager.ResetPasswordAsync(user, resetToken, NewPassword);

            if (result.Succeeded)
            {
                _context.PasswordResetTokens.RemoveRange(
                    _context.PasswordResetTokens.Where(t => t.UserId == user.Id));
                await _context.SaveChangesAsync();
                TempData["SuccessMessage"] = "Success";
                return RedirectToAction("ResetPassword", new { userId, code });
            }

            foreach (var error in result.Errors)
            {
                ModelState.AddModelError(string.Empty, error.Description);
            }

            return View();
        }

        [HttpGet]
        public IActionResult BanNotification()
        {
            return View();
        }

    }
}
