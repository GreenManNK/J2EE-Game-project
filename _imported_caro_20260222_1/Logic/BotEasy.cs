using System;
using System.Collections.Generic;
using System.Linq;

namespace Caro.Logic
{
    public static class BotEasy
    {
        private static int Size = 15;
        private static char[,] Board = new char[Size, Size];

        public static void DatQuanNguoiChoi(int x, int y)
        {
            if (TrongBan(x, y))
            {
                Board[x, y] = 'X';
            }
        }

        public static (int X, int Y) GetNextMove(int lastPlayerX, int lastPlayerY)
        {
            if (TrongBan(lastPlayerX, lastPlayerY))
                Board[lastPlayerX, lastPlayerY] = 'X';

            if (DemSoQuanCo('O') == 0)
            {
                var (x, y) = TimNuocDiDauTienCuaNguoiChoi();
                if (DatNuocDiXungQuanh(x, y, out var move)) return move;
                return (0, 0);
            }

            if (ChanNguoiChoi(out var chanMove))
                return chanMove;

            if (MoRongNuocDiBot(out var moRongMove))
                return moRongMove;

            if (DatQuanDeTaoThe(out var taoTheMove))
                return taoTheMove;

            return (0, 0);
        }

        public static bool KiemTraThang(char quanCo)
        {
            for (int i = 0; i < Size; i++)
            {
                for (int j = 0; j < Size; j++)
                {
                    if (Board[i, j] == quanCo)
                    {
                        if (DemLienTiep(i, j, quanCo, 1, 0) >= 5 ||
                            DemLienTiep(i, j, quanCo, 0, 1) >= 5 ||
                            DemLienTiep(i, j, quanCo, 1, 1) >= 5 ||
                            DemLienTiep(i, j, quanCo, 1, -1) >= 5)
                        {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public static void ResetBoard()
        {
            Board = new char[Size, Size];
        }

        private static int DemLienTiep(int x, int y, char quanCo, int dx, int dy)
        {
            int count = 0;
            while (TrongBan(x, y) && Board[x, y] == quanCo)
            {
                count++;
                x += dx;
                y += dy;
            }
            return count;
        }

        private static int DemSoQuanCo(char quanCo) =>
            Board.Cast<char>().Count(cell => cell == quanCo);

        private static (int, int) TimNuocDiDauTienCuaNguoiChoi()
        {
            for (int i = 0; i < Size; i++)
                for (int j = 0; j < Size; j++)
                    if (Board[i, j] == 'X') return (i, j);
            return (-1, -1);
        }

        private static bool TrongBan(int x, int y) => x >= 0 && x < Size && y >= 0 && y < Size;

        private static bool DatNuocDiXungQuanh(int x, int y, out (int X, int Y) move)
        {
            var oXungQuanh = new List<(int, int)>
            {
                (x - 1, y - 1), (x - 1, y), (x - 1, y + 1),
                (x, y - 1),               (x, y + 1),
                (x + 1, y - 1), (x + 1, y), (x + 1, y + 1),
                (x - 2, y - 2), (x - 2, y - 1), (x - 2, y), (x - 2, y + 1), (x - 2, y + 2),
                (x - 1, y - 2),                           (x - 1, y + 2),
                (x, y - 2),                               (x, y + 2),
                (x + 1, y - 2),                           (x + 1, y + 2),
                (x + 2, y - 2), (x + 2, y - 1), (x + 2, y), (x + 2, y + 1), (x + 2, y + 2)
            }.OrderBy(_ => Guid.NewGuid()).ToList();

            foreach (var (i, j) in oXungQuanh)
            {
                if (TrongBan(i, j) && Board[i, j] == '\0')
                {
                    Board[i, j] = 'O';
                    move = (i, j);
                    return true;
                }
            }

            move = (-1, -1);
            return false;
        }

        private static bool ChanNguoiChoi(out (int X, int Y) move)
        {
            for (int i = 0; i < Size; i++)
            {
                for (int j = 0; j < Size; j++)
                {
                    if (Board[i, j] == 'X')
                    {
                        if (CoTheChan(i, j, 1, 0, out move) ||
                            CoTheChan(i, j, 0, 1, out move) ||
                            CoTheChan(i, j, 1, 1, out move) ||
                            CoTheChan(i, j, 1, -1, out move))
                            return true;
                    }
                }
            }

            move = (-1, -1);
            return false;
        }

        private static bool CoTheChan(int x, int y, int dx, int dy, out (int X, int Y) move)
        {
            int count = 0;
            int startX = x, startY = y;

            while (TrongBan(startX, startY) && Board[startX, startY] == 'X')
            {
                count++;
                startX += dx;
                startY += dy;
            }

            bool dauTruocTrong = TrongBan(x - dx, y - dy) && Board[x - dx, y - dy] == '\0';
            bool dauSauTrong = TrongBan(startX, startY) && Board[startX, startY] == '\0';

            if (count == 4 && (dauTruocTrong || dauSauTrong))
            {
                move = dauTruocTrong ? (x - dx, y - dy) : (startX, startY);
                if (TrongBan(move.X, move.Y)) Board[move.X, move.Y] = 'O';
                return true;
            }

            if (count == 3 && !(dauTruocTrong && dauSauTrong))
            {
                move = (-1, -1);
                return false;
            }

            if (count == 3 && dauTruocTrong && dauSauTrong)
            {
                move = dauTruocTrong ? (x - dx, y - dy) : (startX, startY);
                if (TrongBan(move.X, move.Y)) Board[move.X, move.Y] = 'O';
                return true;
            }

            move = (-1, -1);
            return false;
        }

        private static bool MoRongNuocDiBot(out (int X, int Y) move)
        {
            var oDaDat = new List<(int, int)>();
            var rand = new Random();

            for (int i = 0; i < Size; i++)
                for (int j = 0; j < Size; j++)
                    if (Board[i, j] == 'O') oDaDat.Add((i, j));

            foreach (var (x, y) in oDaDat)
            {
                var oXungQuanh = new List<(int, int)>
                {
                    (x - 1, y - 1), (x - 1, y), (x - 1, y + 1),
                    (x, y - 1),               (x, y + 1),
                    (x + 1, y - 1), (x + 1, y), (x + 1, y + 1)
                }.OrderBy(_ => rand.Next()).ToList();

                foreach (var (i, j) in oXungQuanh)
                {
                    if (TrongBan(i, j) && Board[i, j] == '\0' && DemKhoangTrong(i, j, 'O') >= 5)
                    {
                        Board[i, j] = 'O';
                        move = (i, j);
                        return true;
                    }
                }

                foreach (var (i, j) in oXungQuanh)
                {
                    int dx = i - x;
                    int dy = j - y;
                    int nx = i + dx;
                    int ny = j + dy;

                    if (TrongBan(i, j) && TrongBan(nx, ny) &&
                        Board[i, j] == '\0' && Board[nx, ny] == 'O')
                    {
                        Board[i, j] = 'O';
                        move = (i, j);
                        return true;
                    }
                }
            }

            move = (-1, -1);
            return false;
        }

        private static bool DatQuanDeTaoThe(out (int X, int Y) move)
        {
            var oDaDat = new List<(int, int)>();
            var oTrong = new List<(int, int)>();
            var rand = new Random();

            for (int i = 0; i < Size; i++)
            {
                for (int j = 0; j < Size; j++)
                {
                    if (Board[i, j] == 'O') oDaDat.Add((i, j));
                    else if (Board[i, j] == '\0') oTrong.Add((i, j));
                }
            }

            (int, int)? viTriTotNhat = null;
            int maxO = 0;

            foreach (var (x, y) in oDaDat)
            {
                var oXungQuanh = new List<(int, int)>
                {
                    (x - 1, y - 1), (x - 1, y), (x - 1, y + 1),
                    (x, y - 1),               (x, y + 1),
                    (x + 1, y - 1), (x + 1, y), (x + 1, y + 1)
                }.OrderBy(_ => rand.Next()).ToList();

                foreach (var (i, j) in oXungQuanh)
                {
                    if (TrongBan(i, j) && Board[i, j] == '\0')
                    {
                        int oLienKe = DemQuan(i, j, 'O');
                        if (DemKhoangTrong(i, j, 'O') >= 5)
                        {
                            Board[i, j] = 'O';
                            move = (i, j);
                            return true;
                        }
                        if (oLienKe > maxO)
                        {
                            maxO = oLienKe;
                            viTriTotNhat = (i, j);
                        }
                    }
                }
            }

            if (viTriTotNhat.HasValue)
            {
                Board[viTriTotNhat.Value.Item1, viTriTotNhat.Value.Item2] = 'O';
                move = viTriTotNhat.Value;
                return true;
            }
            else if (oTrong.Count > 0)
            {
                var random = oTrong[rand.Next(oTrong.Count)];
                Board[random.Item1, random.Item2] = 'O';
                move = random;
                return true;
            }

            move = (-1, -1);
            return false;
        }

        private static int DemQuan(int row, int col, char player) =>
            new[] { (1, 0), (0, 1), (1, 1), (1, -1) }
                .Max(d => DemHang(row, col, player, d.Item1, d.Item2));

        private static int DemHang(int row, int col, char player, int dx, int dy) =>
            Enumerable.Range(-4, 9)
                .Count(i => TrongBan(row + i * dx, col + i * dy) &&
                            Board[row + i * dx, col + i * dy] == player);

        private static int DemKhoangTrong(int x, int y, char quanCo)
        {
            int[] dx = { -1, 1, 0, 0 };
            int[] dy = { 0, 0, -1, 1 };
            int maxKhoangTrong = 0;

            for (int k = 0; k < 4; k++)
            {
                int demTrai = 0, demPhai = 0;
                int i = x + dx[k], j = y + dy[k];

                while (TrongBan(i, j) && Board[i, j] == '\0')
                {
                    demTrai++;
                    i += dx[k];
                    j += dy[k];
                }

                i = x - dx[k];
                j = y - dy[k];

                while (TrongBan(i, j) && Board[i, j] == '\0')
                {
                    demPhai++;
                    i -= dx[k];
                    j -= dy[k];
                }

                maxKhoangTrong = Math.Max(maxKhoangTrong, demTrai + demPhai);
            }

            return maxKhoangTrong;
        }
    }
}
