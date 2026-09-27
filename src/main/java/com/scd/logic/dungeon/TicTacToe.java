package com.scd.logic.dungeon;

/** Tic Tac Toe best move for 'O' (the player) by minimax; board[row][col] is 'X', 'O' or 0. */
public final class TicTacToe {
	private TicTacToe() {
	}

	/** {row, col} of the best move for O, or null if the game is over. */
	public static int[] bestMove(char[][] board) {
		if (winner(board) != 0) return null;
		int bestScore = Integer.MIN_VALUE;
		int[] best = null;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				if (board[r][c] != 0) continue;
				board[r][c] = 'O';
				int score = minimax(board, false, 1);
				board[r][c] = 0;
				if (score > bestScore) {
					bestScore = score;
					best = new int[]{r, c};
				}
			}
		}
		return best;
	}

	private static int minimax(char[][] b, boolean oTurn, int depth) {
		char w = winner(b);
		if (w == 'O') return 10 - depth;
		if (w == 'X') return depth - 10;
		boolean full = true;
		int best = oTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				if (b[r][c] != 0) continue;
				full = false;
				b[r][c] = oTurn ? 'O' : 'X';
				int s = minimax(b, !oTurn, depth + 1);
				b[r][c] = 0;
				best = oTurn ? Math.max(best, s) : Math.min(best, s);
			}
		}
		return full ? 0 : best;
	}

	static char winner(char[][] b) {
		int[][] lines = {{0, 0, 0, 1, 0, 2}, {1, 0, 1, 1, 1, 2}, {2, 0, 2, 1, 2, 2}, {0, 0, 1, 0, 2, 0}, {0, 1, 1, 1, 2, 1}, {0, 2, 1, 2, 2, 2},
				{0, 0, 1, 1, 2, 2}, {0, 2, 1, 1, 2, 0}};
		for (int[] l : lines) {
			char a = b[l[0]][l[1]];
			if (a != 0 && a == b[l[2]][l[3]] && a == b[l[4]][l[5]]) return a;
		}
		return 0;
	}
}
