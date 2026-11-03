//! Arabic text support for the reflow engine: contextual shaping and visual reordering.
//!
//! `ab_glyph` measures and rasterizes one code point at a time and the line breaker places runs from
//! the left, so on their own Arabic letters come out disconnected, in the wrong order, and flush
//! left. This module supplies the two missing steps as pure functions over `&str`, with no
//! dependencies and no font access, so they can be tested on the host:
//!
//! 1. [`shape`] replaces each Arabic letter with its isolated / final / initial / medial form
//!    from the Arabic Presentation Forms-B block (and joins lam+alef into one ligature). Run it on
//!    the *logical* text **before** measuring and line breaking, so widths match what is drawn.
//! 2. [`to_visual`] reorders one already-broken *line* into left-to-right display order, keeping
//!    Latin text and numbers readable. Run it **after** line breaking, once per line.
//!
//! The font used for Arabic text must contain the Presentation Forms glyphs (U+FB50..U+FDFF and
//! U+FE70..U+FEFF); Noto Naskh Arabic and GNU FreeSerif both do.

/// How a letter connects to its neighbours.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Join {
    /// Connects on both sides (ba, seen, meem ...).
    Dual,
    /// Connects only to the letter before it (alef, dal, reh, waw ...).
    Right,
}

/// `(isolated, final, initial, medial)`; `0` marks a form that does not exist.
type Forms = (u32, u32, u32, u32);

fn forms(c: char) -> Option<Forms> {
    Some(match c as u32 {
        0x0621 => (0xFE80, 0, 0, 0),
        0x0622 => (0xFE81, 0xFE82, 0, 0),
        0x0623 => (0xFE83, 0xFE84, 0, 0),
        0x0624 => (0xFE85, 0xFE86, 0, 0),
        0x0625 => (0xFE87, 0xFE88, 0, 0),
        0x0626 => (0xFE89, 0xFE8A, 0xFE8B, 0xFE8C),
        0x0627 => (0xFE8D, 0xFE8E, 0, 0),
        0x0628 => (0xFE8F, 0xFE90, 0xFE91, 0xFE92),
        0x0629 => (0xFE93, 0xFE94, 0, 0),
        0x062A => (0xFE95, 0xFE96, 0xFE97, 0xFE98),
        0x062B => (0xFE99, 0xFE9A, 0xFE9B, 0xFE9C),
        0x062C => (0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0),
        0x062D => (0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4),
        0x062E => (0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8),
        0x062F => (0xFEA9, 0xFEAA, 0, 0),
        0x0630 => (0xFEAB, 0xFEAC, 0, 0),
        0x0631 => (0xFEAD, 0xFEAE, 0, 0),
        0x0632 => (0xFEAF, 0xFEB0, 0, 0),
        0x0633 => (0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4),
        0x0634 => (0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8),
        0x0635 => (0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC),
        0x0636 => (0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0),
        0x0637 => (0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4),
        0x0638 => (0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8),
        0x0639 => (0xFEC9, 0xFECA, 0xFECB, 0xFECC),
        0x063A => (0xFECD, 0xFECE, 0xFECF, 0xFED0),
        0x0641 => (0xFED1, 0xFED2, 0xFED3, 0xFED4),
        0x0642 => (0xFED5, 0xFED6, 0xFED7, 0xFED8),
        0x0643 => (0xFED9, 0xFEDA, 0xFEDB, 0xFEDC),
        0x0644 => (0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0),
        0x0645 => (0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4),
        0x0646 => (0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8),
        0x0647 => (0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC),
        0x0648 => (0xFEED, 0xFEEE, 0, 0),
        0x0649 => (0xFEEF, 0xFEF0, 0, 0),
        0x064A => (0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4),
        // Persian / Urdu letters.
        0x067E => (0xFB56, 0xFB57, 0xFB58, 0xFB59),
        0x0686 => (0xFB7A, 0xFB7B, 0xFB7C, 0xFB7D),
        0x0698 => (0xFB8A, 0xFB8B, 0, 0),
        0x06A9 => (0xFB8E, 0xFB8F, 0xFB90, 0xFB91),
        0x06AF => (0xFB92, 0xFB93, 0xFB94, 0xFB95),
        0x06CC => (0xFBFC, 0xFBFD, 0xFBFE, 0xFBFF),
        _ => return None,
    })
}

fn join_kind(c: char) -> Option<Join> {
    if c == '\u{0640}' {
        return Some(Join::Dual); // tatweel (kashida) joins on both sides
    }
    let (_, fin, ini, _) = forms(c)?;
    if ini != 0 {
        Some(Join::Dual)
    } else if fin != 0 {
        Some(Join::Right)
    } else {
        None
    }
}

/// Marks that sit on a letter without taking part in joining (harakat, dagger alef, Quranic marks).
#[must_use]
pub fn is_transparent(c: char) -> bool {
    matches!(c as u32,
        0x064B..=0x065F | 0x0670 | 0x06D6..=0x06DC | 0x06DF..=0x06E4 | 0x06E7 | 0x06E8 | 0x06EA..=0x06ED)
}

/// Whether `c` belongs to an Arabic-script block.
#[must_use]
pub fn is_arabic_char(c: char) -> bool {
    matches!(c as u32,
        0x0600..=0x06FF | 0x0750..=0x077F | 0x08A0..=0x08FF | 0xFB50..=0xFDFF | 0xFE70..=0xFEFF)
}

/// Whether `s` contains any Arabic-script character.
#[must_use]
pub fn contains_arabic(s: &str) -> bool {
    s.chars().any(is_arabic_char)
}

fn is_hebrew(c: char) -> bool {
    matches!(c as u32, 0x0590..=0x05FF | 0xFB1D..=0xFB4F)
}

/// Strongly right-to-left letters (Arabic or Hebrew), excluding digits and marks.
fn is_rtl_letter(c: char) -> bool {
    (is_arabic_char(c) && !is_digit(c) && !is_transparent(c)) || is_hebrew(c)
}

/// Whether the first strongly-directional character of `s` is right-to-left. Used to pick a
/// paragraph's base direction when the book does not declare `dir`.
#[must_use]
pub fn is_rtl_paragraph(s: &str) -> bool {
    for c in s.chars() {
        if is_rtl_letter(c) {
            return true;
        }
        if c.is_alphabetic() {
            return false;
        }
    }
    false
}

/// Replace Arabic letters with their contextual presentation forms.
///
/// Letters from other scripts, digits, spaces and marks pass through unchanged. Combining marks
/// (tashkeel) are kept in place and do not break a join. Idempotent: shaped text has no base
/// letters left to shape.
#[must_use]
pub fn shape(s: &str) -> String {
    if !s.chars().any(|c| forms(c).is_some() || c == '\u{0640}') {
        return s.to_string();
    }
    let chars: Vec<char> = s.chars().collect();
    let mut out = String::with_capacity(s.len());
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        let Some(own) = join_kind(c) else {
            out.push(c);
            i += 1;
            continue;
        };
        let prev_connects = prev_visible(&chars, i)
            .and_then(join_kind)
            .is_some_and(|k| k == Join::Dual);
        let next_connects = next_visible(&chars, i).and_then(join_kind).is_some();

        // lam + alef is one ligature glyph, never two letters.
        if c == '\u{0644}' {
            if let Some(&nx) = chars.get(i + 1) {
                if let Some(lig) = lam_alef(nx, prev_connects) {
                    out.push(lig);
                    i += 2;
                    continue;
                }
            }
        }

        if c == '\u{0640}' {
            out.push(c);
            i += 1;
            continue;
        }
        let (iso, fin, ini, med) = forms(c).unwrap_or((0, 0, 0, 0));
        let joins_prev = prev_connects;
        let joins_next = own == Join::Dual && next_connects;
        let pick = match (joins_prev, joins_next) {
            (false, false) => iso,
            (true, false) => fin,
            (false, true) => ini,
            (true, true) => med,
        };
        let pick = if pick == 0 { iso } else { pick };
        out.push(char::from_u32(pick).unwrap_or(c));
        i += 1;
    }
    out
}

fn prev_visible(chars: &[char], i: usize) -> Option<char> {
    chars[..i].iter().rev().copied().find(|c| !is_transparent(*c))
}

fn next_visible(chars: &[char], i: usize) -> Option<char> {
    chars[i + 1..].iter().copied().find(|c| !is_transparent(*c))
}

fn lam_alef(next: char, joins_prev: bool) -> Option<char> {
    let (iso, fin) = match next as u32 {
        0x0622 => (0xFEF5, 0xFEF6),
        0x0623 => (0xFEF7, 0xFEF8),
        0x0625 => (0xFEF9, 0xFEFA),
        0x0627 => (0xFEFB, 0xFEFC),
        _ => return None,
    };
    char::from_u32(if joins_prev { fin } else { iso })
}

fn is_digit(c: char) -> bool {
    matches!(c as u32, 0x0030..=0x0039 | 0x0660..=0x0669 | 0x06F0..=0x06F9)
}

/// Bidi class reduced to what reordering a single line needs.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Class {
    Rtl,
    Ltr,
    Number,
    Neutral,
}

fn classify(c: char) -> Class {
    if is_digit(c) {
        Class::Number
    } else if is_rtl_letter(c) {
        Class::Rtl
    } else if is_transparent(c) {
        // A mark follows the letter it sits on, so it must not split a run.
        Class::Rtl
    } else if c.is_alphabetic() {
        Class::Ltr
    } else {
        Class::Neutral
    }
}

fn mirror(c: char) -> char {
    match c {
        '(' => ')',
        ')' => '(',
        '[' => ']',
        ']' => '[',
        '{' => '}',
        '}' => '{',
        '<' => '>',
        '>' => '<',
        '\u{00AB}' => '\u{00BB}',
        '\u{00BB}' => '\u{00AB}',
        other => other,
    }
}

/// Reorder one **line** of already-broken text into left-to-right display order.
///
/// `rtl_base` is the paragraph direction. In an RTL paragraph, Arabic runs are reversed while
/// Latin words and numbers keep their internal order; brackets are mirrored. In an LTR paragraph
/// the line is returned unchanged unless it holds Arabic, in which case only those runs are
/// reversed in place. Text must already be [`shape`]d.
#[must_use]
pub fn to_visual(line: &str, rtl_base: bool) -> String {
    let chars: Vec<char> = line.chars().collect();
    if chars.is_empty() {
        return String::new();
    }
    if !rtl_base && !contains_arabic(line) {
        return line.to_string();
    }
    let base_level: u8 = if rtl_base { 1 } else { 0 };
    let classes: Vec<Class> = chars.iter().map(|&c| classify(c)).collect();

    // Neutrals take the surrounding direction when both sides agree, else the paragraph's.
    let mut levels: Vec<u8> = vec![base_level; chars.len()];
    for (i, cl) in classes.iter().enumerate() {
        levels[i] = match cl {
            Class::Rtl => 1,
            Class::Ltr => {
                if rtl_base {
                    2
                } else {
                    0
                }
            }
            Class::Number => 2,
            Class::Neutral => base_level,
        };
    }
    let mut i = 0;
    while i < chars.len() {
        if classes[i] != Class::Neutral {
            i += 1;
            continue;
        }
        let start = i;
        while i < chars.len() && classes[i] == Class::Neutral {
            i += 1;
        }
        let before = (0..start).rev().find(|&k| classes[k] != Class::Neutral);
        let after = (i..chars.len()).find(|&k| classes[k] != Class::Neutral);
        if let (Some(b), Some(a)) = (before, after) {
            let lb = levels[b];
            let la = levels[a];
            if lb == la {
                for l in &mut levels[start..i] {
                    *l = lb;
                }
            }
        }
    }

    // Reverse runs from the deepest level down to the lowest odd level.
    let mut order: Vec<usize> = (0..chars.len()).collect();
    let max = levels.iter().copied().max().unwrap_or(0);
    let mut level = max;
    while level >= 1 {
        let mut k = 0;
        while k < order.len() {
            if levels[order[k]] >= level {
                let s = k;
                while k < order.len() && levels[order[k]] >= level {
                    k += 1;
                }
                order[s..k].reverse();
            } else {
                k += 1;
            }
        }
        level -= 1;
    }
    order
        .into_iter()
        .map(|idx| {
            let c = chars[idx];
            if levels[idx] % 2 == 1 {
                mirror(c)
            } else {
                c
            }
        })
        .collect()
}

/// Remove harakat and Quranic marks (the reader's "hide diacritics" setting).
#[must_use]
pub fn strip_tashkeel(s: &str) -> String {
    s.chars().filter(|&c| !is_transparent(c)).collect()
}

/// Remove tatweel (kashida) stretching characters.
#[must_use]
pub fn strip_tatweel(s: &str) -> String {
    s.chars().filter(|&c| c != '\u{0640}').collect()
}

/// Western digits to Arabic-Indic digits (U+0660..U+0669).
#[must_use]
pub fn to_arabic_indic_digits(s: &str) -> String {
    s.chars()
        .map(|c| match c {
            '0'..='9' => char::from_u32(0x0660 + (c as u32 - '0' as u32)).unwrap_or(c),
            other => other,
        })
        .collect()
}

/// Arabic-Indic and Persian digits to Western digits.
#[must_use]
pub fn to_western_digits(s: &str) -> String {
    s.chars()
        .map(|c| match c as u32 {
            0x0660..=0x0669 => char::from_u32('0' as u32 + (c as u32 - 0x0660)).unwrap_or(c),
            0x06F0..=0x06F9 => char::from_u32('0' as u32 + (c as u32 - 0x06F0)).unwrap_or(c),
            _ => c,
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_dual_joining_word_gets_initial_medial_final_forms() {
        // ba, seen, meem
        assert_eq!(shape("\u{0628}\u{0633}\u{0645}"), "\u{FE91}\u{FEB4}\u{FEE2}");
    }

    #[test]
    fn a_right_joining_letter_breaks_the_join_after_it() {
        // kaf, teh, alef, ba -> initial, medial, final, isolated
        assert_eq!(
            shape("\u{0643}\u{062A}\u{0627}\u{0628}"),
            "\u{FEDB}\u{FE98}\u{FE8E}\u{FE8F}"
        );
    }

    #[test]
    fn lam_alef_becomes_a_single_ligature() {
        assert_eq!(shape("\u{0644}\u{0627}"), "\u{FEFB}");
        // joined from the right: final-form ligature
        assert_eq!(shape("\u{0628}\u{0644}\u{0627}"), "\u{FE91}\u{FEFC}");
    }

    #[test]
    fn diacritics_do_not_break_a_join() {
        // ba + fatha + seen: the mark sits on ba and the pair still connects.
        assert_eq!(shape("\u{0628}\u{064E}\u{0633}"), "\u{FE91}\u{064E}\u{FEB2}");
    }

    #[test]
    fn non_arabic_text_is_untouched() {
        assert_eq!(shape("Hello, 123!"), "Hello, 123!");
    }

    #[test]
    fn shaping_twice_changes_nothing() {
        let once = shape("\u{0628}\u{0633}\u{0645} \u{0627}\u{0644}\u{0644}\u{0647}");
        assert_eq!(shape(&once), once);
    }

    #[test]
    fn an_rtl_line_is_reversed() {
        assert_eq!(
            to_visual("\u{0623}\u{0628} \u{062C}\u{062F}", true),
            "\u{062F}\u{062C} \u{0628}\u{0623}"
        );
    }

    #[test]
    fn numbers_keep_their_reading_order_inside_rtl_text() {
        assert_eq!(to_visual("\u{0623} 12", true), "12 \u{0623}");
    }

    #[test]
    fn latin_words_keep_their_order_inside_rtl_text() {
        assert_eq!(to_visual("\u{0623} ab", true), "ab \u{0623}");
    }

    #[test]
    fn brackets_are_mirrored_in_rtl_runs() {
        assert_eq!(
            to_visual("(\u{0623})", true),
            "(\u{0623})".chars().rev().map(mirror).collect::<String>()
        );
    }

    #[test]
    fn ltr_text_is_never_reordered() {
        assert_eq!(to_visual("Hello world", false), "Hello world");
        assert_eq!(to_visual("Hello world", true), "Hello world");
    }

    #[test]
    fn paragraph_direction_follows_the_first_strong_letter() {
        assert!(is_rtl_paragraph("  \u{0645}\u{0631}\u{062D}\u{0628}\u{0627} hello"));
        assert!(!is_rtl_paragraph("hello \u{0645}\u{0631}\u{062D}\u{0628}\u{0627}"));
        assert!(!is_rtl_paragraph("123 ..."));
    }

    #[test]
    fn tashkeel_and_tatweel_can_be_stripped() {
        assert_eq!(strip_tashkeel("\u{0628}\u{064E}\u{0633}"), "\u{0628}\u{0633}");
        assert_eq!(strip_tatweel("\u{0628}\u{0640}\u{0633}"), "\u{0628}\u{0633}");
    }

    #[test]
    fn digits_convert_both_ways() {
        assert_eq!(to_arabic_indic_digits("2026"), "\u{0662}\u{0660}\u{0662}\u{0666}");
        assert_eq!(to_western_digits("\u{0662}\u{0660}\u{06F2}\u{06F6}"), "2026");
    }
}
