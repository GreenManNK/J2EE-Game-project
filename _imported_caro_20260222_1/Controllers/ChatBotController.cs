using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace Caro.Controllers
{
    public class ChatBotController : Controller
    {
        private readonly IConfiguration _configuration;
        private readonly HttpClient _httpClient;

        public ChatBotController(IConfiguration configuration)
        {
            _configuration = configuration;
            _httpClient = new HttpClient();
        }

        public IActionResult Index()
        {
            return View();
        }

        [HttpPost]
        public async Task<IActionResult> SendMessage(string message)
        {
            var apiKey = _configuration["HuggingFace:ApiKey"];
            var model = _configuration["HuggingFace:Model"];

            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

            // ===== Trả lời cứng cho một số câu hỏi =====
            string normalized = message.ToLower();

            if (normalized.Contains("đấu rank") || normalized.Contains("dau rank"))
            {
                return Json(new
                {
                    success = true,
                    reply = "Hướng dẫn đấu xếp hạng:\n1. Vào mục 'Home' ở góc trên bên trái.\n2. Chọn multiplayer.\n3. Chọn 'online'.\n4. Bấm 'Tìm trận' để vào trận xếp hạng."
                });
            }

            if (normalized.Contains("xem bảng xếp hạng") || normalized.Contains("bang xep hang"))
            {
                return Json(new
                {
                    success = true,
                    reply = "Chọn 'Bảng xếp hạng' phía trên bên trái để xem thứ hạng người chơi theo điểm số."
                });
            }

            if (normalized.Contains("đổi mật khẩu") || normalized.Contains("doi mat khau"))
            {
                return Json(new
                {
                    success = true,
                    reply = "Click vào avatar góc phải trên cùng → chọn 'Đổi mật khẩu'."
                });
            }

            // ===== Gửi đến mô hình AI HuggingFace (nếu không khớp câu hỏi cứng) =====
            var requestBody = new
            {
                inputs = message
            };

            var content = new StringContent(JsonSerializer.Serialize(requestBody), Encoding.UTF8, "application/json");
            var response = await _httpClient.PostAsync($"https://api-inference.huggingface.co/models/{model}", content);

            if (!response.IsSuccessStatusCode)
            {
                var err = await response.Content.ReadAsStringAsync();
                return Json(new { success = false, message = $"Lỗi Hugging Face: {response.StatusCode} - {err}" });
            }

            var responseString = await response.Content.ReadAsStringAsync();

            try
            {
                using var doc = JsonDocument.Parse(responseString);
                var result = doc.RootElement[0].GetProperty("generated_text").GetString();
                return Json(new { success = true, reply = result });
            }
            catch
            {
                return Json(new { success = false, message = "Lỗi xử lý phản hồi từ mô hình AI!" });
            }
        }
    }
}
