using Microsoft.AspNetCore.SignalR;
using System.Threading.Tasks;

namespace Caro.Hubs
{
    public class ChatHub : Hub
    {
        // Gửi tin nhắn riêng
        public async Task SendPrivateMessage(string toUserId, string message)
        {
            var fromUserId = Context.UserIdentifier;

            if (!string.IsNullOrEmpty(fromUserId) && !string.IsNullOrEmpty(toUserId))
            {
                // Gửi cho người nhận
                await Clients.User(toUserId).SendAsync("ReceiveMessage", fromUserId, message);
                await Clients.Caller.SendAsync("ReceiveMessage", fromUserId, message);
            }
        }

        public override Task OnConnectedAsync()
        {
            Console.WriteLine($"User connected: {Context.UserIdentifier}");
            return base.OnConnectedAsync();
        }

        public override Task OnDisconnectedAsync(Exception exception)
        {
            Console.WriteLine($"User disconnected: {Context.UserIdentifier}");
            return base.OnDisconnectedAsync(exception);
        }
    }
}
