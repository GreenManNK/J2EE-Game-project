using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Caro.Models
{
    public class Friendship
    {
        public int Id { get; set; }

        public string RequesterId { get; set; }
        public ApplicationUser Requester { get; set; }

        public string AddresseeId { get; set; }
        public ApplicationUser Addressee { get; set; }

        public bool IsAccepted { get; set; }
    }

}
