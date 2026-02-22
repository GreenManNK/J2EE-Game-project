using System.Net;
using System.Net.Mail;
using Microsoft.Extensions.Configuration;

namespace Caro.Services
{
    public class EmailService : IEmailService
    {
        private readonly IConfiguration _config;

        public EmailService(IConfiguration config)
        {
            _config = config;
        }

        public async Task SendEmailAsync(string toEmail, string subject, string message)
        {
            var smtp = _config.GetSection("SmtpSettings");
            var client = new SmtpClient(smtp["Host"], int.Parse(smtp["Port"]))
            {
                Credentials = new NetworkCredential(smtp["UserName"], smtp["Password"]),
                EnableSsl = bool.Parse(smtp["EnableSsl"])
            };

            var mail = new MailMessage(smtp["UserName"], toEmail, subject, message)
            {
                IsBodyHtml = true
            };

            await client.SendMailAsync(mail);
        }
    }
}
