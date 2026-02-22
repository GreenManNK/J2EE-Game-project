using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Caro.Models;
using System.IO;
using Microsoft.EntityFrameworkCore;
using Caro.Data;
using OfficeOpenXml;
using OfficeOpenXml.Style;
using System.IO;
namespace Caro.Controllers
{
    [Authorize(Roles = "Admin")]
    public class AdminController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly IWebHostEnvironment _webHostEnvironment;
        private readonly RoleManager<IdentityRole> _roleManager;
        private readonly ApplicationDbContext _context;
        public AdminController(
            UserManager<ApplicationUser> userManager,
            IWebHostEnvironment webHostEnvironment,
            RoleManager<IdentityRole> roleManager,
            ApplicationDbContext context)
        {
            _userManager = userManager;
            _webHostEnvironment = webHostEnvironment;
            _roleManager = roleManager;
            _context = context;
        }
        public async Task<IActionResult> Index(string searchTerm, string banFilter)
        {
            var users = _userManager.Users.ToList();

            if (!string.IsNullOrEmpty(searchTerm))
            {
                users = users.Where(u =>
                    (!string.IsNullOrEmpty(u.DisplayName) && u.DisplayName.Contains(searchTerm, StringComparison.OrdinalIgnoreCase)) ||
                    (!string.IsNullOrEmpty(u.Email) && u.Email.Contains(searchTerm, StringComparison.OrdinalIgnoreCase))
                ).ToList();
            }

            if (!string.IsNullOrEmpty(banFilter))
            {
                if (banFilter == "banned")
                {
                    users = users.Where(u => u.BannedUntil != null && u.BannedUntil > DateTime.Now).ToList();
                }
                else if (banFilter == "active")
                {
                    users = users.Where(u => u.BannedUntil == null || u.BannedUntil <= DateTime.Now).ToList();
                }
            }

            var userRoles = new Dictionary<string, string>();
            foreach (var user in users)
            {
                var roles = await _userManager.GetRolesAsync(user);
                userRoles[user.Id] = roles.FirstOrDefault() ?? "User";
            }

            ViewBag.UserRoles = userRoles;
            ViewBag.SearchTerm = searchTerm;
            ViewBag.BanFilter = banFilter;

            return View(users);
        }


        public IActionResult Create()
        {
            return View();
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Create(AdminCreateUserViewModel model)
        {
            if (ModelState.IsValid)
            {
                string avatarPath = "/uploads/avatars/default-avatar.jpg";

                if (model.Avatar != null && model.Avatar.Length > 0)
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

                var user = new ApplicationUser
                {
                    UserName = model.Email,
                    Email = model.Email,
                    DisplayName = model.DisplayName,
                    Score = model.Score,
                    AvatarPath = avatarPath
                };

                var result = await _userManager.CreateAsync(user, model.Password);
                if (result.Succeeded)
                {
                    if (!await _roleManager.RoleExistsAsync(model.Role))
                    {
                        await _roleManager.CreateAsync(new IdentityRole(model.Role));
                    }

                    await _userManager.AddToRoleAsync(user, model.Role);
                    return RedirectToAction(nameof(Index));
                }

                foreach (var error in result.Errors)
                {
                    ModelState.AddModelError("", error.Description);
                }
            }

            return View(model);
        }

        public async Task<IActionResult> Edit(string id)
        {
            var user = await _userManager.FindByIdAsync(id);
            if (user == null) return NotFound();

            var roles = await _userManager.GetRolesAsync(user);

            var model = new AdminEditUserViewModel
            {
                Id = user.Id,
                Email = user.Email,
                DisplayName = user.DisplayName,
                Score = user.Score,
                ExistingAvatarPath = user.AvatarPath,
                Role = roles.FirstOrDefault() ?? "User",
                BannedUntil = user.BannedUntil
            };

            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(AdminEditUserViewModel model)
        {
            var user = await _userManager.FindByIdAsync(model.Id);
            if (user == null) return NotFound();

            user.DisplayName = model.DisplayName;
            user.Score = model.Score;

            if (model.Avatar != null && model.Avatar.Length > 0)
            {
                var uploadsFolder = Path.Combine(_webHostEnvironment.WebRootPath, "uploads/avatars");
                Directory.CreateDirectory(uploadsFolder);
                var uniqueFileName = Guid.NewGuid().ToString() + Path.GetExtension(model.Avatar.FileName);
                var filePath = Path.Combine(uploadsFolder, uniqueFileName);

                using (var stream = new FileStream(filePath, FileMode.Create))
                {
                    await model.Avatar.CopyToAsync(stream);
                }

                user.AvatarPath = "/uploads/avatars/" + uniqueFileName;
            }

            var currentRoles = await _userManager.GetRolesAsync(user);
            await _userManager.RemoveFromRolesAsync(user, currentRoles);

            if (!await _roleManager.RoleExistsAsync(model.Role))
            {
                await _roleManager.CreateAsync(new IdentityRole(model.Role));
            }

            await _userManager.AddToRoleAsync(user, model.Role);
            await _userManager.UpdateAsync(user);

            return RedirectToAction(nameof(Index));
        }

        public async Task<IActionResult> Delete(string id)
        {
            var user = await _userManager.FindByIdAsync(id);
            if (user == null) return NotFound();

            return View(user);
        }

        [HttpPost, ActionName("Delete")]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> DeleteConfirmed(string id)
        {
            var user = await _userManager.FindByIdAsync(id);
            if (user == null) return NotFound();

            // Xóa các mối quan hệ kết bạn liên quan trước khi xóa user
            var friendships = _context.Friendships
                .Where(f => f.RequesterId == id || f.AddresseeId == id)
            .ToList();

            _context.Friendships.RemoveRange(friendships);

            // Xóa người dùng
            await _context.SaveChangesAsync(); // Lưu trước khi xóa user
            await _userManager.DeleteAsync(user);

            return RedirectToAction(nameof(Index));
        }
        public async Task<IActionResult> Details(string id)
        {
            var user = await _userManager.FindByIdAsync(id);
            if (user == null) return NotFound();

            var roles = await _userManager.GetRolesAsync(user);

            var model = new AdminEditUserViewModel
            {
                Id = user.Id,
                Email = user.Email,
                DisplayName = user.DisplayName,
                Score = user.Score,
                ExistingAvatarPath = user.AvatarPath,
                Role = roles.FirstOrDefault() ?? "User",
                BannedUntil = user.BannedUntil
            };

            return View(model);
        }
        [HttpPost]
        public async Task<IActionResult> BanUser(string userId, int duration)
        {
            var user = await _userManager.FindByIdAsync(userId);
            if (user == null) return NotFound();

            if (duration == -1)
                user.BannedUntil = DateTime.MaxValue;
            else
                user.BannedUntil = DateTime.Now.AddMinutes(duration);

            await _userManager.UpdateAsync(user);
            return RedirectToAction("Details", new { id = userId });
        }

        [HttpPost]
        public async Task<IActionResult> UnbanUser(string userId)
        {
            var user = await _userManager.FindByIdAsync(userId);
            if (user == null) return NotFound();

            user.BannedUntil = null;
            await _userManager.UpdateAsync(user);
            return RedirectToAction("Details", new { id = userId });
        }
        [HttpGet]
        public async Task<IActionResult> ExportUsersToExcel()
        {
            ExcelPackage.LicenseContext = LicenseContext.NonCommercial;

            var users = _userManager.Users.ToList();
            // Tạo dictionary lưu vai trò
            var userRoles = new Dictionary<string, string>();
            foreach (var user in users)
            {
                var roles = await _userManager.GetRolesAsync(user);
                userRoles[user.Id] = roles.FirstOrDefault() ?? "User";
            }

            using var package = new ExcelPackage();
            var worksheet = package.Workbook.Worksheets.Add("Users");

            // Tiêu đề cột
            worksheet.Cells[1, 1].Value = "User ID";
            worksheet.Cells[1, 2].Value = "Email";
            worksheet.Cells[1, 3].Value = "Tên hiển thị";
            worksheet.Cells[1, 4].Value = "Điểm";
            worksheet.Cells[1, 5].Value = "Vai trò";

            for (int i = 0; i < users.Count; i++)
            {
                var user = users[i];
                worksheet.Cells[i + 2, 1].Value = user.Id;
                worksheet.Cells[i + 2, 2].Value = user.Email;
                worksheet.Cells[i + 2, 3].Value = user.DisplayName;
                worksheet.Cells[i + 2, 4].Value = user.Score;
                worksheet.Cells[i + 2, 5].Value = userRoles[user.Id];
            }

            worksheet.Cells.AutoFitColumns();

            var stream = new MemoryStream(package.GetAsByteArray());
            string fileName = $"DanhSachNguoiChoi_{DateTime.Now:yyyyMMddHHmmss}.xlsx";

            return File(stream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName);
        }



    }
}
