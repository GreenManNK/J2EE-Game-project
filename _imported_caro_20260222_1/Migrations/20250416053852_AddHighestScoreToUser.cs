using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Caro.Migrations
{
    /// <inheritdoc />
    public partial class AddHighestScoreToUser : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "HighestScore",
                table: "AspNetUsers",
                type: "int",
                nullable: false,
                defaultValue: 0);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "HighestScore",
                table: "AspNetUsers");
        }
    }
}
