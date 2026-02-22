using System;
using System.Collections.Generic;
using System.Linq;

namespace Caro.Logic
{
    public static class BotHard
    {
        private static int Size = 15;
        private static char[,] Board = new char[Size, Size];

        private static (int X, int Y) lastPlayerMove;
        private static (int X, int Y) lastBotMove;

        public static (int X, int Y) GetNextMove(int lastPlayerX, int lastPlayerY)
        {
            if (TrongBan(lastPlayerX, lastPlayerY))
                Board[lastPlayerX, lastPlayerY] = 'X';

            lastPlayerMove = (lastPlayerX, lastPlayerY);

            int bestScore = int.MinValue;
            (int X, int Y) bestMove = (-1, -1);

            foreach (var (x, y) in GetFocusedValidMoves(lastPlayerMove, lastBotMove))
            {
                if (!TrongBan(x, y)) continue;

                Board[x, y] = 'O';
                int score = Minimax(3, false, int.MinValue, int.MaxValue);
                Board[x, y] = '\0';

                if (score > bestScore)
                {
                    bestScore = score;
                    bestMove = (x, y);
                }
            }

            lastBotMove = bestMove;
            if (TrongBan(bestMove.X, bestMove.Y))
                Board[bestMove.X, bestMove.Y] = 'O';

            return bestMove;
        }

        private static List<(int, int)> GetFocusedValidMoves((int X, int Y) lastPlayer, (int X, int Y) lastBot)
        {
            var moves = new HashSet<(int, int)>();

            foreach (var (cx, cy) in new[] { lastPlayer, lastBot })
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    for (int dy = -2; dy <= 2; dy++)
                    {
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (TrongBan(nx, ny) && Board[nx, ny] == '\0')
                            moves.Add((nx, ny));
                    }
                }
            }

            return moves.ToList();
        }

        private static bool TrongBan(int x, int y)
        {
            return x >= 0 && x < Size && y >= 0 && y < Size;
        }

        private static int Minimax(int depth, bool isMaximizing, int alpha, int beta)
        {
            if (depth == 0 || KiemTraThang('X', out _) || KiemTraThang('O', out _))
                return EvaluateBoard();

            var moves = GetFocusedValidMoves(lastPlayerMove, lastBotMove);

            if (isMaximizing)
            {
                int maxEval = int.MinValue;
                foreach (var (x, y) in moves)
                {
                    if (!TrongBan(x, y)) continue;

                    Board[x, y] = 'O';
                    int eval = Minimax(depth - 1, false, alpha, beta);
                    Board[x, y] = '\0';
                    maxEval = Math.Max(maxEval, eval);
                    alpha = Math.Max(alpha, eval);
                    if (beta <= alpha) break;
                }
                return maxEval;
            }
            else
            {
                int minEval = int.MaxValue;
                foreach (var (x, y) in moves)
                {
                    if (!TrongBan(x, y)) continue;

                    Board[x, y] = 'X';
                    int eval = Minimax(depth - 1, true, alpha, beta);
                    Board[x, y] = '\0';
                    minEval = Math.Min(minEval, eval);
                    beta = Math.Min(beta, eval);
                    if (beta <= alpha) break;
                }
                return minEval;
            }
        }

        private static int EvaluateBoard()
        {
            return TinhDiem('O') - TinhDiem('X');
        }

        private static int TinhDiem(char player)
        {
            int score = 0;
            for (int i = 0; i < Size; i++)
                for (int j = 0; j < Size; j++)
                    if (Board[i, j] == player)
                    {
                        score += DemDiem(i, j, player, 1, 0); // ngang
                        score += DemDiem(i, j, player, 0, 1); // dọc
                        score += DemDiem(i, j, player, 1, 1); // chéo xuôi
                        score += DemDiem(i, j, player, 1, -1);// chéo ngược
                    }
            return score;
        }

        private static int DemDiem(int x, int y, char player, int dx, int dy)
        {
            int count = 0;
            for (int i = 0; i < 5; i++)
            {
                int nx = x + i * dx;
                int ny = y + i * dy;
                if (TrongBan(nx, ny) && Board[nx, ny] == player)
                    count++;
                else break;
            }

            return count switch
            {
                5 => 100000,
                4 => 10000,
                3 => 1000,
                2 => 100,
                _ => 0
            };
        }

        public static bool KiemTraThang(char player, out List<(int x, int y)> winningLine)
        {
            winningLine = new List<(int x, int y)>();
            for (int i = 0; i < Size; i++)
            {
                for (int j = 0; j < Size; j++)
                {
                    if (Board[i, j] == player)
                    {
                        foreach (var (dx, dy) in new[] { (1, 0), (0, 1), (1, 1), (1, -1) })
                        {
                            var temp = new List<(int, int)>();
                            int x = i, y = j;
                            while (TrongBan(x, y) && Board[x, y] == player)
                            {
                                temp.Add((x, y));
                                x += dx;
                                y += dy;
                            }
                            if (temp.Count >= 5)
                            {
                                winningLine = temp;
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        public static void ResetBoard()
        {
            for (int i = 0; i < Size; i++)
                for (int j = 0; j < Size; j++)
                    Board[i, j] = '\0';
        }
    }
}
