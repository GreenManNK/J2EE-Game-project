using Microsoft.AspNetCore.SignalR;
using System;
using Microsoft.AspNetCore.Identity;
using Microsoft.Extensions.DependencyInjection;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Caro.Models;
using Caro.Data;
public class GameHub : Hub
{
    private static ConcurrentDictionary<string, (string roomId, string symbol)> players = new();
    private static ConcurrentDictionary<string, string> currentTurn = new();
    private static ConcurrentDictionary<string, string[,]> gameBoards = new();
    private static ConcurrentDictionary<string, int> roomPlayers = new();
    private static ConcurrentDictionary<string, string> connectionToUser = new();
    private static ConcurrentDictionary<string, (string displayName, string avatarPath)> userProfiles = new();
    private static ConcurrentQueue<(string connectionId, int score, DateTime timestamp)> waitingPlayers = new();

    private readonly UserManager<ApplicationUser> _userManager;
    private readonly ApplicationDbContext _context;

    public GameHub(UserManager<ApplicationUser> userManager, ApplicationDbContext context)
    {
        _userManager = userManager;
        _context = context;
    }
    public async Task JoinGame(string roomId, string userId, string displayName, string avatarPath)
    {
        if (!connectionToUser.ContainsKey(Context.ConnectionId))
        {
            await Groups.AddToGroupAsync(Context.ConnectionId, roomId);

            int playerCount = roomPlayers.GetOrAdd(roomId, 0);
            if (playerCount >= 2)
            {
                await Clients.Caller.SendAsync("ErrorMessage", "Phòng đã đầy!");
                return;
            }

            string symbol = playerCount == 0 ? "X" : "O";

            connectionToUser[Context.ConnectionId] = userId;
            players[userId] = (roomId, symbol);
            roomPlayers[roomId] = playerCount + 1;
            userProfiles[userId] = (displayName, avatarPath);

            if (!gameBoards.ContainsKey(roomId))
                gameBoards[roomId] = new string[10, 10];

            /*if (!currentTurn.ContainsKey(roomId))
                currentTurn[roomId] = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Value.symbol == "X").Key;*/

            if (!currentTurn.ContainsKey(roomId))
            {
                var firstXPlayer = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Value.symbol == "X");
                if (!string.IsNullOrEmpty(firstXPlayer.Key))
                {
                    currentTurn[roomId] = firstXPlayer.Key;
                }
                else
                {
                    Console.WriteLine($"Không tìm thấy người chơi X trong phòng {roomId}");
                    await Clients.Caller.SendAsync("RedirectToLobby");
                    return;
                }
            }


            Console.WriteLine($"{userId} vào phòng {roomId} với ký hiệu {symbol}");
            Console.WriteLine($"Phòng {roomId} có {roomPlayers[roomId]} người tham gia");

            await Clients.Group(roomId).SendAsync("PlayerJoined", userId, symbol);
            await Clients.All.SendAsync("UpdateRoomList", roomPlayers.Where(r => r.Value == 1).Select(r => r.Key).ToList());

            if (roomPlayers[roomId] == 2)
            {
                await ResetGame(roomId);
                await Clients.Group(roomId).SendAsync("GameStarted");

                // Gửi thông tin đối thủ cho từng người chơi
                var usersInRoom = players.Where(p => p.Value.roomId == roomId).Select(p => p.Key).ToList();

                foreach (var uid in usersInRoom)
                {
                    var opponentId = usersInRoom.FirstOrDefault(u => u != uid);

                    if (opponentId != null && userProfiles.TryGetValue(opponentId, out var profile))
                    {
                        var doiThu = await _userManager.FindByIdAsync(opponentId);
                        var connEntry = connectionToUser.FirstOrDefault(c => c.Value == uid);
                        if (!string.IsNullOrEmpty(connEntry.Key))
                        {
                            await Clients.Client(connEntry.Key).SendAsync("OpponentJoined", new
                            {
                                userId = opponentId,
                                displayName = profile.displayName,
                                avatarPath = profile.avatarPath,
                                score = doiThu?.Score ?? 0
                            });
                        }
                        else
                        {
                            Console.WriteLine($"Không tìm thấy connection cho người chơi {uid} để gửi OpponentJoined");
                        }
                    }
                }
            }
        }
    }

    public async Task LeaveGame()
    {
        if (connectionToUser.TryRemove(Context.ConnectionId, out var userId) &&
            players.TryRemove(userId, out var playerData))
        {
            string roomId = playerData.roomId;
            Console.WriteLine($"Người chơi {userId} đã rời phòng {roomId}");

            await Groups.RemoveFromGroupAsync(Context.ConnectionId, roomId);
            userProfiles.TryRemove(userId, out _);
            roomPlayers.AddOrUpdate(roomId, 0, (_, old) => old - 1);

            var remainingPlayers = players.Where(p => p.Value.roomId == roomId).ToList();
            int remainingCount = remainingPlayers.Count;

            Console.WriteLine($"Phòng {roomId} còn {remainingCount} người chơi");

            bool isRanked = roomId.StartsWith("Ranked_");
            bool hasX = false, hasO = false;

            if (gameBoards.TryGetValue(roomId, out var board))
            {
                for (int i = 0; i < 10; i++)
                    for (int j = 0; j < 10; j++)
                    {
                        if (board[i, j] == "X") hasX = true;
                        if (board[i, j] == "O") hasO = true;
                    }
            }

            if (remainingCount == 1)
            {
                string remainingUserId = remainingPlayers.First().Key;

                if (hasX && hasO && isRanked)
                {
                    await CapNhatDiemSauTran(remainingUserId, userId);

                    var loser = await _userManager.FindByIdAsync(userId);
                    var winner = await _userManager.FindByIdAsync(remainingUserId);

                    var loserConn = connectionToUser.FirstOrDefault(c => c.Value == userId).Key;
                    var winnerConn = connectionToUser.FirstOrDefault(c => c.Value == remainingUserId).Key;

                    if (!string.IsNullOrEmpty(winnerConn))
                        await Clients.Client(winnerConn).SendAsync("UpdateScore", winner.Score);

                    if (!string.IsNullOrEmpty(loserConn))
                        await Clients.Client(loserConn).SendAsync("UpdateScore", loser.Score);
                }

                if (isRanked)
                {
                    await Task.Delay(1000);
                    await HandleRemainingPlayerAsync(roomId, userId);
                }
                else
                {
                    players[remainingUserId] = (roomId, "X");
                    currentTurn[roomId] = remainingUserId;

                    var connId = connectionToUser.FirstOrDefault(c => c.Value == remainingUserId).Key;
                    if (!string.IsNullOrEmpty(connId))
                        await Clients.Client(connId).SendAsync("GameOver", "Đối thủ đã rời phòng.");
                }

                await Task.Delay(2000);
                await ResetGame(roomId);
            }
            else if (remainingCount == 0)
            {
                gameBoards.TryRemove(roomId, out _);
                currentTurn.TryRemove(roomId, out _);
                roomPlayers.TryRemove(roomId, out _);
                Console.WriteLine($"Phòng {roomId} không còn người chơi -> xóa phòng");
                await Clients.All.SendAsync("RemoveRoom", roomId);
            }

            await Clients.All.SendAsync("UpdateRoomList", roomPlayers.Keys.ToList());
            await Clients.Caller.SendAsync("RedirectToLobby");
        }
    }

    private async Task HandleRemainingPlayerAsync(string roomId, string leaverUserId)
    {
        var remainingPlayer = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Key != leaverUserId);
        string remainingUserId = remainingPlayer.Key;

        if (string.IsNullOrEmpty(remainingUserId)) return;

        var connId = connectionToUser.FirstOrDefault(c => c.Value == remainingUserId).Key;

        if (!string.IsNullOrEmpty(connId))
        {
            Console.WriteLine($"Gửi tín hiệu LeaveGame tới người chơi còn lại: {remainingUserId}");
            await Clients.Client(connId).SendAsync("GameOver", "Phòng đã đóng do đối thủ rời trận.");
            await Clients.Client(connId).SendAsync("ForceLeave");
        }
        else
        {
            Console.WriteLine($"Không tìm thấy connId của {remainingUserId} để gửi ForceLeave");
        }
    }




    public async Task MakeMove(string roomId, int x, int y)
    {
        if (!connectionToUser.TryGetValue(Context.ConnectionId, out var userId) ||
            !players.TryGetValue(userId, out var playerData) || playerData.roomId != roomId)
        {
            await Clients.Caller.SendAsync("ErrorMessage", "Bạn không thuộc phòng này.");
            return;
        }

        if (currentTurn[roomId] != userId)
        {
            await Clients.Caller.SendAsync("ErrorMessage", "Không phải lượt của bạn!");
            return;
        }

        string[,] board = gameBoards[roomId];
        if (!string.IsNullOrEmpty(board[x, y]))
        {
            await Clients.Caller.SendAsync("ErrorMessage", "Ô này đã có quân cờ!");
            return;
        }

        string symbol = playerData.symbol;
        board[x, y] = symbol;
        await Clients.Group(roomId).SendAsync("ReceiveMove", x, y, symbol);

        if (CheckWin(board, x, y, symbol))
        {
            var winnerUser = await _userManager.FindByIdAsync(userId);
            await Clients.Group(roomId).SendAsync("GameOver", $"{winnerUser.DisplayName} thắng!");

            string winnerId = userId;
            string loserId = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Key != winnerId).Key;
            //Tính số lượt đã đi
            int moveCount = 0;
            for (int i = 0; i < board.GetLength(0); i++)
                for (int j = 0; j < board.GetLength(1); j++)
                    if (!string.IsNullOrEmpty(board[i, j]))
                        moveCount++;

            if (!string.IsNullOrEmpty(loserId))
            {
                var winner = await _userManager.FindByIdAsync(winnerId);
                var loser = await _userManager.FindByIdAsync(loserId);
                // lưu lịch sử
                if (roomId.StartsWith("Ranked_") || roomId.StartsWith("Normal_") || roomId.StartsWith("Challenge_"))
                {
                    var player1 = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Value.symbol == "X").Key;
                    var player2 = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Value.symbol == "O").Key;

                    if (string.IsNullOrEmpty(player1) || string.IsNullOrEmpty(player2))
                    {
                        Console.WriteLine("Không đủ thông tin người chơi để lưu lịch sử!");
                        return;
                    }

                    string prefix = "game_";
                    if (roomId.StartsWith("Ranked_")) prefix = "Ranked_";
                    else if (roomId.StartsWith("Normal_")) prefix = "Normal_";
                    else if (roomId.StartsWith("Challenge_")) prefix = "Challenge_";

                    var game = new GameHistory
                    {
                        GameCode = prefix + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        Player1Id = player1,
                        Player2Id = player2,
                        FirstPlayerId = currentTurn.ContainsKey(roomId) ? currentTurn[roomId] : null,
                        TotalMoves = moveCount,
                        WinnerId = winnerId
                    };

                    _context.GameHistories.Add(game);
                    await _context.SaveChangesAsync();

                    Console.WriteLine($"Đã lưu lịch sử trận {game.GameCode} ({player1} vs {player2})");
                }

                if (roomId.StartsWith("Ranked_") || roomId.StartsWith("Normal_"))
                {
                    //Thành tựu
                    await CheckSpecialAchievements(winner, loser, moveCount, roomId);
                    var winnerConn = connectionToUser.FirstOrDefault(c => c.Value == winnerId).Key;
                    var loserConn = connectionToUser.FirstOrDefault(c => c.Value == loserId).Key;

                    if (!string.IsNullOrEmpty(winnerConn))
                    {
                        await Clients.Client(winnerConn).SendAsync("UpdateScore", winner.Score);
                        await Clients.Client(winnerConn).SendAsync("OpponentJoined", new
                        {
                            userId = loser.Id,
                            displayName = loser.DisplayName,
                            avatarPath = loser.AvatarPath,
                            score = loser.Score
                        });
                    }

                    if (!string.IsNullOrEmpty(loserConn))
                    {
                        await Clients.Client(loserConn).SendAsync("UpdateScore", loser.Score);
                        await Clients.Client(loserConn).SendAsync("OpponentJoined", new
                        {
                            userId = winner.Id,
                            displayName = winner.DisplayName,
                            avatarPath = winner.AvatarPath,
                            score = winner.Score
                        });
                    }
                }    

                if (roomId.StartsWith("Ranked_"))
                {
                    await CapNhatDiemSauTran(winnerId, loserId);
 
                    var nguoiThang = await _userManager.FindByIdAsync(winnerId);
                    var nguoiThua = await _userManager.FindByIdAsync(loserId);
                    var connThang = connectionToUser.FirstOrDefault(c => c.Value == winnerId).Key;
                    var connThua = connectionToUser.FirstOrDefault(c => c.Value == loserId).Key;

                    if (!string.IsNullOrEmpty(connThang))
                        await Clients.Client(connThang).SendAsync("UpdateScore", nguoiThang.Score);

                    if (!string.IsNullOrEmpty(connThua))
                        await Clients.Client(connThua).SendAsync("UpdateScore", nguoiThua.Score);
                    // Gửi lại thông tin đối thủ đã cập nhật điểm số
                    if (!string.IsNullOrEmpty(connThang))
                    {
                        await Clients.Client(connThang).SendAsync("OpponentJoined", new
                        {
                            userId = nguoiThua.Id,
                            displayName = nguoiThua.DisplayName,
                            avatarPath = nguoiThua.AvatarPath,
                            score = nguoiThua.Score  // đã được cập nhật
                        });
                    }

                    if (!string.IsNullOrEmpty(connThua))
                    {
                        await Clients.Client(connThua).SendAsync("OpponentJoined", new
                        {
                            userId = nguoiThang.Id,
                            displayName = nguoiThang.DisplayName,
                            avatarPath = nguoiThang.AvatarPath,
                            score = nguoiThang.Score  // đã được cập nhật
                        });
                    }

                }

            }

            await Task.Delay(2000);
            await ResetGame(roomId);
        }
        else
        {
            var nextPlayer = players.Keys.FirstOrDefault(id => id != userId && players[id].roomId == roomId);
            if (nextPlayer != null)
                currentTurn[roomId] = nextPlayer;
        }
    }


    public async Task SendMessage(string roomId, string userId, string message)
    {
        if (userProfiles.TryGetValue(userId, out var profile))
        {
            await Clients.Group(roomId).SendAsync("ReceiveMessage", userId, profile.displayName, profile.avatarPath, message);
        }
    }

    public override async Task OnConnectedAsync()
    {
        var userId = Context.UserIdentifier;
        var connectionId = Context.ConnectionId;

        if (!string.IsNullOrEmpty(userId))
        {
            // Cập nhật IsOnline
            var user = await _userManager.FindByIdAsync(userId);
            if (user != null)
            {
                user.IsOnline = true;
                await _userManager.UpdateAsync(user);
            }
            connectionToUser[userId] = connectionId;
        }

        await base.OnConnectedAsync();
    }


    public override async Task OnDisconnectedAsync(Exception exception)
    {
        string disconnectedUserId = connectionToUser.FirstOrDefault(kv => kv.Value == Context.ConnectionId).Key;

        if (!string.IsNullOrEmpty(disconnectedUserId))
        {
            // Cập nhật trạng thái offline
            var user = await _userManager.FindByIdAsync(disconnectedUserId);
            if (user != null)
            {
                user.IsOnline = false;
                await _userManager.UpdateAsync(user);
            }

            // Gỡ kết nối
            connectionToUser.TryRemove(disconnectedUserId, out _);
        }

        await LeaveGame();
        await base.OnDisconnectedAsync(exception);
    }

    public async Task ResetGame(string roomId)
    {
        if (gameBoards.ContainsKey(roomId))
        {
            gameBoards[roomId] = new string[10, 10];
            var firstPlayer = players.FirstOrDefault(p => p.Value.roomId == roomId && p.Value.symbol == "X").Key;
            if (firstPlayer != null)
                currentTurn[roomId] = firstPlayer;
            await Clients.Group(roomId).SendAsync("GameReset");
        }
    }

    private bool CheckWin(string[,] board, int x, int y, string symbol)
    {
        return CheckDirection(board, x, y, symbol, 1, 0) ||
               CheckDirection(board, x, y, symbol, 0, 1) ||
               CheckDirection(board, x, y, symbol, 1, 1) ||
               CheckDirection(board, x, y, symbol, 1, -1);
    }

    private bool CheckDirection(string[,] board, int x, int y, string symbol, int dx, int dy)
    {
        int count = 1;
        for (int i = 1; i < 5; i++)
            if (IsValid(x + i * dx, y + i * dy) && board[x + i * dx, y + i * dy] == symbol)
                count++;
            else break;

        for (int i = 1; i < 5; i++)
            if (IsValid(x - i * dx, y - i * dy) && board[x - i * dx, y - i * dy] == symbol)
                count++;
            else break;

        return count >= 5;
    }

    private bool IsValid(int x, int y) => x >= 0 && x < 10 && y >= 0 && y < 10;

    public async Task GetRoomList()
    {
        var availableRooms = roomPlayers.Where(r => r.Value == 1).Select(r => r.Key).ToList();
        await Clients.Caller.SendAsync("UpdateRoomList", availableRooms);
    }

    public string GetConnectionId()
    {
        return Context.ConnectionId;
    }
    public async Task FindMatch(string userId, string displayName, string avatarPath)
    {
        string connectionId = Context.ConnectionId;
        var user = await _userManager.FindByIdAsync(userId);
        if (user == null) return;

        int score = user.Score;
        var entryTime = DateTime.UtcNow;
        waitingPlayers.Enqueue((connectionId, score, entryTime));
        Console.WriteLine($"{connectionId} ({score}) vào hàng đợi lúc {entryTime:HH:mm:ss}");
        // Delay ngắn để đảm bảo Queue ổn định
        await Task.Delay(300);
        // Duyệt các cặp tiềm năng trong danh sách chờ
        var candidates = waitingPlayers.ToList();
        foreach (var p1 in candidates)
        {
            foreach (var p2 in candidates)
            {
                if (p1.connectionId == p2.connectionId) continue;

                var wait1 = (DateTime.UtcNow - p1.timestamp).TotalSeconds;
                var wait2 = (DateTime.UtcNow - p2.timestamp).TotalSeconds;
                var scoreDiff = Math.Abs(p1.score - p2.score);
                
                bool isStrictMatch = scoreDiff <= 50;
                bool isRelaxedMatch = scoreDiff > 50 && (wait1 >= 15 || wait2 >= 15);
                if (isStrictMatch || isRelaxedMatch)
                {
                    // Gỡ khỏi queue
                    waitingPlayers = new ConcurrentQueue<(string, int, DateTime)>(waitingPlayers.Where(x => x.connectionId != p1.connectionId && x.connectionId != p2.connectionId));

                    // Tạo phòng
                    string roomId = $"Ranked_{DateTime.Now.Ticks}";
                    Console.WriteLine($"Ghep {p1.connectionId} ({p1.score}) vs {p2.connectionId} ({p2.score}) tai {DateTime.UtcNow:HH:mm:ss}");

                    await Clients.Client(p1.connectionId).SendAsync("RoomCreated", roomId);
                    await Clients.Client(p2.connectionId).SendAsync("JoinRoom", roomId);
                    await Groups.AddToGroupAsync(p1.connectionId, roomId);
                    await Groups.AddToGroupAsync(p2.connectionId, roomId);

                    await JoinGame(roomId, connectionToUser.GetValueOrDefault(p1.connectionId) ?? p1.connectionId, displayName, avatarPath);
                    await JoinGame(roomId, connectionToUser.GetValueOrDefault(p2.connectionId) ?? userId, displayName, avatarPath);

                    currentTurn[roomId] = players.First(p => p.Value.roomId == roomId && p.Value.symbol == "X").Key;

                    await Clients.Group(roomId).SendAsync("GameStarted", new
                    {
                        roomId = roomId,
                        PlayerX = new { ConnectionId = p1.connectionId, Symbol = "X" },
                        PlayerO = new { ConnectionId = p2.connectionId, Symbol = "O" },
                        CurrentTurn = currentTurn[roomId]
                    });
                    return;
                }
            }
        }
        await Clients.Client(connectionId).SendAsync("WaitingForOpponent", "Đang chờ đối thủ...");
    }
    public async Task CancelMatchmaking()
    {
        string userId = Context.ConnectionId;
        if (waitingPlayers.Any(p => p.connectionId == userId))
        {
            waitingPlayers = new ConcurrentQueue<(string, int, DateTime)>(waitingPlayers.Where(p => p.connectionId != userId));
            Console.WriteLine($"{userId} đa huy tim tran.");
            await Clients.Client(userId).SendAsync("MatchmakingCanceled");
        }
    }


    public async Task<string> SendChallenge(string toUserId)
    {
        var fromUserId = Context.UserIdentifier;
        var roomId = $"Challenge_{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}";

        var newRequest = new ChallengeRequest
        {
            FromUserId = fromUserId,
            ToUserId = toUserId,
            RoomId = roomId,
            SentAt = DateTime.UtcNow
        };

        _context.ChallengeRequests.Add(newRequest);
        await _context.SaveChangesAsync();

        if (connectionToUser.TryGetValue(toUserId, out var conn))
        {
            if (userProfiles.TryGetValue(fromUserId, out var profile))
            {
                await Clients.Client(conn).SendAsync("ReceiveChallengeInvite", fromUserId, profile.displayName, roomId, newRequest.Id);
            }
        }

        return newRequest.Id.ToString(); // tra ve requestId để redirect sang trang chờ
    }

    public async Task RespondToChallenge(int requestId, bool accept)
    {
        var toUserId = Context.UserIdentifier;
        var request = await _context.ChallengeRequests.FindAsync(requestId);

        if (request == null || request.ToUserId != toUserId || request.IsHandled) return;

        request.IsAccepted = accept;
        request.IsHandled = true;
        await _context.SaveChangesAsync();

        if (accept)
        {
            if (connectionToUser.TryGetValue(request.FromUserId, out var conn1) &&
                connectionToUser.TryGetValue(toUserId, out var conn2))
            {
                await Clients.Client(conn1).SendAsync("StartChallengeGame", request.RoomId);
                await Clients.Client(conn2).SendAsync("StartChallengeGame", request.RoomId);

                if (userProfiles.TryGetValue(request.FromUserId, out var profile1))
                    await JoinGame(request.RoomId, request.FromUserId, profile1.displayName, profile1.avatarPath);

                if (userProfiles.TryGetValue(toUserId, out var profile2))
                    await JoinGame(request.RoomId, toUserId, profile2.displayName, profile2.avatarPath);
            }
        }
        else
        {
            if (connectionToUser.TryGetValue(request.FromUserId, out var conn))
            {
                await Clients.Client(conn).SendAsync("ChallengeDeclined");
            }
        }
    }

    public async Task RegisterPlayerInfo()
    {
        var userId = Context.UserIdentifier;
        var connectionId = Context.ConnectionId;

        if (!string.IsNullOrEmpty(userId))
        {
            connectionToUser[userId] = connectionId;
        }
        var user = await _userManager.FindByIdAsync(userId);
        if (user != null)
        {
            userProfiles[userId] = (user.DisplayName, user.AvatarPath);
        }
    }

    public async Task CapNhatDiemSauTran(string userWinId, string userLoseId)
    {
        var nguoiThang = await _userManager.FindByIdAsync(userWinId);
        var nguoiThua = await _userManager.FindByIdAsync(userLoseId);

        if (nguoiThang != null && nguoiThua != null)
        {
            if (nguoiThang.Score < nguoiThua.Score / 2)
            {
                nguoiThang.Score += 10;
                nguoiThua.Score -= 15;
            }
            else
            {
                nguoiThang.Score += 5;
                nguoiThua.Score -= 5;
            }

            // Không cho điểm âm
            nguoiThang.Score = Math.Max(0, nguoiThang.Score);
            nguoiThua.Score = Math.Max(0, nguoiThua.Score);

            //Cập nhật HighestScore nếu điểm mới cao hơn
            if (nguoiThang.Score > nguoiThang.HighestScore)
                nguoiThang.HighestScore = nguoiThang.Score;

            if (nguoiThua.Score > nguoiThua.HighestScore)
                nguoiThua.HighestScore = nguoiThua.Score;

            await _userManager.UpdateAsync(nguoiThang);
            await _userManager.UpdateAsync(nguoiThua);
        }
    }
    private async Task CheckSpecialAchievements(ApplicationUser winner, ApplicationUser loser, int totalMoves, string roomId)
    {
        var existing = _context.UserAchievements
            .Where(a => a.UserId == winner.Id || a.UserId == loser.Id)
            .ToList();

        var existingNamesWinner = existing
            .Where(a => a.UserId == winner.Id)
            .Select(a => a.AchievementName)
            .ToHashSet();

        var existingNamesLoser = existing
            .Where(a => a.UserId == loser.Id)
            .Select(a => a.AchievementName)
            .ToHashSet();

        var newAchievements = new List<UserAchievement>();

        if (roomId.StartsWith("Ranked_"))
        {
            if (winner.Score >= 100 && !existingNamesWinner.Contains("Bạc"))
            {
                newAchievements.Add(new UserAchievement
                {
                    UserId = winner.Id,
                    AchievementName = "Bạc",
                    AchievedAt = DateTime.Now
                });
            }
            if (winner.Score >= 300 && !existingNamesWinner.Contains("Vàng"))
            {
                newAchievements.Add(new UserAchievement
                {
                    UserId = winner.Id,
                    AchievementName = "Vàng",
                    AchievedAt = DateTime.Now
                });
            }
            if (winner.Score >= 500 && !existingNamesWinner.Contains("Kim cương"))
            {
                newAchievements.Add(new UserAchievement
                {
                    UserId = winner.Id,
                    AchievementName = "Kim cương",
                    AchievedAt = DateTime.Now
                });
            }
            if (winner.Score >= 1000 && !existingNamesWinner.Contains("Thách đấu"))
            {
                newAchievements.Add(new UserAchievement
                {
                    UserId = winner.Id,
                    AchievementName = "Thách đấu",
                    AchievedAt = DateTime.Now
                });
            }
        }


        //Đấu trí thắng Phật (nhiều lần)
        if (loser.Score >= winner.Score * 3)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Đấu trí thắng Phật",
                AchievedAt = DateTime.Now
            });
        }

        //Hạ gục vua (nhiều lần)
        var topPlayer = _userManager.Users.OrderByDescending(u => u.Score).FirstOrDefault();
        if (topPlayer != null && topPlayer.Id == loser.Id)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Hạ gục vua",
                AchievedAt = DateTime.Now
            });
        }

        //Chuỗi bất bại ( bây giờ thì tính 1 lần)
        var winnerGames = _context.GameHistories
            .Where(g => (g.Player1Id == winner.Id || g.Player2Id == winner.Id) && g.GameCode.StartsWith("Ranked_"))
            .OrderBy(g => g.PlayedAt)
            .ToList();

        int winStreak = 0;
        int maxWinStreak = 0;
        foreach (var g in winnerGames)
        {
            if (g.WinnerId == winner.Id)
            {
                winStreak++;
                maxWinStreak = Math.Max(maxWinStreak, winStreak);
                if (winStreak == 5 && !existingNamesWinner.Contains("Chuỗi bất bại"))
                {
                    newAchievements.Add(new UserAchievement
                    {
                        UserId = winner.Id,
                        AchievementName = "Chuỗi bất bại",
                        AchievedAt = DateTime.Now
                    });
                }
            }
            else
            {
                winStreak = 0;
            }
        }
        //Không thể ngăn cản
        if (maxWinStreak >= 5)
        {
            string achievementName = $"Không thể ngăn cản: {maxWinStreak}";

            var existingKhongthengancan = existing
                .Where(a => a.UserId == winner.Id && a.AchievementName.StartsWith("Không thể ngăn cản"))
                .OrderByDescending(a => a.AchievedAt)
                .FirstOrDefault();

            if (existingKhongthengancan == null)
            {
                newAchievements.Add(new UserAchievement { UserId = winner.Id, AchievementName = achievementName, AchievedAt = DateTime.Now });
            }
            else
            {
                var oldNumber = int.Parse(existingKhongthengancan.AchievementName.Split(':').Last().Trim());
                if (maxWinStreak > oldNumber)
                {
                    _context.UserAchievements.Remove(existingKhongthengancan);
                    newAchievements.Add(new UserAchievement { UserId = winner.Id, AchievementName = achievementName, AchievedAt = DateTime.Now });
                }
            }
        }

        //Chuỗi thua huyền thoại ( bây giờ thì tính 1 lần)
        var loserGames = _context.GameHistories
            .Where(g => (g.Player1Id == loser.Id || g.Player2Id == loser.Id) && g.GameCode.StartsWith("Ranked_"))
            .OrderBy(g => g.PlayedAt)
            .ToList();

        int loseStreak = 0;
        int maxLoseStreak = 0;

        foreach (var g in loserGames)
        {
            if (g.WinnerId != loser.Id)
            {
                loseStreak++;
                maxLoseStreak = Math.Max(maxLoseStreak, loseStreak);
                if (loseStreak == 5 && !existingNamesLoser.Contains("Chuỗi thua huyền thoại"))
                {
                    newAchievements.Add(new UserAchievement
                    {
                        UserId = loser.Id,
                        AchievementName = "Chuỗi thua huyền thoại",
                        AchievedAt = DateTime.Now
                    });
                }
            }
            else
            {
                loseStreak = 0;
            }
        }

        // Kẻ cố chấp
        if (maxLoseStreak >= 5)
        {
            string achievementName = $"Kẻ cố chấp: {maxLoseStreak}";

            var existingCoChap = existing
                .Where(a => a.UserId == loser.Id && a.AchievementName.StartsWith("Kẻ cố chấp"))
                .OrderByDescending(a => a.AchievedAt)
                .FirstOrDefault();

            if (existingCoChap == null)
            {
                newAchievements.Add(new UserAchievement { UserId = loser.Id, AchievementName = achievementName, AchievedAt = DateTime.Now });
            }
            else
            {
                var oldNumber = int.Parse(existingCoChap.AchievementName.Split(':').Last().Trim());
                if (maxLoseStreak > oldNumber)
                {
                    _context.UserAchievements.Remove(existingCoChap);
                    newAchievements.Add(new UserAchievement { UserId = loser.Id, AchievementName = achievementName, AchievedAt = DateTime.Now });
                }
            }
        }


        // ❤️ Chơi vì đam mê (chỉ 1 lần)
        if (winnerGames.Count >= 100)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Chơi vì đam mê",
                AchievedAt = DateTime.Now
            });
        }

        //1 phút lơ là (nhiều lần)
        if (winner.Score <= loser.Score / 3)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = loser.Id,
                AchievementName = "1 phút lơ là",
                AchievedAt = DateTime.Now
            });
        }

        //Kẻ sát thần (nhiều lần)
        if (loser.Id == "admin001-aa00-a00a-00aa-aa00aa00aa00")
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Kẻ sát thần",
                AchievedAt = DateTime.Now
            });
        }

        //Chiến thắng khó khăn (nhiều lần)
        if (totalMoves >= 60)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Chiến thắng khó khăn",
                AchievedAt = DateTime.Now
            });
        }

        //Thất bại xứng đáng (nhiều lần)
        if (totalMoves >= 60)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = loser.Id,
                AchievementName = "Thất bại xứng đáng",
                AchievedAt = DateTime.Now
            });
        }

        //Chiến thắng không tưởng (nhiều lần)
        if (loser.Score >= winner.Score * 10)
        {
            newAchievements.Add(new UserAchievement
            {
                UserId = winner.Id,
                AchievementName = "Chiến thắng không tưởng",
                AchievedAt = DateTime.Now
            });
        }

        //Lưu và thông báo nếu có
        if (newAchievements.Any())
        {
            _context.UserAchievements.AddRange(newAchievements);

            _context.AchievementNotifications.AddRange(
                newAchievements.Select(a => new AchievementNotification
                {
                    UserId = a.UserId,
                    AchievementName = a.AchievementName,
                    CreatedAt = DateTime.Now
                })
            );

            await _context.SaveChangesAsync();
            var badAchievements = new HashSet<string> { "Chuỗi thua huyền thoại", "1 phút lơ là" };
            foreach (var a in newAchievements)
            {
                Console.WriteLine($"Đã trao thành tựu: {a.AchievementName}");
                bool isBad = badAchievements.Any(b => a.AchievementName.StartsWith(b)) || a.AchievementName.StartsWith("Kẻ cố chấp");
                if (connectionToUser.TryGetValue(a.UserId, out var connId))
                {
                    await Clients.Client(connId).SendAsync("ShowAchievementToast", a.AchievementName, isBad);
                }
            }
        }
    }
}