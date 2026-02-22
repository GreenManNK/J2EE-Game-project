using Caro.Data;
using Caro.Models;
using Microsoft.EntityFrameworkCore;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace Caro.Services
{
    public class AchievementService
    {
        private readonly ApplicationDbContext _context;

        public AchievementService(ApplicationDbContext context)
        {
            _context = context;
        }

        public async Task<List<string>> GetAchievementsAsync(string userId)
        {
            return await _context.UserAchievements
                .Where(a => a.UserId == userId)
                .Select(a => a.AchievementName)
                .ToListAsync();
        }

        public async Task AddAchievementIfNotExistsAsync(string userId, string achievementName)
        {
            bool alreadyExists = await _context.UserAchievements
                .AnyAsync(a => a.UserId == userId && a.AchievementName == achievementName);

            if (!alreadyExists)
            {
                _context.UserAchievements.Add(new UserAchievement
                {
                    UserId = userId,
                    AchievementName = achievementName,
                    AchievedAt = DateTime.UtcNow
                });

                await _context.SaveChangesAsync();
            }
        }
    }
}
