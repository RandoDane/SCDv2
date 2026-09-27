package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single-line text input drawn with SCD's smooth font (vanilla EditBox measures with the pixel font,
 * so its cursor would drift from styled text). Supports cursor + selection, word jumps (Ctrl),
 * Home/End, copy/cut/paste/select-all, horizontal scrolling, and a per-character filter.
 */
public class TextField extends AbstractWidget {
	private static final int PAD_X = 6;

	private final String hint;
	private final Predicate<Character> allowed;
	private final Consumer<String> onChange;
	private String value;
	private int cursor;
	private int anchor;
	private int scrollChars;
	private int maxLength = 256;
	private long focusedAt;

	public TextField(int x, int y, int w, int h, String hint, String initial, Predicate<Character> allowed, Consumer<String> onChange) {
		super(x, y, w, h, Component.literal(hint == null ? "" : hint));
		this.hint = hint;
		this.allowed = allowed;
		this.onChange = onChange;
		this.value = initial == null ? "" : filter(initial);
		this.cursor = this.anchor = value.length();
	}

	public static Predicate<Character> any() {
		return c -> c >= ' ';
	}

	public static Predicate<Character> digits() {
		return Character::isDigit;
	}

	/** Digits, separators and a k/m/b suffix - "1.3m", "800k". Full validity is checked on submit. */
	public static Predicate<Character> compactNumber() {
		return c -> Character.isDigit(c) || c == '.' || c == ',' || "kKmMbB".indexOf(c) >= 0;
	}

	public static Predicate<Character> ign() {
		return c -> Character.isLetterOrDigit(c) || c == '_';
	}

	public String getValue() {
		return value;
	}

	public void setValue(String text) {
		value = filter(text == null ? "" : text);
		cursor = anchor = value.length();
		onChange.accept(value);
	}

	public void setMaxLength(int max) {
		maxLength = max;
	}

	public void moveCursorToEnd() {
		cursor = anchor = value.length();
	}

	private String filter(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) if (allowed.test(c) && sb.length() < maxLength) sb.append(c);
		return sb.toString();
	}

	private boolean hasSelection() {
		return cursor != anchor;
	}

	private void insert(String text) {
		String clean = filter(text);
		int from = Math.min(cursor, anchor), to = Math.max(cursor, anchor);
		String next = value.substring(0, from) + clean + value.substring(to);
		if (next.length() > maxLength) return;
		value = next;
		cursor = anchor = from + clean.length();
		onChange.accept(value);
	}

	private void deleteSelectionOr(int direction, boolean word) {
		if (hasSelection()) {
			insert("");
			return;
		}
		int target = word ? wordBoundary(direction) : Math.max(0, Math.min(value.length(), cursor + direction));
		int from = Math.min(cursor, target), to = Math.max(cursor, target);
		if (from == to) return;
		value = value.substring(0, from) + value.substring(to);
		cursor = anchor = from;
		onChange.accept(value);
	}

	private int wordBoundary(int direction) {
		int i = cursor;
		if (direction < 0) {
			while (i > 0 && value.charAt(i - 1) == ' ') i--;
			while (i > 0 && value.charAt(i - 1) != ' ') i--;
		} else {
			while (i < value.length() && value.charAt(i) == ' ') i++;
			while (i < value.length() && value.charAt(i) != ' ') i++;
		}
		return i;
	}

	private void move(int to, boolean select) {
		cursor = Math.max(0, Math.min(value.length(), to));
		if (!select) anchor = cursor;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!isFocused() || !isActive()) return false;
		String s = event.codepointAsString();
		if (s.length() == 1 && !allowed.test(s.charAt(0))) return true;
		insert(s);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!isFocused() || !isActive()) return false;
		boolean shift = event.hasShiftDown();
		boolean word = event.hasControlDownWithQuirk();
		var kb = Minecraft.getInstance().keyboardHandler;
		if (event.isSelectAll()) {
			anchor = 0;
			cursor = value.length();
			return true;
		}
		if (event.isCopy()) {
			if (hasSelection()) kb.setClipboard(value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)));
			return true;
		}
		if (event.isCut()) {
			if (hasSelection()) {
				kb.setClipboard(value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)));
				insert("");
			}
			return true;
		}
		if (event.isPaste()) {
			insert(kb.getClipboard());
			return true;
		}
		switch (event.key()) {
			case GLFW.GLFW_KEY_BACKSPACE -> deleteSelectionOr(-1, word);
			case GLFW.GLFW_KEY_DELETE -> deleteSelectionOr(1, word);
			case GLFW.GLFW_KEY_LEFT -> move(word ? wordBoundary(-1) : (hasSelection() && !shift ? Math.min(cursor, anchor) : cursor - 1), shift);
			case GLFW.GLFW_KEY_RIGHT -> move(word ? wordBoundary(1) : (hasSelection() && !shift ? Math.max(cursor, anchor) : cursor + 1), shift);
			case GLFW.GLFW_KEY_HOME -> move(0, shift);
			case GLFW.GLFW_KEY_END -> move(value.length(), shift);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (doubleClick) {
			anchor = 0;
			cursor = value.length();
			return;
		}
		move(indexAt(event.x()), event.hasShiftDown());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		move(indexAt(event.x()), true);
	}

	private int indexAt(double mouseX) {
		int rel = (int) (mouseX - getX() - PAD_X);
		String visible = value.substring(Math.min(scrollChars, value.length()));
		for (int i = 0; i <= visible.length(); i++) {
			if (Ui.width(visible.substring(0, i)) > rel) return scrollChars + Math.max(0, i - 1);
		}
		return value.length();
	}

	@Override
	public void setFocused(boolean focused) {
		if (focused && !isFocused()) focusedAt = System.currentTimeMillis();
		super.setFocused(focused);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		int border = isFocused() ? t.accent() : isHovered() ? Ui.blend(t.border(), 0xFFFFFFFF, 0.08f) : t.border();
		Ui.rect(g, x, y, w, h, Ui.RADIUS_SMALL, t.field(), border);
		int inner = w - PAD_X * 2;
		// Keep the cursor visible: scroll the text window as needed.
		scrollChars = Math.min(scrollChars, cursor);
		while (scrollChars < cursor && Ui.width(value.substring(scrollChars, cursor)) > inner - 2) scrollChars++;
		String visible = value.substring(scrollChars);
		int ty = y + (h - 8) / 2;
		if (value.isEmpty() && !isFocused() && hint != null) {
			Ui.text(g, Ui.ellipsize(hint, inner), x + PAD_X, ty, t.textMuted());
			return;
		}
		g.enableScissor(x + 2, y, x + w - 2, y + h);
		int tx = x + PAD_X;
		if (hasSelection() && isFocused()) {
			int a = Math.max(scrollChars, Math.min(cursor, anchor)), b = Math.max(cursor, anchor);
			int sx = tx + Ui.width(value.substring(scrollChars, a));
			int ex = tx + Ui.width(value.substring(scrollChars, Math.max(a, b)));
			g.fill(sx, ty - 1, ex, ty + 9, (0x60 << 24) | (t.accent() & 0xFFFFFF));
		}
		Ui.text(g, visible, tx, ty, isActive() ? t.textPrimary() : t.textMuted());
		if (isFocused() && ((System.currentTimeMillis() - focusedAt) / 500) % 2 == 0) {
			int cx = tx + Ui.width(value.substring(scrollChars, cursor));
			g.fill(cx, ty - 1, cx + 1, ty + 9, t.textPrimary());
		}
		g.disableScissor();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + value));
	}
}
