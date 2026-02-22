using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using Microsoft.AspNetCore.SignalR;

[Authorize] // ✅ Chặn người chưa đăng nhập vào sảnh/phòng
public class LobbyController : Controller
{
    private readonly IHubContext<GameHub> _gameHubContext;

    public LobbyController(IHubContext<GameHub> gameHubContext)
    {
        _gameHubContext = gameHubContext;
    }

    public IActionResult Index()
    {
        var userId = User.FindFirstValue(ClaimTypes.NameIdentifier);

        ViewBag.UserId = userId;

        return View();
    }

    public ActionResult JoinRoom(string roomId)
    {
        var userId = User.FindFirstValue(ClaimTypes.NameIdentifier);

        return RedirectToAction("Index", "Game", new { roomId });
    }

    [HttpPost]
    public async Task<IActionResult> StartMatchmaking()
    {
        var connectionId = User.FindFirstValue(ClaimTypes.NameIdentifier); 
        await _gameHubContext.Clients.Client(connectionId).SendAsync("FindMatch");
        return Ok();
    }
    [HttpPost]
    public async Task<IActionResult> CancelMatchmaking()
    {
        var connectionId = User.FindFirstValue(ClaimTypes.NameIdentifier);
        await _gameHubContext.Clients.Client(connectionId).SendAsync("CancelMatchmaking");
        return Ok();
    }
}
