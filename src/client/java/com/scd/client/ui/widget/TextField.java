package com.scd.client.ui.widget;

import com.scd.client.ui.Theme;
import com.scd.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Vanilla EditBox with SCD's flat frame and a per-keystroke character filter (EditBox has no filter
 * hook in 26.2, so invalid characters are stripped in the responder).
 */
public class TextField extends EditBox {
	private final Predicate<Character> allowed;

	public TextField(int x, int y, int w, int h, String hint, String initial, Predicate<Character> allowed, Consumer<String> onChange) {
		super(Ui.font(), x + 4, y + (h - 8) / 2, w - 8, h, Component.literal(hint == null ? "" : hint));
		this.allowed = allowed;
		setBordered(false);
		setMaxLength(256);
		setTextColor(Ui.theme().textPrimary());
		if (hint != null) setHint(Component.literal(hint));
		setValue(initial == null ? "" : initial);
		setResponder(s -> {
			String cleaned = clean(s);
			if (!cleaned.equals(s)) {
				setValue(cleaned);
				return;
			}
			onChange.accept(s);
		});
	}

	public static Predicate<Character> any() {
		return c -> true;
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

	private String clean(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) if (allowed.test(c)) sb.append(c);
		return sb.toString();
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Theme t = Ui.theme();
		int fx = getX() - 4, fy = getY() - (getHeight() - 8) / 2, fw = getWidth() + 8, fh = getHeight();
		g.fill(fx, fy, fx + fw, fy + fh, t.field());
		g.outline(fx, fy, fw, fh, isFocused() ? t.accent() : t.border());
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
	}
}
