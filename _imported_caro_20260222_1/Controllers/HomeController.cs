using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Caro.Models;
using Caro.Data;
using System;
using System.Linq;

namespace Caro.Controllers
{
    public class HomeController : Controller
    {
        private readonly ApplicationDbContext _context;

        public HomeController(ApplicationDbContext context)
        {
            _context = context;
        }

        public IActionResult Index()
        {
            var posts = _context.Posts
                .Include(p => p.Comments)
                .OrderByDescending(p => p.CreatedAt)
                .ToList();

            return View(posts);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> CreatePost(string Content, IFormFile? ImageFile)
        {
            string imagePath = null;

            if (ImageFile != null && ImageFile.Length > 0)
            {
                var fileName = Guid.NewGuid().ToString() + Path.GetExtension(ImageFile.FileName);
                var savePath = Path.Combine(Directory.GetCurrentDirectory(), "wwwroot/uploads", fileName);

                Directory.CreateDirectory(Path.GetDirectoryName(savePath)!);

                using (var stream = new FileStream(savePath, FileMode.Create))
                {
                    await ImageFile.CopyToAsync(stream);
                }

                imagePath = "/uploads/" + fileName;
            }

            var post = new Post
            {
                Content = Content,
                Author = User.Identity?.Name ?? "Ẩn danh",
                CreatedAt = DateTime.Now,
                ImagePath = imagePath
            };

            _context.Posts.Add(post);
            await _context.SaveChangesAsync();

            return RedirectToAction("Index");
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult CreateComment(int postId, string content)
        {
            if (!string.IsNullOrWhiteSpace(content))
            {
                var comment = new Comment
                {
                    PostId = postId,
                    Content = content,
                    Author = User.Identity?.Name ?? "Ẩn danh",
                    CreatedAt = DateTime.Now
                };
                _context.Comments.Add(comment);
                _context.SaveChanges();
            }
            return RedirectToAction("Index");
        }

        public IActionResult Multiplayer()
        {
            return View();
        }

        public IActionResult SinglePlayer()
        {
            return View();
        }

        public IActionResult TienDo()
        {
            return View();
        }
    }
}
