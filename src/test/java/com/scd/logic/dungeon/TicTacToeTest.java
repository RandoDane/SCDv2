package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicTacToeTest {
	@Test
	void winsWhenPossibleAndBlocksOtherwise() {
		char[][] win = {{'O', 'O', 0}, {'X', 'X', 0}, {'X', 0, 0}};
		assertArrayEquals(new int[]{0, 2}, TicTacToe.bestMove(win));
		char[][] block = {{'X', 'X', 0}, {'O', 0, 0}, {0, 0, 0}};
		assertArrayEquals(new int[]{0, 2}, TicTacToe.bestMove(block));
		char[][] done = {{'X', 'X', 'X'}, {'O', 'O', 0}, {0, 0, 0}};
		assertNull(TicTacToe.bestMove(done));
	}
}
