using Caro.Data;
using Caro.Models;
using Microsoft.EntityFrameworkCore;

namespace Caro.Services
{
    public class FriendshipService
    {
        private readonly ApplicationDbContext _context;

        public FriendshipService(ApplicationDbContext context)
        {
            _context = context;
        }

        // Gửi lời mời kết bạn
        public async Task<bool> SendRequest(string requesterId, string addresseeId)
        {
            if (requesterId == addresseeId) return false;

            var exists = await _context.Friendships.AnyAsync(f =>
                (f.RequesterId == requesterId && f.AddresseeId == addresseeId) ||
                (f.RequesterId == addresseeId && f.AddresseeId == requesterId));

            if (exists) return false;

            var friendship = new Friendship
            {
                RequesterId = requesterId,
                AddresseeId = addresseeId,
                IsAccepted = false
            };

            _context.Friendships.Add(friendship);
            await _context.SaveChangesAsync();
            return true;
        }

        // Chấp nhận lời mời kết bạn
        public async Task<bool> AcceptRequest(int id)
        {
            var friendship = await _context.Friendships.FindAsync(id);
            if (friendship == null || friendship.IsAccepted) return false;

            friendship.IsAccepted = true;
            await _context.SaveChangesAsync();
            return true;
        }

        // Hủy kết bạn hoặc từ chối lời mời
        public async Task<bool> RemoveFriendship(int id)
        {
            var friendship = await _context.Friendships.FindAsync(id);
            if (friendship == null) return false;

            _context.Friendships.Remove(friendship);
            await _context.SaveChangesAsync();
            return true;
        }

        // Lấy danh sách bạn bè của một người dùng
        public async Task<List<ApplicationUser>> GetFriends(string userId)
        {
            var friendIds = await _context.Friendships
                .Where(f => (f.RequesterId == userId || f.AddresseeId == userId) && f.IsAccepted)
                .Select(f => f.RequesterId == userId ? f.AddresseeId : f.RequesterId)
                .ToListAsync();

            return await _context.Users
                .Where(u => friendIds.Contains(u.Id))
                .ToListAsync();
        }

        // Lấy danh sách lời mời kết bạn đang chờ (nhận)
        public async Task<List<Friendship>> GetPendingRequests(string userId)
        {
            return await _context.Friendships
                .Include(f => f.Requester)
                .Where(f => f.AddresseeId == userId && !f.IsAccepted)
                .ToListAsync();
        }

        // Lấy danh sách lời mời đã gửi mà chưa được chấp nhận
        public async Task<List<Friendship>> GetSentRequests(string userId)
        {
            return await _context.Friendships
                .Include(f => f.Addressee)
                .Where(f => f.RequesterId == userId && !f.IsAccepted)
                .ToListAsync();
        }

        public async Task<bool> RemoveFriendship(string userId1, string userId2)
        {
            var friendship = await _context.Friendships.FirstOrDefaultAsync(f =>
                (f.RequesterId == userId1 && f.AddresseeId == userId2) ||
                (f.RequesterId == userId2 && f.AddresseeId == userId1));

            if (friendship == null) return false;

            _context.Friendships.Remove(friendship);
            await _context.SaveChangesAsync();
            return true;
        }
        public async Task<bool> AreFriends(string userId1, string userId2)
        {
            return await _context.Friendships.AnyAsync(f =>
                f.IsAccepted &&
                ((f.RequesterId == userId1 && f.AddresseeId == userId2) ||
                 (f.RequesterId == userId2 && f.AddresseeId == userId1)));
        }

        public async Task<bool> HasPendingRequest(string fromId, string toId)
        {
            return await _context.Friendships.AnyAsync(f =>
                !f.IsAccepted &&
                f.RequesterId == fromId && f.AddresseeId == toId);
        }

        public async Task<bool> RemoveRequestById(int id)
        {
            var friendship = await _context.Friendships.FindAsync(id);
            if (friendship == null || friendship.IsAccepted) return false;

            _context.Friendships.Remove(friendship);
            await _context.SaveChangesAsync();
            return true;
        }

    }
}
