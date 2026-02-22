using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Caro.Models;
using System.IO;
using Microsoft.EntityFrameworkCore;
using Caro.Data;

namespace Caro.Controllers
{
    [Authorize(Roles = "Manager")]
    public class ManagerController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly IWebHostEnvironment _webHostEnvironment;
        private readonly RoleManager<IdentityRole> _roleManager;
        private readonly ApplicationDbContext _context;
        public ManagerController(
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

        // ✅ GET: Admin/Index
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
                    users = users.Where(u => u.BannedUntil != null && u.BannedUntil > DateTime.Now).ToList();
                else if (banFilter == "active")
                    users = users.Where(u => u.BannedUntil == null || u.BannedUntil <= DateTime.Now).ToList();
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
                Role = roles.FirstOrDefault() ?? "User"
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

    }
}
