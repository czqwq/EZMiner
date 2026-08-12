package com.czqwq.EZMiner.utils;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;

/**
 * Generic item-matching expression compiler — a standalone, reusable module with
 * zero EZMiner dependencies.
 *
 * <p>
 * An expression selects items by numeric ID and/or OreDictionary name patterns:
 *
 * <pre>
 *   123              item ID 123, any damage
 *   456:1            item ID 456, damage exactly 1
 *   OreCopper        ore-name exact match
 *   raw*             ore-name prefix match
 *   *Purified*       ore-name substring match
 *   !4               NOT — excludes item ID 4
 *   4|3              OR
 *   4&raw*           AND
 *   (4|3)&!5         grouping, precedence: ! &gt; &amp; &gt; |
 * </pre>
 *
 * <p>
 * Empty / blank expressions are disabled and never match. Malformed expressions
 * compile to a never-matching matcher (with a warning) instead of throwing —
 * server-side config input must never crash the chain.
 *
 * <p>
 * Numeric IDs are parsed as {@code int} (EndlessIDs: block IDs up to 2^24-1,
 * item IDs up to 2^24-1, item damage 0-32767). Out-of-range atoms never match.
 * {@code *} wildcards apply to ore-name patterns only (hand-rolled
 * startsWith/endsWith/contains matching, no regex). Ore-name matching is
 * case-sensitive, like standard OreDictionary names.
 *
 * <p>
 * {@link #compile(String)} parses once and returns an immutable matcher safe
 * for concurrent use. The hot path ({@link Compiled#matches(ItemStack)}) only
 * performs a handful of int comparisons for pure-numeric expressions and
 * touches {@link OreDictionary} only when the expression contains an ore-name
 * pattern.
 */
public final class ItemFilterExpression {

    private static final Logger LOG = LogManager.getLogger("EZMiner");

    /** Hard ceiling for numeric IDs — EndlessIDs 24-bit encoding (2^24 - 1). */
    private static final long MAX_ID = 0xFFFFFFL;
    /** Hard ceiling for damage — block metadata is 16-bit under EndlessIDs. */
    private static final long MAX_META = 0xFFFFL;

    private static final Compiled NEVER_MATCHES = stack -> false;

    /** Expression length cap — bounds parser work and memory for hostile input. */
    private static final int MAX_EXPRESSION_LENGTH = 4096;
    /** Nesting cap for '!' / '(' recursion — guards against StackOverflowError. */
    private static final int MAX_NESTING = 64;

    /** Identity-keyed compile cache — zero hot-path cost while the expression is unchanged. */
    private static volatile CacheEntry cache = new CacheEntry("", NEVER_MATCHES);

    @Desugar
    private record CacheEntry(String expression, Compiled matcher) {}

    private ItemFilterExpression() {}

    /** True when the expression is non-blank, i.e. filtering is enabled. */
    public static boolean isEnabled(String expression) {
        return expression != null && !expression.trim()
            .isEmpty();
    }

    /**
     * Compile-once cache keyed by String identity. Config only assigns a fresh
     * String when the value changes, so the fast path is a single volatile read
     * plus a reference comparison. Callers never need to know about reloads.
     *
     * @param expression current expression (may be null / blank / malformed)
     * @return the matcher for this exact String reference
     */
    public static Compiled cached(String expression) {
        if (expression == null) expression = "";
        CacheEntry entry = cache;
        if (entry.expression == expression) return entry.matcher;
        synchronized (ItemFilterExpression.class) {
            CacheEntry current = cache;
            if (current.expression == expression) return current.matcher;
            cache = new CacheEntry(expression, compile(expression));
            return cache.matcher;
        }
    }

    /**
     * Compiles an expression into an immutable matcher.
     *
     * @param expression the expression, may be null / blank / malformed
     * @return a matcher; blank or malformed expressions yield a never-matching
     *         matcher (blank is the normal "disabled" state)
     */
    public static Compiled compile(String expression) {
        if (!isEnabled(expression)) return NEVER_MATCHES;
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            LOG.warn(
                "Item filter expression too long ({} chars, max {}) — filtering disabled for it",
                expression.length(),
                MAX_EXPRESSION_LENGTH);
            return NEVER_MATCHES;
        }
        Parser parser = new Parser(expression);
        Node node = parser.parseOr();
        if (node == null || parser.hasNext()) {
            LOG.warn("Invalid item filter expression '{}' — filtering disabled for it", expression);
            return NEVER_MATCHES;
        }
        return node;
    }

    /** Immutable compiled matcher. Thread-safe; use concurrently. */
    public interface Compiled {

        boolean matches(ItemStack stack);
    }

    // ── AST nodes ────────────────────────────────────────────────────────────

    private interface Node extends Compiled {
    }

    @Desugar
    private record OrNode(Node[] children) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            for (Node child : children) {
                if (child.matches(stack)) return true;
            }
            return false;
        }
    }

    @Desugar
    private record AndNode(Node[] children) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            for (Node child : children) {
                if (!child.matches(stack)) return false;
            }
            return true;
        }
    }

    @Desugar
    private record NotNode(Node child) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            return !child.matches(stack);
        }
    }

    @Desugar
    private record NumericIdAtom(int id) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            return Item.getIdFromItem(stack.getItem()) == id;
        }
    }

    @Desugar
    private record NumericIdMetaAtom(int id, int meta) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            return Item.getIdFromItem(stack.getItem()) == id && stack.getItemDamage() == meta;
        }
    }

    @Desugar
    private record OrePatternAtom(GlobMatcher matcher) implements Node {

        @Override
        public boolean matches(ItemStack stack) {
            int[] ids = OreDictionary.getOreIDs(stack);
            if (ids.length == 0) return false;
            for (int oreId : ids) {
                if (matcher.matches(OreDictionary.getOreName(oreId))) return true;
            }
            return false;
        }
    }

    /** Shared never-matching leaf — out-of-range / malformed atoms compile to this. */
    private static final Node NEVER_NODE = stack -> false;

    // ── Hand-rolled glob matching (no regex) ─────────────────────────────────

    @FunctionalInterface
    private interface GlobMatcher {

        boolean matches(String name);
    }

    /** Compiles a {@code *}-wildcard pattern into a prefix/suffix/infix matcher. */
    private static GlobMatcher compileGlob(String pattern) {
        if (!pattern.contains("*")) {
            return name -> name.equals(pattern);
        }
        // Leading/trailing anchors select the cheapest branch:
        // a* → startsWith, *a → endsWith, *a* → contains, a*b → startsWith+endsWith.
        boolean startsWith = pattern.charAt(0) != '*';
        boolean endsWith = pattern.charAt(pattern.length() - 1) != '*';
        String[] parts = pattern.split("\\*", -1);

        if (parts.length == 2) {
            String first = parts[0];
            String second = parts[1];
            if (startsWith && endsWith) {
                return name -> name.startsWith(first) && name.endsWith(second);
            }
            if (startsWith) {
                return name -> name.startsWith(first);
            }
            if (endsWith) {
                return name -> name.endsWith(second);
            }
            return name -> true; // bare "*" matches every name
        }

        // Multiple wildcards: sequential infix scan bounded by the trailing anchor.
        String prefix = startsWith ? parts[0] : "";
        String suffix = endsWith ? parts[parts.length - 1] : "";
        List<String> middles = new ArrayList<>();
        for (int i = startsWith ? 1 : 0; i < parts.length - (endsWith ? 1 : 0); i++) {
            if (!parts[i].isEmpty()) middles.add(parts[i]);
        }
        return name -> {
            if (startsWith && !name.startsWith(prefix)) return false;
            if (endsWith && !name.endsWith(suffix)) return false;
            int idx = startsWith ? prefix.length() : 0;
            // Infix matches must start at or before the trailing anchor's start
            // AND end before it — `*ab*b` must not match "ab" (overlap would
            // let the same characters satisfy both the infix and the anchor).
            int limit = endsWith ? name.length() - suffix.length() : name.length();
            for (String mid : middles) {
                idx = name.indexOf(mid, idx);
                if (idx < 0 || idx + mid.length() > limit) return false;
                idx += mid.length();
            }
            return true;
        };
    }

    // ── Recursive descent parser ─────────────────────────────────────────────

    /** or := and ('|' and)* ; and := not ('&' not)* ; not := '!' not | primary ; primary := '(' or ')' | atom */
    private static final class Parser {

        private final String expr;
        private int pos;
        private int nesting;

        Parser(String expr) {
            this.expr = expr;
        }

        boolean hasNext() {
            skipWhitespace();
            return pos < expr.length();
        }

        Node parseOr() {
            Node first = parseAnd();
            if (first == null) return null;
            List<Node> nodes = new ArrayList<>(2);
            nodes.add(first);
            while (true) {
                skipWhitespace();
                if (!consume('|')) break;
                Node next = parseAnd();
                if (next == null) return null;
                nodes.add(next);
            }
            if (nodes.size() == 1) return nodes.get(0);
            return new OrNode(nodes.toArray(new Node[0]));
        }

        Node parseAnd() {
            Node first = parseNot();
            if (first == null) return null;
            List<Node> nodes = new ArrayList<>(2);
            nodes.add(first);
            while (true) {
                skipWhitespace();
                if (!consume('&')) break;
                Node next = parseNot();
                if (next == null) return null;
                nodes.add(next);
            }
            if (nodes.size() == 1) return nodes.get(0);
            return new AndNode(nodes.toArray(new Node[0]));
        }

        Node parseNot() {
            skipWhitespace();
            if (consume('!')) {
                if (++nesting > MAX_NESTING) return null;
                Node child = parseNot();
                nesting--;
                return child == null ? null : new NotNode(child);
            }
            return parsePrimary();
        }

        Node parsePrimary() {
            skipWhitespace();
            if (pos >= expr.length()) return null;
            if (expr.charAt(pos) == '(') {
                if (++nesting > MAX_NESTING) return null;
                pos++;
                Node inner = parseOr();
                nesting--;
                if (inner == null) return null;
                skipWhitespace();
                if (!consume(')')) return null;
                return inner;
            }
            int start = pos;
            while (pos < expr.length()) {
                char c = expr.charAt(pos);
                if (c == '|' || c == '&' || c == '!' || c == '(' || c == ')') break;
                pos++;
            }
            // Strip embedded whitespace (incl. full-width U+3000) so Chinese-IME
            // input like '123 |456' parses as '123|456'. Ore-name patterns never
            // contain whitespace, so removal is safe.
            String atom = stripWhitespace(expr.substring(start, pos));
            if (atom.isEmpty()) return null;
            return compileAtom(atom);
        }

        private void skipWhitespace() {
            while (pos < expr.length() && Character.isWhitespace(expr.charAt(pos))) {
                pos++;
            }
        }

        private boolean consume(char c) {
            if (pos < expr.length() && expr.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /** Compiles one atomic token into a leaf node. */
        private static Node compileAtom(String atom) {
            int colon = atom.indexOf(':');
            if (colon >= 0) {
                // 123:5 — numeric ID with exact damage.
                Long id = parseLong(atom.substring(0, colon));
                Long meta = parseLong(atom.substring(colon + 1));
                if (id == null || meta == null) return NEVER_NODE;
                if (id < 0 || id > MAX_ID || meta < 0 || meta > MAX_META) return NEVER_NODE;
                return new NumericIdMetaAtom((int) (long) id, (int) (long) meta);
            }
            if (isAllDigits(atom)) {
                // 123 — numeric ID, any damage.
                Long id = parseLong(atom);
                if (id == null || id < 0 || id > MAX_ID) return NEVER_NODE;
                return new NumericIdAtom((int) (long) id);
            }
            // Anything else is an OreDictionary name wildcard pattern.
            return new OrePatternAtom(compileGlob(atom));
        }

        private static boolean isAllDigits(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                // ASCII digits only — Character.isDigit also accepts full-width
                // digits (U+FF11) which Long.parseLong would reject.
                if (c < '0' || c > '9') return false;
            }
            return true;
        }

        private static String stripWhitespace(String s) {
            StringBuilder sb = null;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (sb == null) sb = new StringBuilder(s.length());
                    continue;
                }
                if (sb != null) sb.append(c);
            }
            return sb == null ? s : sb.toString();
        }

        private static Long parseLong(String s) {
            if (s.isEmpty() || !isAllDigits(s)) return null;
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
