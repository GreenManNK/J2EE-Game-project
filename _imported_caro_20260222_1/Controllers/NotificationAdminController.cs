using Caro.Data;
using Caro.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Caro.Controllers
{
    [Authorize(Roles = "Admin")]
    public class NotificationAdminController : Controller
    {
        private readonly ApplicationDbContext _context;

        public NotificationAdminController(ApplicationDbContext context)
        {
            _context = context;
        }

        // GET: /NotificationAdmin
        public IActionResult Index()
        {
            var notis = _context.SystemNotifications.OrderByDescending(n => n.CreatedAt).ToList();
            return View(notis);
        }

        // POST: /NotificationAdmin/Create
        [HttpPost]
        public async Task<IActionResult> Create(string content)
        {
            if (!string.IsNullOrWhiteSpace(content))
            {
                var noti = new SystemNotification { Content = content };
                _context.SystemNotifications.Add(noti);
                await _context.SaveChangesAsync();
            }

            return RedirectToAction("Index");
        }
        [HttpPost]
        public async Task<IActionResult> Delete(int id)
        {
            var noti = await _context.SystemNotifications.FindAsync(id);
            if (noti != null)
            {
                _context.SystemNotifications.Remove(noti);
                await _context.SaveChangesAsync();
            }

            return RedirectToAction("Index");
        }


    }
}
